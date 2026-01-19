package com.livinglands.modules.metabolism.buff;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.PlayerSession;
import com.livinglands.modules.metabolism.DebuffsSystem;
import com.livinglands.modules.metabolism.PlayerMetabolismData;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import java.util.HashSet;
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
            applyStatModifier(buffType, config.multiplier, ref, store, world);
            sendBuffMessage(player, buffType, true);
            logger.at(Level.INFO).log("Player %s gained %s buff (stat: %.1f >= %.1f)",
                playerId, buffType.getDisplayName(), statValue, config.activationThreshold);
        } else if (isBuffed && statValue < config.deactivationThreshold) {
            // Deactivate buff (hysteresis)
            buffedPlayers.remove(playerId);
            removeStatModifier(buffType, ref, store, world);
            sendBuffMessage(player, buffType, false);
            logger.at(Level.INFO).log("Player %s lost %s buff (stat: %.1f < %.1f)",
                playerId, buffType.getDisplayName(), statValue, config.deactivationThreshold);
        }
    }

    /**
     * Apply a stat modifier for a buff type.
     */
    private void applyStatModifier(BuffType buffType, float multiplier,
                                    Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap == null) {
                    return;
                }

                switch (buffType) {
                    case SPEED -> {
                        // Note: Movement speed stat needs investigation
                        // For now, log that the buff would be applied
                        logger.at(Level.FINE).log("Speed buff would apply: %.2fx (stat ID TBD)", multiplier);
                    }
                    case DEFENSE -> {
                        // Apply max health buff
                        var healthStatId = DefaultEntityStatTypes.getHealth();
                        var modifier = new MultiplyModifier(multiplier);
                        statMap.putModifier(healthStatId, MODIFIER_KEY_HEALTH, modifier);
                        logger.at(Level.FINE).log("Applied health buff: %.2fx", multiplier);
                    }
                    case STAMINA_REGEN -> {
                        // Apply stamina buff
                        var staminaStatId = DefaultEntityStatTypes.getStamina();
                        var modifier = new MultiplyModifier(multiplier);
                        statMap.putModifier(staminaStatId, MODIFIER_KEY_STAMINA, modifier);
                        logger.at(Level.FINE).log("Applied stamina buff: %.2fx", multiplier);
                    }
                    default -> {}
                }
            } catch (Exception e) {
                logger.at(Level.FINE).withCause(e).log("Error applying %s buff modifier", buffType);
            }
        });
    }

    /**
     * Remove a stat modifier for a buff type.
     */
    private void removeStatModifier(BuffType buffType, Ref<EntityStore> ref,
                                     Store<EntityStore> store, World world) {
        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap == null) {
                    return;
                }

                switch (buffType) {
                    case SPEED -> {
                        // statMap.removeModifier(speedStatId, MODIFIER_KEY_SPEED);
                        logger.at(Level.FINE).log("Speed buff removed (stat ID TBD)");
                    }
                    case DEFENSE -> {
                        var healthStatId = DefaultEntityStatTypes.getHealth();
                        statMap.removeModifier(healthStatId, MODIFIER_KEY_HEALTH);
                        logger.at(Level.FINE).log("Removed health buff");
                    }
                    case STAMINA_REGEN -> {
                        var staminaStatId = DefaultEntityStatTypes.getStamina();
                        statMap.removeModifier(staminaStatId, MODIFIER_KEY_STAMINA);
                        logger.at(Level.FINE).log("Removed stamina buff");
                    }
                    default -> {}
                }
            } catch (Exception e) {
                logger.at(Level.FINE).withCause(e).log("Error removing %s buff modifier", buffType);
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
            removeStatModifier(BuffType.SPEED, ref, store, world);
            hadBuffs = true;
        }
        if (defenseBuffedPlayers.remove(playerId)) {
            removeStatModifier(BuffType.DEFENSE, ref, store, world);
            hadBuffs = true;
        }
        if (staminaBuffedPlayers.remove(playerId)) {
            removeStatModifier(BuffType.STAMINA_REGEN, ref, store, world);
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
                } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
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
    }

    /**
     * Simple multiply modifier for stat values.
     */
    private static class MultiplyModifier extends Modifier {
        private final float multiplier;

        public MultiplyModifier(float multiplier) {
            this.multiplier = multiplier;
        }

        @Override
        public float apply(float value) {
            return value * multiplier;
        }
    }
}
