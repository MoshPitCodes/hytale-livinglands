package com.livinglands.modules.metabolism.buff;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.metabolism.DebuffsSystem;
import com.livinglands.modules.metabolism.PlayerMetabolismData;
import com.livinglands.core.util.SpeedManager;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * System that applies buff effects based on high metabolism stat levels.
 *
 * Buffs are positive effects that enhance player abilities when metabolism is high:
 * - Energy >= 90%: Speed buff (movement speed increase)
 * - Hunger >= 90%: Defense buff (max health increase)
 * - Thirst >= 90%: Stamina buff (max stamina increase)
 *
 * Buffs are suppressed when the player has active debuffs (debuffs take priority).
 *
 * Uses PlayerRegistry for ECS reference access (thread-safe via world.execute()).
 */
public class BuffsSystem {

    private static final String MODIFIER_KEY_SPEED = "livinglands_buff_speed";
    private static final String MODIFIER_KEY_HEALTH = "livinglands_buff_health";
    private static final String MODIFIER_KEY_STAMINA = "livinglands_buff_stamina";

    private final BuffConfig config;
    private final HytaleLogger logger;
    private final PlayerRegistry playerRegistry;
    private DebuffsSystem debuffsSystem;
    private SpeedManager speedManager;

    // Track which players have which buffs active
    private final Set<UUID> speedBuffedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> defenseBuffedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staminaBuffedPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new buffs system.
     *
     * @param config The buff configuration
     * @param logger The logger for debug output
     * @param playerRegistry The central player registry for ECS access
     */
    public BuffsSystem(@Nonnull BuffConfig config, @Nonnull HytaleLogger logger,
                        @Nonnull PlayerRegistry playerRegistry) {
        this.config = config;
        this.logger = logger;
        this.playerRegistry = playerRegistry;
    }

    /**
     * Sets the debuffs system reference (for checking if debuffs are active).
     */
    public void setDebuffsSystem(DebuffsSystem debuffsSystem) {
        this.debuffsSystem = debuffsSystem;
    }

    /**
     * Sets the centralized speed manager.
     * Must be called before processing buffs.
     */
    public void setSpeedManager(@Nonnull SpeedManager speedManager) {
        this.speedManager = speedManager;
    }

