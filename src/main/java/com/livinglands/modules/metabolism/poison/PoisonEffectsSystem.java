package com.livinglands.modules.metabolism.poison;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.config.PoisonConfig;
import com.livinglands.modules.metabolism.MetabolismSystem;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * System that manages poison effects on players.
 * Handles timed metabolism drain effects from:
 * 1. Consuming poisonous items (consumable poison)
 * 2. Native Hytale poison debuffs (from combat, NPCs, etc.)
 *
 * Unlike vanilla poison that damages health, this system drains
 * the player's custom metabolism stats (hunger, thirst, energy).
 *
 * Supports multiple poison types for consumables:
 * - MILD_TOXIN: Short burst of metabolism drain
 * - SLOW_POISON: Extended period of gradual drain
 * - PURGE: Major drain burst followed by faster metabolism recovery
 * - RANDOM: Randomly selects one of the above
 *
 * Native poison debuffs (green skull icon) trigger additional
 * metabolism drain while active based on configured rates.
 */
public class PoisonEffectsSystem {

    private final PoisonConfig config;
    private final HytaleLogger logger;
    private final Random random = new Random();

    // Reference to metabolism system for stat manipulation
    private MetabolismSystem metabolismSystem;

    // Reference to player registry for session access
    private PlayerRegistry playerRegistry;

    // Native poison detector for checking Hytale's poison debuffs
    private final NativePoisonDetector nativePoisonDetector;

    // Active consumable poison state tracking per player
    private final Map<UUID, ActivePoisonState> activePoisonStates = new ConcurrentHashMap<>();

    // Track last native poison tick time per player to control drain rate
    private final Map<UUID, Long> lastNativePoisonTickTime = new ConcurrentHashMap<>();

    /**
     * Tracks active poison effect state for a player.
     */
    private static class ActivePoisonState {
        final PoisonEffectType effectType;
        final long startTime;
        final float durationSeconds;
        int ticksApplied;
        long lastTickTime;
        boolean inRecoveryPhase; // For PURGE effect

        ActivePoisonState(PoisonEffectType effectType, float durationSeconds) {
            this.effectType = effectType;
            this.startTime = System.currentTimeMillis();
            this.durationSeconds = durationSeconds;
            this.ticksApplied = 0;
            this.lastTickTime = 0;
            this.inRecoveryPhase = false;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > (durationSeconds * 1000);
        }
    }

