package com.livinglands.metabolism.debuff;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.config.DebuffConfig;
import com.livinglands.metabolism.MetabolismSystem;
import com.livinglands.metabolism.poison.PoisonEffectsSystem;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * System that manages all native Hytale debuff effects on players.
 *
 * Handles metabolism drain from debuff categories:
 * - POISON: Moderate all-stat drain (Poison, Poison_T1/T2/T3)
 * - BURN: Very high thirst + high energy drain (Burn, Lava_Burn, Flame_Staff_Burn)
 * - STUN: Very high energy drain (Stun, Bomb_Explode_Stun)
 * - FREEZE: Very high energy drain (Freeze)
 * - ROOT: High energy drain (Root)
 * - SLOW: Medium energy drain (Slow, Two_Handed_Bow_Ability2_Slow)
 *
 * Thread Safety: All ECS access happens via world.execute() on WorldThread.
 * This system maintains compatibility with the legacy PoisonEffectsSystem.
 */
public class DebuffEffectsSystem {

    private final DebuffConfig config;
    private final HytaleLogger logger;

    // Reference to metabolism system for stat manipulation
    private MetabolismSystem metabolismSystem;

    // Reference to player registry for session access
    private PlayerRegistry playerRegistry;

    // Native debuff detector for checking Hytale's debuff effects
    private final NativeDebuffDetector debuffDetector;

    // Track last tick time per player per debuff type to control drain rate
    private final Map<UUID, Map<DebuffType, Long>> lastDebuffTickTime = new ConcurrentHashMap<>();

    /**
     * Creates a new debuff effects system.
     *
     * @param config The debuff configuration
     * @param logger The logger for debug output
     */
    public DebuffEffectsSystem(@Nonnull DebuffConfig config, @Nonnull HytaleLogger logger) {
        this.config = config;
        this.logger = logger;
        this.debuffDetector = new NativeDebuffDetector(logger);
    }

    /**
     * Sets the metabolism system reference for stat manipulation.
     * Must be called after MetabolismSystem is created.
     *
     * @param metabolismSystem The metabolism system
     * @param playerRegistry The player registry for session access
     */
    public void setMetabolismSystem(@Nonnull MetabolismSystem metabolismSystem,
                                    @Nonnull PlayerRegistry playerRegistry) {
        this.metabolismSystem = metabolismSystem;
        this.playerRegistry = playerRegistry;
    }

    /**
     * Processes active debuff effects for all players.
     * Called from the main metabolism tick loop.
     *
     * IMPORTANT: ECS access must happen on the WorldThread, so we schedule
     * the detection to run on each player's world thread.
     */
    public void processDebuffEffects() {
        if (!config.enabled() || metabolismSystem == null || playerRegistry == null) {
            return;
        }

        var currentTime = System.currentTimeMillis();

        // Process all online players
        for (var playerId : metabolismSystem.getTrackedPlayerIds()) {
            var sessionOpt = playerRegistry.getSession(playerId);
            if (sessionOpt.isEmpty()) {
                continue;
            }

            var session = sessionOpt.get();

            // Skip if in Creative mode
            if (session.isCreativeMode()) {
                continue;
            }

            // Skip if world not ready
            var world = session.getWorld();
            if (world == null) {
                continue;
            }

            // Execute ECS access on the WorldThread
            final long capturedCurrentTime = currentTime;
            world.execute(() -> {
                try {
                    processPlayerDebuffs(playerId, session, capturedCurrentTime);
                } catch (Exception e) {
                    // Silently handle ECS access errors
                    logger.at(Level.FINE).log("Error processing debuffs for player %s: %s",
                        playerId, e.getMessage());
                }
            });
        }
    }

    /**
     * Processes all active debuffs for a single player.
     * Must be called from within world.execute() on WorldThread.
     */
    private void processPlayerDebuffs(UUID playerId, com.livinglands.core.PlayerSession session, long currentTime) {
        // Get all active debuffs on the player
        var activeDebuffs = debuffDetector.getActiveDebuffs(session);
        if (activeDebuffs.isEmpty()) {
            // Clean up tracking when no debuffs active
            lastDebuffTickTime.remove(playerId);
            return;
        }

        // Get or create tick time tracking for this player
        var playerTickTimes = lastDebuffTickTime.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        // Process each active debuff
        for (var debuff : activeDebuffs) {
            processDebuff(playerId, debuff, playerTickTimes, currentTime);
        }

        // Clean up tracking for debuff types that are no longer active
        var activeTypes = activeDebuffs.stream()
            .map(d -> d.debuffType())
            .toList();
        playerTickTimes.keySet().removeIf(type -> !activeTypes.contains(type));
    }