    /**
     * Process buffs for a player based on their current stats.
     * Called from the metabolism tick loop.
     *
     * @param playerId The player's UUID
     * @param data The player's current metabolism data
     */
    public void processBuffs(UUID playerId, PlayerMetabolismData data) {
        if (!config.enabled) {
            return;
        }

        try {
            // Get player session from registry
            var sessionOpt = playerRegistry.getSession(playerId);
            if (sessionOpt.isEmpty()) {
                return;
            }

            var session = sessionOpt.get();
            if (!session.isEcsReady()) {
                return;
            }

            var ref = session.getEntityRef();
            var store = session.getStore();
            var world = session.getWorld();

            if (ref == null || store == null || world == null) {
                return;
            }

            // Check if player has active debuffs - if so, remove all buffs
            if (debuffsSystem != null && debuffsSystem.hasActiveDebuffs(playerId)) {
                removeAllBuffs(playerId, ref, store, world, session.getPlayer());
                return;
            }

            // Process each buff type
            if (config.energy.enabled) {
                processStatBuff(playerId, data.getEnergy(), config.energy,
                    speedBuffedPlayers, BuffType.SPEED, ref, store, world, session.getPlayer());
            }

            if (config.hunger.enabled) {
                processStatBuff(playerId, data.getHunger(), config.hunger,
                    defenseBuffedPlayers, BuffType.DEFENSE, ref, store, world, session.getPlayer());
            }

            if (config.thirst.enabled) {
                processStatBuff(playerId, data.getThirst(), config.thirst,
                    staminaBuffedPlayers, BuffType.STAMINA_REGEN, ref, store, world, session.getPlayer());
            }

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Error processing buffs for player %s", playerId
            );
        }
    }

    /**
     * Process a single stat-based buff.
     */
    private void processStatBuff(UUID playerId, double statValue, BuffConfig.StatBuffConfig config,
                                  Set<UUID> buffedPlayers, BuffType buffType,
                                  Ref<EntityStore> ref, Store<EntityStore> store, World world, Player player) {
        boolean isBuffed = buffedPlayers.contains(playerId);

        if (!isBuffed && statValue >= config.activationThreshold) {
            // Activate buff
            buffedPlayers.add(playerId);
            applyStatModifier(playerId, buffType, config.multiplier, ref, store, world);
            sendBuffMessage(player, buffType, true);
            logger.at(Level.FINE).log("Player %s gained %s buff (stat: %.1f >= %.1f)",
                playerId, buffType.getDisplayName(), statValue, config.activationThreshold);
        } else if (isBuffed && statValue < config.deactivationThreshold) {
            // Deactivate buff (hysteresis)
            buffedPlayers.remove(playerId);
            removeStatModifier(playerId, buffType, ref, store, world);
            sendBuffMessage(player, buffType, false);
            logger.at(Level.FINE).log("Player %s lost %s buff (stat: %.1f < %.1f)",
                playerId, buffType.getDisplayName(), statValue, config.deactivationThreshold);
        }
    }

    /**
     * Apply a stat modifier for a buff type.
     *
     * @param playerId The player's UUID (for speed tracking)
     * @param buffType The type of buff to apply
     * @param multiplier The buff multiplier
     * @param ref The entity reference
     * @param store The entity store
     * @param world The world for thread-safe execution
     */
    private void applyStatModifier(UUID playerId, BuffType buffType, float multiplier,
                                    Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        // Handle SPEED buff via centralized SpeedManager (not on WorldThread)
        if (buffType == BuffType.SPEED) {
            if (speedManager != null) {
                speedManager.setBuffMultiplier(playerId, multiplier);
                logger.at(Level.FINE).log("Set speed buff multiplier for player %s: %.2f", playerId, multiplier);
            }
            return;
        }

        world.execute(() -> {
            try {
                switch (buffType) {
                    case DEFENSE -> {
                        // Apply max health buff using StaticModifier with MULTIPLICATIVE calculation
                        var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                        if (statMap != null) {
                            var healthStatId = DefaultEntityStatTypes.getHealth();
                            var modifier = new StaticModifier(
                                Modifier.ModifierTarget.MAX,
                                StaticModifier.CalculationType.MULTIPLICATIVE,
                                multiplier
                            );
                            statMap.putModifier(healthStatId, MODIFIER_KEY_HEALTH, modifier);
                            logger.at(Level.FINE).log("Applied health buff to player %s: %.2fx", playerId, multiplier);
                        }
                    }
                    case STAMINA_REGEN -> {
                        // Apply stamina buff using StaticModifier with MULTIPLICATIVE calculation
                        var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                        if (statMap != null) {
                            var staminaStatId = DefaultEntityStatTypes.getStamina();
                            var modifier = new StaticModifier(
                                Modifier.ModifierTarget.MAX,
                                StaticModifier.CalculationType.MULTIPLICATIVE,
                                multiplier
                            );
                            statMap.putModifier(staminaStatId, MODIFIER_KEY_STAMINA, modifier);
                            logger.at(Level.FINE).log("Applied stamina buff to player %s: %.2fx", playerId, multiplier);
                        }
                    }
                    default -> {}
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error applying %s buff modifier for player %s", buffType, playerId);
            }
        });
    }

    /**
     * Remove a stat modifier for a buff type.
     *
     * @param playerId The player's UUID (for speed tracking)
     * @param buffType The type of buff to remove
     * @param ref The entity reference
     * @param store The entity store
     * @param world The world for thread-safe execution
     */
    private void removeStatModifier(UUID playerId, BuffType buffType, Ref<EntityStore> ref,
                                     Store<EntityStore> store, World world) {
        // Handle SPEED buff via centralized SpeedManager (not on WorldThread)
        if (buffType == BuffType.SPEED) {
            if (speedManager != null) {
                speedManager.clearBuffMultiplier(playerId);
                logger.at(Level.FINE).log("Cleared speed buff multiplier for player %s", playerId);
            }
            return;
        }

        world.execute(() -> {
            try {
                switch (buffType) {
                    case DEFENSE -> {
                        var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                        if (statMap != null) {
                            var healthStatId = DefaultEntityStatTypes.getHealth();
                            statMap.removeModifier(healthStatId, MODIFIER_KEY_HEALTH);
                            logger.at(Level.FINE).log("Removed health buff from player %s", playerId);
                        }
                    }
                    case STAMINA_REGEN -> {
                        var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                        if (statMap != null) {
                            var staminaStatId = DefaultEntityStatTypes.getStamina();
                            statMap.removeModifier(staminaStatId, MODIFIER_KEY_STAMINA);
                            logger.at(Level.FINE).log("Removed stamina buff from player %s", playerId);
                        }
                    }
                    default -> {}
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error removing %s buff modifier for player %s", buffType, playerId);
            }
        });
    }

    /**
     * Remove all buffs from a player (e.g., when debuffs are active).
     */
    private void removeAllBuffs(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store,
                                 World world, Player player) {
        boolean hadBuffs = false;

        if (speedBuffedPlayers.remove(playerId)) {
            removeStatModifier(playerId, BuffType.SPEED, ref, store, world);
            hadBuffs = true;
        }
        if (defenseBuffedPlayers.remove(playerId)) {
            removeStatModifier(playerId, BuffType.DEFENSE, ref, store, world);
            hadBuffs = true;
        }
        if (staminaBuffedPlayers.remove(playerId)) {
            removeStatModifier(playerId, BuffType.STAMINA_REGEN, ref, store, world);
            hadBuffs = true;
        }

        if (hadBuffs) {
            logger.at(Level.FINE).log("Removed all buffs from player %s (debuffs active)", playerId);
            if (player != null) {
                try {
                    player.sendMessage(
                        Message.raw("Buffs suppressed - metabolism too low")
                            .color(ColorUtil.getHexColor("gray"))
                    );
                } catch (Exception e) {
                    logger.at(Level.FINE).withCause(e).log("Failed to send buff suppression message to player %s", playerId);
                }
            }
        }
    }

    /**
     * Send a message to the player about buff status.
     */
    private void sendBuffMessage(Player player, BuffType buffType, boolean activated) {
        if (player == null) {
            return;
        }

        String message = activated
            ? String.format("%s activated!", buffType.getDisplayName())
            : String.format("%s expired.", buffType.getDisplayName());

        String color = activated
            ? ColorUtil.getHexColor("green")
            : ColorUtil.getHexColor("gray");

        try {
            player.sendMessage(Message.raw(message).color(color));
        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("Failed to send buff message to player");
        }
    }

    /**
     * Gets all active buffs for a player.
     *
     * @param playerId The player's UUID
     * @return Set of active buff types
     */
    public Set<BuffType> getActiveBuffs(UUID playerId) {
        var buffs = new HashSet<BuffType>();
        if (speedBuffedPlayers.contains(playerId)) {
            buffs.add(BuffType.SPEED);
        }
        if (defenseBuffedPlayers.contains(playerId)) {
            buffs.add(BuffType.DEFENSE);
        }
        if (staminaBuffedPlayers.contains(playerId)) {
            buffs.add(BuffType.STAMINA_REGEN);
        }
        return buffs;
    }

    /**
     * Checks if a player has any active buffs.
     */
    public boolean hasActiveBuffs(UUID playerId) {
        return speedBuffedPlayers.contains(playerId) ||
               defenseBuffedPlayers.contains(playerId) ||
               staminaBuffedPlayers.contains(playerId);
    }

    /**
     * Clean up tracking data when a player disconnects.
     */
    public void removePlayer(UUID playerId) {
        speedBuffedPlayers.remove(playerId);
        defenseBuffedPlayers.remove(playerId);
        staminaBuffedPlayers.remove(playerId);
        // Note: SpeedManager cleanup is handled separately
    }

    /**
     * Clean up any stale buff modifiers that Hytale may have persisted from a previous session.
     * Called on player login/ready before the first metabolism tick runs.
     *
     * This ensures players start with clean stats and buffs are re-applied based on
     * actual current metabolism levels, not persisted modifiers from old sessions.
     *
     * @param playerId The player's UUID
     */
    public void cleanupStaleModifiers(UUID playerId) {
        if (!config.enabled) {
            return;
        }

        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty() || !sessionOpt.get().isEcsReady()) {
            return;
        }

        var session = sessionOpt.get();
        var ref = session.getEntityRef();
        var store = session.getStore();
        var world = session.getWorld();

        if (ref == null || store == null || world == null) {
            return;
        }

        // Remove any persisted buff modifiers - they'll be re-applied by processBuffs()
        // based on actual current metabolism levels
        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap == null) return;

                // Remove health buff modifier if it exists
                var healthStatId = DefaultEntityStatTypes.getHealth();
                statMap.removeModifier(healthStatId, MODIFIER_KEY_HEALTH);

                // Remove stamina buff modifier if it exists
                var staminaStatId = DefaultEntityStatTypes.getStamina();
                statMap.removeModifier(staminaStatId, MODIFIER_KEY_STAMINA);

                logger.at(Level.FINE).log("Cleaned up stale metabolism buff modifiers for %s", playerId);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log(
                    "Failed to clean up stale buff modifiers for %s", playerId
                );
            }
        });

        // Clear tracking state - buffs will be re-evaluated on next tick
        speedBuffedPlayers.remove(playerId);
        defenseBuffedPlayers.remove(playerId);
        staminaBuffedPlayers.remove(playerId);
    }
}
