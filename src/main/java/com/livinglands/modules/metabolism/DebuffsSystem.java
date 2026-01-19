package com.livinglands.modules.metabolism;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.PlayerSession;
import com.livinglands.core.config.DebuffsConfig;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * System that applies debuff effects based on metabolism stat levels.
 *
 * Debuffs:
 * - Hunger at 0: Takes damage that increases over time
 * - Thirst low: Visual impairment and damage at 0
 * - Energy low: Reduced movement speed and increased stamina consumption
 *
 * Uses PlayerRegistry for ECS reference access (thread-safe via world.execute()).
 */
public class DebuffsSystem {

    private static final String MODIFIER_KEY_ENERGY_SPEED = "livinglands_energy_speed";
    private static final String MODIFIER_KEY_ENERGY_STAMINA = "livinglands_energy_stamina";
    private static final String MODIFIER_KEY_THIRST_SPEED = "livinglands_thirst_speed";
    private static final String MODIFIER_KEY_THIRST_STAMINA = "livinglands_thirst_stamina";

    private final DebuffsConfig config;
    private final HytaleLogger logger;
    private final PlayerRegistry playerRegistry;

    // Track starvation damage progression per player
    private final Map<UUID, Integer> starvationTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHungerDamageTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastThirstDamageTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStaminaDrainTime = new ConcurrentHashMap<>();

    // Track whether players are currently in damage/debuff state (for hysteresis)
    // Player enters starving state when hunger <= damageStartThreshold
    // Player exits starving state when hunger >= recoveryThreshold
    private final Set<UUID> starvingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> dehydratedPlayers = ConcurrentHashMap.newKeySet();
    // Player enters exhausted state when energy <= staminaDrainStartThreshold
    // Player exits exhausted state when energy >= staminaDrainRecoveryThreshold
    private final Set<UUID> exhaustedPlayers = ConcurrentHashMap.newKeySet();
    // Track players with thirst-based speed/stamina debuffs
    private final Set<UUID> parchedPlayers = ConcurrentHashMap.newKeySet();
    // Track players with energy-based tired debuffs
    private final Set<UUID> tiredPlayers = ConcurrentHashMap.newKeySet();

    // Track when we last logged warning messages
    private final Map<UUID, Long> lastWarningTime = new ConcurrentHashMap<>();
    private static final long WARNING_COOLDOWN_MS = 10000; // 10 seconds between warnings

    // Track original base speed for each player to restore when debuffs are removed
    private final Map<UUID, Float> originalBaseSpeeds = new ConcurrentHashMap<>();
    // Track current speed multiplier per player (combined from all debuffs)
    private final Map<UUID, Float> currentSpeedMultipliers = new ConcurrentHashMap<>();

    /**
     * Creates a new debuffs system.
     *
     * @param config The debuffs configuration
     * @param logger The logger for debug output
     * @param playerRegistry The central player registry for ECS access
     */
    public DebuffsSystem(@Nonnull DebuffsConfig config, @Nonnull HytaleLogger logger,
                         @Nonnull PlayerRegistry playerRegistry) {
        this.config = config;
        this.logger = logger;
        this.playerRegistry = playerRegistry;
    }

