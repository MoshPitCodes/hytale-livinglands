package com.livinglands.modules.metabolism.buff;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.metabolism.MetabolismSystem;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * System that processes native buff effects and provides metabolism restoration
 * while buffs are active.
 *
 * When a player has food/potion buffs active:
 * - Optionally restores metabolism stats slowly
 * - Tracks buff effects for duration
 * - Applies tier-based scaling to restoration
 */
public class BuffEffectsSystem {

    private final BuffConfig config;
    private final HytaleLogger logger;
    private final NativeBuffDetector buffDetector;

    private MetabolismSystem metabolismSystem;
    private PlayerRegistry playerRegistry;

    // Track last tick time per player for rate limiting
    private final Map<UUID, Long> lastTickTime = new ConcurrentHashMap<>();

    /**
     * Creates a new buff effects system.
     *
     * @param config The buff configuration
     * @param logger The logger for debug output
     */
    public BuffEffectsSystem(@Nonnull BuffConfig config, @Nonnull HytaleLogger logger) {
        this.config = config;
        this.logger = logger;
        this.buffDetector = new NativeBuffDetector(logger);
    }

    /**
     * Sets the metabolism system and player registry references.
     * Must be called before processing can occur.
     */
    public void setMetabolismSystem(@Nonnull MetabolismSystem metabolismSystem,
                                     @Nonnull PlayerRegistry playerRegistry) {
        this.metabolismSystem = metabolismSystem;
        this.playerRegistry = playerRegistry;
    }

    /**
     * Process buff effects for all tracked players.
     * Called from the metabolism tick loop.
     */
    public void processBuffEffects() {
        if (!config.enabled || !config.foodBuffs.enabled) {
            return;
        }

        if (metabolismSystem == null || playerRegistry == null) {
            return;
        }

        var currentTime = System.currentTimeMillis();
        var intervalMs = (long) (config.tickIntervalSeconds * 1000);

        Set<UUID> trackedPlayers = metabolismSystem.getTrackedPlayerIds();
        for (var playerId : trackedPlayers) {
            // Rate limiting
            var lastTick = lastTickTime.getOrDefault(playerId, 0L);
            if (currentTime - lastTick < intervalMs) {
                continue;
            }

            processPlayerBuffEffects(playerId, currentTime);
            lastTickTime.put(playerId, currentTime);
        }
    }

    /**
     * Process buff effects for a single player.
     */
    private void processPlayerBuffEffects(UUID playerId, long currentTime) {
        try {
            var sessionOpt = playerRegistry.getSession(playerId);
            if (sessionOpt.isEmpty()) {
                return;
            }

            var session = sessionOpt.get();

            // Skip Creative mode players
            if (session.isCreativeMode()) {
                return;
            }

            var world = session.getWorld();
            if (world == null) {
                return;
            }

            // Execute buff detection on WorldThread
            world.execute(() -> {
                try {
                    var activeBuffs = buffDetector.getActiveBuffs(session);
                    if (!activeBuffs.isEmpty()) {
                        processMetabolismRestoration(playerId, activeBuffs);
                    }
                } catch (Exception e) {
                    logger.at(Level.FINE).withCause(e).log(
                        "Error processing buff effects for player %s", playerId
                    );
                }
            });

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Error in buff effects processing for player %s", playerId
            );
        }
    }

    /**
     * Restore metabolism based on active buffs.
     */
    private void processMetabolismRestoration(UUID playerId, List<ActiveBuffDetails> activeBuffs) {
        if (!config.foodBuffs.restoreMetabolismWhileBuffed) {
            return;
        }

        // Calculate total restoration based on buffs and their tiers
        double hungerRestore = 0;
        double thirstRestore = 0;
        double energyRestore = 0;

        for (var buff : activeBuffs) {
            float tierMultiplier = buff.getTierMultiplier();
            double baseRestore = config.foodBuffs.metabolismRestorePerTick;

            switch (buff.buffType()) {
                case DEFENSE -> {
                    // Health/defense buffs restore hunger
                    hungerRestore += baseRestore * tierMultiplier * config.foodBuffs.healthBoostMultiplier;
                }
                case STAMINA_REGEN -> {
                    // Stamina buffs restore thirst and energy
                    thirstRestore += baseRestore * tierMultiplier * config.foodBuffs.staminaBoostMultiplier;
                    energyRestore += baseRestore * 0.5 * tierMultiplier;
                }
                case STRENGTH -> {
                    // Strength buffs restore hunger slowly
                    hungerRestore += baseRestore * 0.5 * tierMultiplier;
                }
                case VITALITY -> {
                    // Vitality buffs restore all stats
                    hungerRestore += baseRestore * 0.3 * tierMultiplier;
                    thirstRestore += baseRestore * 0.3 * tierMultiplier;
                    energyRestore += baseRestore * 0.3 * tierMultiplier;
                }
                default -> {}
            }
        }

        // Apply restoration
        if (hungerRestore > 0) {
            metabolismSystem.restoreHunger(playerId, hungerRestore);
        }
        if (thirstRestore > 0) {
            metabolismSystem.restoreThirst(playerId, thirstRestore);
        }
        if (energyRestore > 0) {
            metabolismSystem.restoreEnergy(playerId, energyRestore);
        }

        if (hungerRestore > 0 || thirstRestore > 0 || energyRestore > 0) {
            logger.at(Level.FINE).log(
                "Buff restoration for %s: hunger +%.2f, thirst +%.2f, energy +%.2f",
                playerId, hungerRestore, thirstRestore, energyRestore
            );
        }
    }

    /**
     * Gets the native buff detector for external use.
     */
    public NativeBuffDetector getBuffDetector() {
        return buffDetector;
    }

    /**
     * Clean up tracking data when a player disconnects.
     */
    public void removePlayer(UUID playerId) {
        lastTickTime.remove(playerId);
    }
}