    /**
     * Processes a single debuff effect.
     */
    private void processDebuff(UUID playerId,
                               NativeDebuffDetector.ActiveDebuffDetails debuff,
                               Map<DebuffType, Long> playerTickTimes,
                               long currentTime) {
        var debuffType = debuff.debuffType();

        // Get configuration for this debuff type
        var drainConfig = getDebuffDrainConfig(debuffType);
        if (drainConfig == null) {
            return; // Debuff type disabled or not configured
        }

        // Rate limit the drain ticks
        var lastTickTime = playerTickTimes.getOrDefault(debuffType, 0L);
        var intervalMs = (long) (drainConfig.tickIntervalSeconds() * 1000);

        if (currentTime - lastTickTime >= intervalMs) {
            // Apply tier multiplier to drain rates
            var tierMultiplier = debuff.tierMultiplier();

            // Calculate drain amounts with tier scaling
            var hungerDrain = drainConfig.hungerDrainPerTick() * tierMultiplier;
            var thirstDrain = drainConfig.thirstDrainPerTick() * tierMultiplier;
            var energyDrain = drainConfig.energyDrainPerTick() * tierMultiplier;

            // Apply metabolism drain
            drainMetabolism(playerId, hungerDrain, thirstDrain, energyDrain);

            playerTickTimes.put(debuffType, currentTime);

            logger.at(Level.FINE).log("%s debuff drain for player %s (tier: %d, multiplier: %.2f) - H: -%.1f, T: -%.1f, E: -%.1f",
                debuffType, playerId, debuff.tier(), tierMultiplier, hungerDrain, thirstDrain, energyDrain);
        }
    }

    /**
     * Gets the drain configuration for a specific debuff type.
     */
    private DebuffDrainConfig getDebuffDrainConfig(DebuffType debuffType) {
        return switch (debuffType) {
            case POISON -> config.poison().enabled() ?
                new DebuffDrainConfig(
                    config.poison().hungerDrainPerTick(),
                    config.poison().thirstDrainPerTick(),
                    config.poison().energyDrainPerTick(),
                    config.poison().tickIntervalSeconds()
                ) : null;

            case BURN -> config.burn().enabled() ?
                new DebuffDrainConfig(
                    config.burn().hungerDrainPerTick(),
                    config.burn().thirstDrainPerTick(),
                    config.burn().energyDrainPerTick(),
                    config.burn().tickIntervalSeconds()
                ) : null;

            case STUN -> config.stun().enabled() ?
                new DebuffDrainConfig(
                    config.stun().hungerDrainPerTick(),
                    config.stun().thirstDrainPerTick(),
                    config.stun().energyDrainPerTick(),
                    config.stun().tickIntervalSeconds()
                ) : null;

            case FREEZE -> config.freeze().enabled() ?
                new DebuffDrainConfig(
                    config.freeze().hungerDrainPerTick(),
                    config.freeze().thirstDrainPerTick(),
                    config.freeze().energyDrainPerTick(),
                    config.freeze().tickIntervalSeconds()
                ) : null;

            case ROOT -> config.root().enabled() ?
                new DebuffDrainConfig(
                    config.root().hungerDrainPerTick(),
                    config.root().thirstDrainPerTick(),
                    config.root().energyDrainPerTick(),
                    config.root().tickIntervalSeconds()
                ) : null;

            case SLOW -> config.slow().enabled() ?
                new DebuffDrainConfig(
                    config.slow().hungerDrainPerTick(),
                    config.slow().thirstDrainPerTick(),
                    config.slow().energyDrainPerTick(),
                    config.slow().tickIntervalSeconds()
                ) : null;
        };
    }

    /**
     * Drains metabolism stats from a player.
     */
    private void drainMetabolism(UUID playerId, float hungerDrain, float thirstDrain, float energyDrain) {
        var dataOpt = metabolismSystem.getPlayerData(playerId);
        if (dataOpt.isEmpty()) {
            return;
        }

        var data = dataOpt.get();

        if (hungerDrain > 0) {
            var current = data.getHunger();
            data.setHunger(Math.max(0, current - hungerDrain));
        }

        if (thirstDrain > 0) {
            var current = data.getThirst();
            data.setThirst(Math.max(0, current - thirstDrain));
        }

        if (energyDrain > 0) {
            var current = data.getEnergy();
            data.setEnergy(Math.max(0, current - energyDrain));
        }
    }

    /**
     * Removes all tracking for a player (on disconnect).
     */
    public void removePlayer(@Nonnull UUID playerId) {
        lastDebuffTickTime.remove(playerId);
    }

    /**
     * Checks if a player has a specific debuff type active.
     * Note: Requires session with ECS access, should be called within world.execute().
     *
     * @param session The player's session
     * @param debuffType The debuff type to check
     * @return true if the player has this debuff active
     */
    public boolean hasDebuff(@Nonnull com.livinglands.core.PlayerSession session,
                             @Nonnull DebuffType debuffType) {
        return debuffDetector.hasDebuffType(session, debuffType);
    }

    /**
     * Checks if a player has a native poison debuff (backwards compatibility).
     * Note: Requires session with ECS access, should be called within world.execute().
     *
     * @param session The player's session
     * @return true if the player has a poison debuff active
     */
    public boolean hasNativePoisonDebuff(@Nonnull com.livinglands.core.PlayerSession session) {
        return debuffDetector.hasNativePoisonDebuff(session);
    }

    /**
     * Simple record to hold debuff drain configuration.
     */
    private record DebuffDrainConfig(
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float tickIntervalSeconds
    ) {}
}