    /**
     * Process debuffs for a player based on their current stats.
     * Called from the metabolism tick loop.
     */
    public void processDebuffs(UUID playerId, PlayerMetabolismData data) {
        try {
            // Get player session from registry
            var sessionOpt = playerRegistry.getSession(playerId);
            if (sessionOpt.isEmpty()) {
                return; // Player not registered
            }

            var session = sessionOpt.get();
            if (!session.isEcsReady()) {
                // ECS not ready yet - this is normal during initial connection
                return;
            }

            var ref = session.getEntityRef();
            var store = session.getStore();
            var world = session.getWorld();

            if (ref == null || store == null || world == null) {
                return;
            }

            // Get player for feedback messages
            var player = session.getPlayer();

            // Process each debuff type
            processHungerDebuff(playerId, data, ref, store, world, player);
            processThirstDebuff(playerId, data, ref, store, world, player);
            processEnergyDebuff(playerId, data, ref, store, world, player);

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Error processing debuffs for player %s", playerId
            );
        }
    }

    /**
     * Process hunger debuff - damage when starving.
     * Uses hysteresis: damage starts at damageStartThreshold, stops at recoveryThreshold.
     */
    private void processHungerDebuff(UUID playerId, PlayerMetabolismData data,
                                      Ref<EntityStore> ref, Store<EntityStore> store, World world, Player player) {
        if (!config.hunger().enabled()) {
            return;
        }

        var hunger = data.getHunger();
        var isCurrentlyStarving = starvingPlayers.contains(playerId);
        var recoveryThreshold = config.hunger().recoveryThreshold();

        // Check if player should enter starving state
        if (!isCurrentlyStarving && hunger <= config.hunger().damageStartThreshold()) {
            starvingPlayers.add(playerId);
            isCurrentlyStarving = true;
            logger.at(Level.INFO).log("Player %s entered starvation state (hunger: %.1f)", playerId, hunger);
            sendDebuffMessage(player, "You are starving! Find food quickly!", true);
        }

        // Check if player should exit starving state (recovered enough)
        if (isCurrentlyStarving && hunger >= recoveryThreshold) {
            starvingPlayers.remove(playerId);
            starvationTicks.remove(playerId);
            lastHungerDamageTime.remove(playerId);
            logger.at(Level.INFO).log("Player %s RECOVERED from starvation (hunger: %.1f >= %.1f)",
                playerId, hunger, recoveryThreshold);
            sendDebuffMessage(player, "You are no longer starving.", false);
            return;
        }

        // Apply damage if currently starving
        if (isCurrentlyStarving) {
            var now = System.currentTimeMillis();
            var lastDamage = lastHungerDamageTime.getOrDefault(playerId, 0L);
            var intervalMs = (long) (config.hunger().damageTickIntervalSeconds() * 1000);

            if (now - lastDamage >= intervalMs) {
                // Calculate damage based on how long they've been starving
                var ticks = starvationTicks.getOrDefault(playerId, 0);
                var damage = Math.min(
                    config.hunger().initialDamage() + (ticks * config.hunger().damageIncreasePerTick()),
                    config.hunger().maxDamage()
                );

                // Apply damage to health (on WorldThread)
                applyDamage(ref, store, world, damage, "starvation");

                // Update tracking
                starvationTicks.put(playerId, ticks + 1);
                lastHungerDamageTime.put(playerId, now);

                // Log warning (with cooldown)
                logWarning(playerId, "Starvation damage: %.1f (tick %d)", damage, ticks + 1);
            }
        }
    }

    /**
     * Process thirst debuff - speed/stamina reduction when parched, damage when dehydrated.
     * Uses hysteresis: damage starts at damageStartThreshold, stops at recoveryThreshold.
     */
    private void processThirstDebuff(UUID playerId, PlayerMetabolismData data,
                                      Ref<EntityStore> ref, Store<EntityStore> store, World world, Player player) {
        if (!config.thirst().enabled()) {
            return;
        }

        var thirst = data.getThirst();
        var isCurrentlyDehydrated = dehydratedPlayers.contains(playerId);
        var recoveryThreshold = config.thirst().recoveryThreshold();
        var slowThreshold = config.thirst().slowStartThreshold();

        // Process speed/stamina debuff when thirst is low (parched state)
        processThirstSpeedDebuff(playerId, thirst, slowThreshold, ref, store, world, player);

        // Check if player should enter dehydrated state (critical - damage)
        if (!isCurrentlyDehydrated && thirst <= config.thirst().damageStartThreshold()) {
            dehydratedPlayers.add(playerId);
            isCurrentlyDehydrated = true;
            logger.at(Level.INFO).log("Player %s entered dehydration state (thirst: %.1f)", playerId, thirst);
            sendDebuffMessage(player, "You are severely dehydrated! Find water immediately!", true);
        }

        // Check if player should exit dehydrated state (recovered enough)
        if (isCurrentlyDehydrated && thirst >= recoveryThreshold) {
            dehydratedPlayers.remove(playerId);
            lastThirstDamageTime.remove(playerId);
            logger.at(Level.INFO).log("Player %s RECOVERED from dehydration (thirst: %.1f >= %.1f)",
                playerId, thirst, recoveryThreshold);
            sendDebuffMessage(player, "You are no longer dehydrated.", false);
            return;
        }

        // Apply damage if currently dehydrated
        if (isCurrentlyDehydrated) {
            var now = System.currentTimeMillis();
            var lastDamage = lastThirstDamageTime.getOrDefault(playerId, 0L);
            var intervalMs = (long) (config.thirst().damageTickIntervalSeconds() * 1000);

            if (now - lastDamage >= intervalMs) {
                applyDamage(ref, store, world, config.thirst().damageAtZero(), "dehydration");
                lastThirstDamageTime.put(playerId, now);

                logWarning(playerId, "Dehydration damage: %.1f", config.thirst().damageAtZero());
            }
        }

        // Low thirst warning (blur threshold) - visual effect would need client-side implementation
        if (thirst > 0 && thirst <= config.thirst().blurStartThreshold()) {
            // TODO: Implement visual blur effect via client-side rendering when API available
            logWarning(playerId, "Thirst low (%.1f) - vision blur threshold reached", thirst);
        }
    }

    /**
     * Process thirst-based speed and stamina regen debuff.
     * Applies gradual reduction as thirst decreases below the slow threshold.
     */
    private void processThirstSpeedDebuff(UUID playerId, double thirst, double slowThreshold,
                                           Ref<EntityStore> ref, Store<EntityStore> store, World world, Player player) {
        var isCurrentlyParched = parchedPlayers.contains(playerId);

        if (thirst < slowThreshold) {
            // Calculate how much to debuff (0 at threshold, max at 0 thirst)
            var debuffRatio = 1.0 - (thirst / slowThreshold);

            // Calculate speed multiplier (1.0 at threshold, minSpeed at 0)
            var speedMultiplier = 1.0f - ((1.0f - config.thirst().minSpeedMultiplier()) * (float) debuffRatio);

            // Calculate stamina regen multiplier (1.0 at threshold, minStaminaRegen at 0)
            var staminaRegenMultiplier = 1.0f - ((1.0f - config.thirst().minStaminaRegenMultiplier()) * (float) debuffRatio);

            // Track that player is parched
            if (!isCurrentlyParched) {
                parchedPlayers.add(playerId);
                logger.at(Level.INFO).log("Player %s entered parched state (thirst: %.1f < %.1f)",
                    playerId, thirst, slowThreshold);
                sendDebuffMessage(player, "You are getting thirsty. Your speed and stamina are reduced.", true);
            }

            // Apply modifiers to entity stats (on WorldThread)
            applyThirstSpeedModifier(playerId, ref, store, world, speedMultiplier);
            applyThirstStaminaRegenModifier(ref, store, world, staminaRegenMultiplier);

            // Log warning periodically
            if (thirst <= slowThreshold / 2) {
                logWarning(playerId, "Thirst debuff: speed=%.2f, stamina_regen=%.2f", speedMultiplier, staminaRegenMultiplier);
            }
        } else if (isCurrentlyParched) {
            // Remove debuff when thirst is above threshold
            parchedPlayers.remove(playerId);
            removeThirstSpeedModifier(playerId, ref, store, world);
            removeThirstStaminaRegenModifier(ref, store, world);
            logger.at(Level.INFO).log("Player %s RECOVERED from parched state (thirst: %.1f >= %.1f)",
                playerId, thirst, slowThreshold);
            sendDebuffMessage(player, "Your thirst is quenched. Speed and stamina restored.", false);
        }
    }

    /**
     * Process energy debuff - reduced movement speed, increased stamina cost, and stamina drain at 0.
     * Uses hysteresis for stamina drain: starts at staminaDrainStartThreshold, stops at staminaDrainRecoveryThreshold.
     */
    private void processEnergyDebuff(UUID playerId, PlayerMetabolismData data,
                                      Ref<EntityStore> ref, Store<EntityStore> store, World world, Player player) {
        if (!config.energy().enabled()) {
            return;
        }

        var energy = data.getEnergy();
        var slowThreshold = config.energy().slowStartThreshold();

        // Track if player just entered tired state for messaging
        boolean wasTired = tiredPlayers.contains(playerId);

        // Speed/stamina consumption debuff when energy is low
        if (energy < slowThreshold) {
            // Calculate how much to debuff (0 at threshold, max at 0 energy)
            var debuffRatio = 1.0 - (energy / slowThreshold);

            // Calculate speed multiplier (1.0 at threshold, minSpeed at 0)
            var speedMultiplier = 1.0f - ((1.0f - config.energy().minSpeedMultiplier()) * (float) debuffRatio);

            // Calculate stamina multiplier (1.0 at threshold, maxStamina at 0)
            var staminaMultiplier = 1.0f + ((config.energy().maxStaminaMultiplier() - 1.0f) * (float) debuffRatio);

            // Track tired state and send message on entry
            if (!wasTired) {
                tiredPlayers.add(playerId);
                sendDebuffMessage(player, "You are getting tired. Your speed is reduced.", true);
            }

            // Apply modifiers to entity stats (on WorldThread)
            applySpeedModifier(playerId, ref, store, world, speedMultiplier);
            applyStaminaModifier(ref, store, world, staminaMultiplier);

            // Log warning at low energy
            if (energy <= slowThreshold / 2) {
                logWarning(playerId, "Energy debuff: speed=%.2f, stamina=%.2f", speedMultiplier, staminaMultiplier);
            }
        } else {
            // Remove modifiers when energy is above threshold (on WorldThread)
            if (wasTired) {
                tiredPlayers.remove(playerId);
                sendDebuffMessage(player, "You feel rested. Speed restored.", false);
            }
            removeSpeedModifier(playerId, ref, store, world);
            removeStaminaModifier(ref, store, world);
        }

        // Stamina drain when energy hits 0 (with hysteresis)
        processStaminaDrain(playerId, data, ref, store, world, player);
    }

    /**
     * Process stamina drain when energy is depleted.
     * Uses hysteresis: drain starts at staminaDrainStartThreshold (0), stops at staminaDrainRecoveryThreshold (50).
     */
    private void processStaminaDrain(UUID playerId, PlayerMetabolismData data,
                                      Ref<EntityStore> ref, Store<EntityStore> store, World world, Player player) {
        var energy = data.getEnergy();
        var isCurrentlyExhausted = exhaustedPlayers.contains(playerId);
        var recoveryThreshold = config.energy().staminaDrainRecoveryThreshold();

        // Check if player should enter exhausted state (energy hit 0)
        if (!isCurrentlyExhausted && energy <= config.energy().staminaDrainStartThreshold()) {
            exhaustedPlayers.add(playerId);
            isCurrentlyExhausted = true;
            logger.at(Level.INFO).log("Player %s entered exhausted state (energy: %.1f) - stamina drain active",
                playerId, energy);
            sendDebuffMessage(player, "You are exhausted! Your stamina is draining rapidly. Rest now!", true);
        }

        // Check if player should exit exhausted state (energy recovered to threshold)
        if (isCurrentlyExhausted && energy >= recoveryThreshold) {
            exhaustedPlayers.remove(playerId);
            lastStaminaDrainTime.remove(playerId);
            logger.at(Level.INFO).log("Player %s RECOVERED from exhaustion (energy: %.1f >= %.1f)",
                playerId, energy, recoveryThreshold);
            sendDebuffMessage(player, "You are no longer exhausted.", false);
            return;
        }

        // Drain stamina if currently exhausted
        if (isCurrentlyExhausted) {
            var now = System.currentTimeMillis();
            var lastDrain = lastStaminaDrainTime.getOrDefault(playerId, 0L);
            var intervalMs = (long) (config.energy().staminaDrainTickIntervalSeconds() * 1000);

            if (now - lastDrain >= intervalMs) {
                var drainAmount = config.energy().staminaDrainPerTick();

                // Drain stamina (on WorldThread)
                drainStamina(ref, store, world, drainAmount);
                lastStaminaDrainTime.put(playerId, now);

                logWarning(playerId, "Exhaustion stamina drain: %.1f", drainAmount);
            }
        }
    }

    /**
     * Apply damage to player's health stat.
     * Uses world.execute() to ensure thread-safe ECS access.
     */
    private void applyDamage(Ref<EntityStore> ref, Store<EntityStore> store, World world,
                              float damage, String damageType) {
        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var healthStatId = DefaultEntityStatTypes.getHealth();
                    statMap.subtractStatValue(healthStatId, damage);
                    logger.at(Level.FINE).log("Applied %s damage: %.1f", damageType, damage);
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error applying %s damage", damageType);
            }
        });
    }

    /**
     * Drain player's stamina stat (exhaustion effect).
     * Uses world.execute() to ensure thread-safe ECS access.
     */
    private void drainStamina(Ref<EntityStore> ref, Store<EntityStore> store, World world, float amount) {
        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var staminaStatId = DefaultEntityStatTypes.getStamina();
                    statMap.subtractStatValue(staminaStatId, amount);
                    logger.at(Level.FINE).log("Drained stamina: %.1f", amount);
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error draining stamina");
            }
        });
    }

    /**
     * Apply speed modifier based on energy level.
     * Uses MovementManager component to modify player movement speed.
     * Uses world.execute() to ensure thread-safe ECS access.
     *
     * @param playerId The player's UUID (for tracking original speed)
     * @param ref The entity reference
     * @param store The entity store
     * @param world The world for thread-safe execution
     * @param multiplier The speed multiplier (e.g., 0.6 = 60% of normal speed)
     */
    private void applySpeedModifier(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store,
                                     World world, float multiplier) {
        world.execute(() -> {
            try {
                var movementManager = store.getComponent(ref, MovementManager.getComponentType());
                if (movementManager != null) {
                    var settings = movementManager.getSettings();
                    if (settings != null) {
                        // Store original base speed if not already stored
                        if (!originalBaseSpeeds.containsKey(playerId)) {
                            originalBaseSpeeds.put(playerId, settings.baseSpeed);
                            logger.at(Level.INFO).log("Stored original base speed for player %s: %.2f",
                                playerId, settings.baseSpeed);
                        }

                        // Calculate new speed
                        float originalSpeed = originalBaseSpeeds.get(playerId);
                        float newSpeed = originalSpeed * multiplier;

                        // Apply the modified speed
                        settings.baseSpeed = newSpeed;
                        currentSpeedMultipliers.put(playerId, multiplier);

                        // Sync the movement settings to the client
                        var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                        if (playerRef != null) {
                            movementManager.update(playerRef.getPacketHandler());
                        }

                        logger.at(Level.INFO).log("Applied energy speed debuff to player %s: %.2f -> %.2f (%.0f%% of normal)",
                            playerId, originalSpeed, newSpeed, multiplier * 100);
                    }
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error applying speed modifier for player %s", playerId);
            }
        });
    }

    /**
     * Apply stamina modifier based on energy level.
     * Reduces max stamina when energy is low, effectively making stamina deplete faster.
     * Uses StaticModifier with MULTIPLICATIVE calculation on MAX value.
     * Uses world.execute() to ensure thread-safe ECS access.
     *
     * @param ref The entity reference
     * @param store The entity store
     * @param world The world for thread-safe execution
     * @param multiplier The stamina multiplier (e.g., 0.7 = 70% max stamina)
     */
    private void applyStaminaModifier(Ref<EntityStore> ref, Store<EntityStore> store,
                                       World world, float multiplier) {
        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var staminaStatId = DefaultEntityStatTypes.getStamina();
                    // Apply a multiplicative modifier to max stamina
                    // multiplier > 1.0 means increased stamina consumption (lower effective max)
                    // We invert: if multiplier is 1.5x consumption, we reduce max by 1/1.5 = 0.67
                    float maxMultiplier = 1.0f / multiplier;
                    var modifier = new StaticModifier(
                        Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.MULTIPLICATIVE,
                        maxMultiplier
                    );
                    statMap.putModifier(staminaStatId, MODIFIER_KEY_ENERGY_STAMINA, modifier);
                    logger.at(Level.FINE).log("Applied energy stamina debuff: %.2fx consumption (%.2f max multiplier)",
                        multiplier, maxMultiplier);
                }
            } catch (Exception e) {
                logger.at(Level.FINE).withCause(e).log("Error applying stamina modifier");
            }
        });
    }

    /**
     * Remove speed modifier when energy recovers.
     * Restores original base speed from stored value.
     * Uses world.execute() to ensure thread-safe ECS access.
     *
     * @param playerId The player's UUID
     * @param ref The entity reference
     * @param store The entity store
     * @param world The world for thread-safe execution
     */
    private void removeSpeedModifier(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        world.execute(() -> {
            try {
                // Only restore if we have stored the original speed and no other debuffs are active
                if (!parchedPlayers.contains(playerId) && originalBaseSpeeds.containsKey(playerId)) {
                    var movementManager = store.getComponent(ref, MovementManager.getComponentType());
                    if (movementManager != null) {
                        var settings = movementManager.getSettings();
                        if (settings != null) {
                            float originalSpeed = originalBaseSpeeds.get(playerId);
                            settings.baseSpeed = originalSpeed;
                            currentSpeedMultipliers.remove(playerId);
                            originalBaseSpeeds.remove(playerId);

                            // Sync the movement settings to the client
                            var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                            if (playerRef != null) {
                                movementManager.update(playerRef.getPacketHandler());
                            }

                            logger.at(Level.INFO).log("Restored original speed for player %s: %.2f",
                                playerId, originalSpeed);
                        }
                    }
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error removing speed modifier for player %s", playerId);
            }
        });
    }

    /**
     * Remove stamina modifier when energy recovers.
     * Uses world.execute() to ensure thread-safe ECS access.
     */
    private void removeStaminaModifier(Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var staminaStatId = DefaultEntityStatTypes.getStamina();
                    statMap.removeModifier(staminaStatId, MODIFIER_KEY_ENERGY_STAMINA);
                    logger.at(Level.FINE).log("Removed energy stamina debuff");
                }
            } catch (Exception e) {
                logger.at(Level.FINE).withCause(e).log("Error removing stamina modifier");
            }
        });
    }

    /**
     * Apply thirst-based speed modifier.
     * Uses MovementManager component to modify player movement speed.
     * Uses world.execute() to ensure thread-safe ECS access.
     *
     * @param playerId The player's UUID (for tracking original speed)
     * @param ref The entity reference
     * @param store The entity store
     * @param world The world for thread-safe execution
     * @param multiplier The speed multiplier (e.g., 0.45 = 45% of normal speed)
     */
    private void applyThirstSpeedModifier(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store,
                                           World world, float multiplier) {
        world.execute(() -> {
            try {
                var movementManager = store.getComponent(ref, MovementManager.getComponentType());
                if (movementManager != null) {
                    var settings = movementManager.getSettings();
                    if (settings != null) {
                        // Store original base speed if not already stored
                        if (!originalBaseSpeeds.containsKey(playerId)) {
                            originalBaseSpeeds.put(playerId, settings.baseSpeed);
                            logger.at(Level.INFO).log("Stored original base speed for player %s: %.2f",
                                playerId, settings.baseSpeed);
                        }

                        // Calculate new speed
                        float originalSpeed = originalBaseSpeeds.get(playerId);
                        float newSpeed = originalSpeed * multiplier;

                        // Apply the modified speed
                        settings.baseSpeed = newSpeed;
                        currentSpeedMultipliers.put(playerId, multiplier);

                        // Sync the movement settings to the client
                        var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                        if (playerRef != null) {
                            movementManager.update(playerRef.getPacketHandler());
                        }

                        logger.at(Level.INFO).log("Applied thirst speed debuff to player %s: %.2f -> %.2f (%.0f%% of normal)",
                            playerId, originalSpeed, newSpeed, multiplier * 100);
                    }
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error applying thirst speed modifier for player %s", playerId);
            }
        });
    }

    /**
     * Apply thirst-based stamina modifier.
     * Reduces max stamina when thirst is low, simulating reduced stamina regeneration.
     * Uses StaticModifier with MULTIPLICATIVE calculation on MAX value.
     * Uses world.execute() to ensure thread-safe ECS access.
     *
     * @param ref The entity reference
     * @param store The entity store
     * @param world The world for thread-safe execution
     * @param multiplier The stamina modifier (e.g., 0.45 = 45% max stamina)
     */
    private void applyThirstStaminaRegenModifier(Ref<EntityStore> ref, Store<EntityStore> store,
                                                  World world, float multiplier) {
        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var staminaStatId = DefaultEntityStatTypes.getStamina();
                    // Apply a multiplicative modifier to max stamina
                    // Lower multiplier = lower max stamina = less effective stamina pool
                    var modifier = new StaticModifier(
                        Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.MULTIPLICATIVE,
                        multiplier
                    );
                    statMap.putModifier(staminaStatId, MODIFIER_KEY_THIRST_STAMINA, modifier);
                    logger.at(Level.FINE).log("Applied thirst stamina debuff: %.2f max multiplier", multiplier);
                }
            } catch (Exception e) {
                logger.at(Level.FINE).withCause(e).log("Error applying thirst stamina modifier");
            }
        });
    }

    /**
     * Remove thirst-based speed modifier.
     * Restores original base speed from stored value.
     * Uses world.execute() to ensure thread-safe ECS access.
     *
     * @param playerId The player's UUID
     * @param ref The entity reference
     * @param store The entity store
     * @param world The world for thread-safe execution
     */
    private void removeThirstSpeedModifier(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        world.execute(() -> {
            try {
                // Only restore if we have stored the original speed and no other debuffs are active
                if (!tiredPlayers.contains(playerId) && originalBaseSpeeds.containsKey(playerId)) {
                    var movementManager = store.getComponent(ref, MovementManager.getComponentType());
                    if (movementManager != null) {
                        var settings = movementManager.getSettings();
                        if (settings != null) {
                            float originalSpeed = originalBaseSpeeds.get(playerId);
                            settings.baseSpeed = originalSpeed;
                            currentSpeedMultipliers.remove(playerId);
                            originalBaseSpeeds.remove(playerId);

                            // Sync the movement settings to the client
                            var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                            if (playerRef != null) {
                                movementManager.update(playerRef.getPacketHandler());
                            }

                            logger.at(Level.INFO).log("Restored original speed for player %s (thirst recovered): %.2f",
                                playerId, originalSpeed);
                        }
                    }
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error removing thirst speed modifier for player %s", playerId);
            }
        });
    }

    /**
     * Remove thirst-based stamina modifier.
     * Uses world.execute() to ensure thread-safe ECS access.
     */
    private void removeThirstStaminaRegenModifier(Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var staminaStatId = DefaultEntityStatTypes.getStamina();
                    statMap.removeModifier(staminaStatId, MODIFIER_KEY_THIRST_STAMINA);
                    logger.at(Level.FINE).log("Removed thirst stamina debuff");
                }
            } catch (Exception e) {
                logger.at(Level.FINE).withCause(e).log("Error removing thirst stamina modifier");
            }
        });
    }

    /**
     * Send a debuff feedback message to the player.
     *
     * @param player The player to send the message to
     * @param message The message text
     * @param isEntering True if entering debuff state (red), false if exiting (green)
     */
    private void sendDebuffMessage(Player player, String message, boolean isEntering) {
        if (player == null) {
            return;
        }
        try {
            var color = isEntering ? ColorUtil.getHexColor("red") : ColorUtil.getHexColor("green");
            player.sendMessage(Message.raw(message).color(color));
        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("Error sending debuff message to player");
        }
    }

    /**
     * Log a warning message with cooldown (for debugging).
     */
    private void logWarning(UUID playerId, String format, Object... args) {
        var now = System.currentTimeMillis();
        var lastWarning = lastWarningTime.getOrDefault(playerId, 0L);

        if (now - lastWarning >= WARNING_COOLDOWN_MS) {
            logger.at(Level.FINE).log("Player %s: " + format, playerId, args);
            lastWarningTime.put(playerId, now);
        }
    }

    /**
     * Clean up tracking data when a player disconnects.
     * Note: ECS references are cleaned up by PlayerRegistry.
     */
    public void removePlayer(UUID playerId) {
        starvationTicks.remove(playerId);
        lastHungerDamageTime.remove(playerId);
        lastThirstDamageTime.remove(playerId);
        lastStaminaDrainTime.remove(playerId);
        starvingPlayers.remove(playerId);
        dehydratedPlayers.remove(playerId);
        exhaustedPlayers.remove(playerId);
        parchedPlayers.remove(playerId);
        tiredPlayers.remove(playerId);
        lastWarningTime.remove(playerId);
        originalBaseSpeeds.remove(playerId);
        currentSpeedMultipliers.remove(playerId);
    }

    /**
     * Checks if a player has any active debuffs.
     * Used by the buff system to determine if buffs should be suppressed.
     */
    public boolean hasActiveDebuffs(UUID playerId) {
        return starvingPlayers.contains(playerId) ||
               dehydratedPlayers.contains(playerId) ||
               exhaustedPlayers.contains(playerId) ||
               parchedPlayers.contains(playerId) ||
               tiredPlayers.contains(playerId);
    }

}