    /**
     * Creates a new poison effects system.
     *
     * @param config The poison configuration
     * @param logger The logger for debug output
     */
    public PoisonEffectsSystem(@Nonnull PoisonConfig config, @Nonnull HytaleLogger logger) {
        this.config = config;
        this.logger = logger;
        this.nativePoisonDetector = new NativePoisonDetector(logger);
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
     * Applies a poison effect to a player.
     * Called when player consumes a poisonous item.
     *
     * @param playerId The player's UUID
     * @param effectType The type of poison effect (or RANDOM)
     */
    public void applyPoison(@Nonnull UUID playerId, @Nonnull PoisonEffectType effectType) {
        if (!config.enabled()) {
            return;
        }

        // Resolve RANDOM to an actual effect type
        var resolvedType = effectType == PoisonEffectType.RANDOM
            ? getRandomEffectType()
            : effectType;

        // Get duration based on effect type
        float duration = switch (resolvedType) {
            case MILD_TOXIN -> config.mildToxin().durationSeconds();
            case SLOW_POISON -> config.slowPoison().durationSeconds();
            case PURGE -> config.purge().drainDurationSeconds() + config.purge().recoveryDurationSeconds();
            case RANDOM -> 10.0f; // Fallback, shouldn't happen
        };

        // Replace any existing poison effect
        activePoisonStates.put(playerId, new ActivePoisonState(resolvedType, duration));

        logger.at(Level.INFO).log("Applied %s poison to player %s (duration: %.1fs)",
            resolvedType, playerId, duration);
    }

    /**
     * Processes active poison effects for all players.
     * Called from the main metabolism tick loop.
     *
     * Handles both:
     * 1. Consumable poison effects (from eating poisonous items)
     * 2. Native Hytale poison debuffs (from combat, NPCs, environment)
     */
    public void processPoisonEffects() {
        if (metabolismSystem == null) {
            return;
        }

        var currentTime = System.currentTimeMillis();

        // Process consumable poison effects
        activePoisonStates.forEach((playerId, state) -> {
            if (state.isExpired()) {
                // Clean up expired effects
                activePoisonStates.remove(playerId);
                logger.at(Level.FINE).log("Consumable poison effect expired for player %s", playerId);
                return;
            }

            processPlayerPoison(playerId, state, currentTime);
        });

        // Process native Hytale poison debuffs for all tracked players
        if (playerRegistry != null && config.nativePoison().enabled()) {
            processNativePoisonDebuffs(currentTime);
        }
    }

    /**
     * Processes native Hytale poison debuffs for all online players.
     * Checks each player's EffectControllerComponent for active poison effects.
     *
     * IMPORTANT: ECS access must happen on the WorldThread, so we schedule
     * the detection to run on each player's world thread.
     */
    private void processNativePoisonDebuffs(long currentTime) {
        var nativeConfig = config.nativePoison();
        var intervalMs = (long) (nativeConfig.tickIntervalSeconds() * 1000);

        // Check all online players
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
                    // Check if player has native poison debuff
                    if (nativePoisonDetector.hasNativePoisonDebuff(session)) {
                        // Rate limit the drain ticks
                        var lastTickTime = lastNativePoisonTickTime.getOrDefault(playerId, 0L);
                        if (capturedCurrentTime - lastTickTime >= intervalMs) {
                            // Get poison details for tier-based drain scaling
                            var detailsOpt = nativePoisonDetector.getActivePoisonDetails(session);
                            float drainMultiplier = detailsOpt
                                .map(d -> getTierDrainMultiplier(d.tier()))
                                .orElse(1.0f);

                            // Apply metabolism drain
                            drainMetabolism(
                                playerId,
                                nativeConfig.hungerDrainPerTick() * drainMultiplier,
                                nativeConfig.thirstDrainPerTick() * drainMultiplier,
                                nativeConfig.energyDrainPerTick() * drainMultiplier
                            );

                            lastNativePoisonTickTime.put(playerId, capturedCurrentTime);

                            logger.at(Level.FINE).log("Native poison drain for player %s (tier multiplier: %.1f)",
                                playerId, drainMultiplier);
                        }
                    } else {
                        // Clean up tick tracking when poison ends
                        lastNativePoisonTickTime.remove(playerId);
                    }
                } catch (Exception e) {
                    // Silently handle ECS access errors
                }
            });
        }
    }

    /**
     * Gets the drain multiplier based on poison tier.
     * Higher tiers cause more metabolism drain.
     */
    private float getTierDrainMultiplier(int tier) {
        return switch (tier) {
            case 1 -> 0.75f;  // T1 - lighter poison
            case 2 -> 1.0f;   // T2 - standard poison
            case 3 -> 1.5f;   // T3 - severe poison
            default -> 1.0f;  // Base poison
        };
    }

    /**
     * Processes poison effect for a single player.
     */
    private void processPlayerPoison(UUID playerId, ActivePoisonState state, long currentTime) {
        // Process based on effect type
        switch (state.effectType) {
            case MILD_TOXIN -> processMildToxin(playerId, state, currentTime);
            case SLOW_POISON -> processSlowPoison(playerId, state, currentTime);
            case PURGE -> processPurge(playerId, state, currentTime);
            default -> {} // RANDOM should have been resolved
        }
    }

    /**
     * Processes MILD_TOXIN effect: Short burst of metabolism drain.
     * Drains hunger and thirst quickly over a short period.
     */
    private void processMildToxin(UUID playerId, ActivePoisonState state, long currentTime) {
        var cfg = config.mildToxin();
        var intervalMs = (long) (cfg.tickIntervalSeconds() * 1000);

        if (currentTime - state.lastTickTime >= intervalMs) {
            // Drain hunger and thirst
            drainMetabolism(playerId, cfg.hungerDrainPerTick(), cfg.thirstDrainPerTick(), cfg.energyDrainPerTick());
            state.lastTickTime = currentTime;
            state.ticksApplied++;

            logger.at(Level.FINE).log("Mild toxin tick %d for player %s (hunger: -%.1f, thirst: -%.1f, energy: -%.1f)",
                state.ticksApplied, playerId, cfg.hungerDrainPerTick(), cfg.thirstDrainPerTick(), cfg.energyDrainPerTick());
        }
    }

    /**
     * Processes SLOW_POISON effect: Extended period of gradual metabolism drain.
     * Lower drain per tick but lasts much longer.
     */
    private void processSlowPoison(UUID playerId, ActivePoisonState state, long currentTime) {
        var cfg = config.slowPoison();
        var intervalMs = (long) (cfg.tickIntervalSeconds() * 1000);

        if (currentTime - state.lastTickTime >= intervalMs) {
            // Drain hunger and thirst slowly
            drainMetabolism(playerId, cfg.hungerDrainPerTick(), cfg.thirstDrainPerTick(), cfg.energyDrainPerTick());
            state.lastTickTime = currentTime;
            state.ticksApplied++;

            logger.at(Level.FINE).log("Slow poison tick %d for player %s (hunger: -%.1f, thirst: -%.1f, energy: -%.1f)",
                state.ticksApplied, playerId, cfg.hungerDrainPerTick(), cfg.thirstDrainPerTick(), cfg.energyDrainPerTick());
        }
    }

    /**
     * Processes PURGE effect: Major drain burst followed by faster metabolism.
     * Severe initial drain but metabolism stats stabilize/recover faster afterward.
     */
    private void processPurge(UUID playerId, ActivePoisonState state, long currentTime) {
        var cfg = config.purge();

        if (!state.inRecoveryPhase) {
            // Drain phase - rapid metabolism drain
            var intervalMs = (long) (cfg.drainIntervalSeconds() * 1000);
            var drainTicks = (int) (cfg.drainDurationSeconds() / cfg.drainIntervalSeconds());

            if (state.ticksApplied < drainTicks && currentTime - state.lastTickTime >= intervalMs) {
                drainMetabolism(playerId, cfg.hungerDrainPerTick(), cfg.thirstDrainPerTick(), cfg.energyDrainPerTick());
                state.lastTickTime = currentTime;
                state.ticksApplied++;

                logger.at(Level.FINE).log("Purge drain tick %d for player %s (hunger: -%.1f, thirst: -%.1f, energy: -%.1f)",
                    state.ticksApplied, playerId, cfg.hungerDrainPerTick(), cfg.thirstDrainPerTick(), cfg.energyDrainPerTick());

                // Transition to recovery phase after all drain ticks
                if (state.ticksApplied >= drainTicks) {
                    state.inRecoveryPhase = true;
                    state.lastTickTime = currentTime;
                    logger.at(Level.INFO).log("Player %s entering purge recovery phase", playerId);
                }
            }
        }
        // Recovery phase - no drain, just let it expire naturally
        // The metabolism system's natural recovery will feel faster since drain stopped
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
     * Gets a random poison effect type (excluding RANDOM itself).
     */
    private PoisonEffectType getRandomEffectType() {
        var types = new PoisonEffectType[] {
            PoisonEffectType.MILD_TOXIN,
            PoisonEffectType.SLOW_POISON,
            PoisonEffectType.PURGE
        };
        return types[random.nextInt(types.length)];
    }

    /**
     * Checks if a player currently has an active consumable poison effect.
     */
    public boolean hasConsumablePoison(@Nonnull UUID playerId) {
        return activePoisonStates.containsKey(playerId);
    }

    /**
     * Checks if a player has an active consumable poison effect.
     *
     * Note: This only checks consumable poison, not native Hytale poison debuffs,
     * because native poison detection requires ECS access which must run on
     * the WorldThread. Use the native poison detection in processPoisonEffects()
     * for full poison checking.
     *
     * @param playerId The player's UUID
     * @return true if poisoned from consumable poison
     */
    public boolean isPoisoned(@Nonnull UUID playerId) {
        // Only check consumable poison (ECS access for native poison requires WorldThread)
        return activePoisonStates.containsKey(playerId);
    }

    /**
     * Removes consumable poison effect from a player (e.g., antidote).
     * Note: Does not remove native Hytale poison debuffs.
     */
    public void removePoison(@Nonnull UUID playerId) {
        var removed = activePoisonStates.remove(playerId);
        if (removed != null) {
            logger.at(Level.INFO).log("Removed consumable poison effect from player %s", playerId);
        }
    }

    /**
     * Removes all tracking for a player (on disconnect).
     */
    public void removePlayer(@Nonnull UUID playerId) {
        activePoisonStates.remove(playerId);
        lastNativePoisonTickTime.remove(playerId);
    }
}
