package com.livinglands.modules.leveling.ability;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.notifications.NotificationModule;
import com.livinglands.modules.metabolism.MetabolismSystem;
import com.livinglands.core.util.SpeedManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages temporary ability buffs with expiration times.
 * Handles speed buffs, metabolism pauses, and stat restores.
 *
 * Features:
 * - Timed buffs that expire after duration
 * - Buff refresh (resets timer) when triggered again while active
 * - Chat + sound feedback on trigger
 * - Stacks additively with metabolism buffs/debuffs
 */
public class TimedBuffManager {

    private static final long TICK_INTERVAL_MS = 500;  // Check every 500ms

    private final PlayerRegistry playerRegistry;
    private final HytaleLogger logger;
    private final ScheduledExecutorService tickExecutor;

    @Nullable
    private SpeedManager speedManager;
    @Nullable
    private MetabolismSystem metabolismSystem;
    @Nullable
    private NotificationModule notificationModule;

    // Active buffs per player: playerId -> (buffType -> expiration time)
    private final Map<UUID, Map<TimedBuffType, Long>> activeBuffs = new ConcurrentHashMap<>();

    // Buff values per player for restoration: playerId -> (buffType -> strength)
    private final Map<UUID, Map<TimedBuffType, Float>> buffStrengths = new ConcurrentHashMap<>();

    public TimedBuffManager(@Nonnull PlayerRegistry playerRegistry,
                            @Nonnull HytaleLogger logger) {
        this.playerRegistry = playerRegistry;
        this.logger = logger;
        this.tickExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TimedBuffManager-Tick");
            t.setDaemon(true);
            return t;
        });
    }

    public void setSpeedManager(@Nullable SpeedManager speedManager) {
        this.speedManager = speedManager;
    }

    public void setMetabolismSystem(@Nullable MetabolismSystem metabolismSystem) {
        this.metabolismSystem = metabolismSystem;
    }

    public void setNotificationModule(@Nullable NotificationModule notificationModule) {
        this.notificationModule = notificationModule;
    }

    /**
     * Start the tick loop to check for expired buffs.
     */
    public void start() {
        tickExecutor.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        logger.at(Level.FINE).log("TimedBuffManager started");
    }

    /**
     * Stop the tick loop and clean up.
     */
    public void shutdown() {
        // Initiate graceful shutdown - prevents new tasks from being submitted
        tickExecutor.shutdown();

        try {
            // Wait for current tick operations to complete (up to 5 seconds)
            if (!tickExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                // Force shutdown if graceful shutdown times out
                logger.at(Level.WARNING).log("TimedBuffManager tick executor did not terminate gracefully, forcing shutdown");
                tickExecutor.shutdownNow();

                // Wait a bit more for forced shutdown to complete
                if (!tickExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.at(Level.SEVERE).log("TimedBuffManager tick executor failed to terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            // If interrupted during shutdown, force immediate shutdown
            logger.at(Level.WARNING).log("Interrupted during TimedBuffManager shutdown, forcing immediate termination");
            tickExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Remove all active buffs after executor is stopped
        for (UUID playerId : activeBuffs.keySet()) {
            removeAllBuffs(playerId);
        }
        activeBuffs.clear();
        buffStrengths.clear();

        logger.at(Level.FINE).log("TimedBuffManager shutdown complete");
    }

    /**
     * Apply a timed buff to a player.
     * If buff is already active, refreshes the duration.
     */
    public void applyBuff(@Nonnull UUID playerId, @Nonnull TimedBuffType buffType,
                          float strength, float durationSeconds,
                          boolean showMessage, boolean playSound) {
        long expirationTime = System.currentTimeMillis() + (long)(durationSeconds * 1000);

        // Get or create player's buff map
        var playerBuffs = activeBuffs.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        var playerStrengths = buffStrengths.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        boolean isRefresh = playerBuffs.containsKey(buffType);

        // Store/update buff
        playerBuffs.put(buffType, expirationTime);
        playerStrengths.put(buffType, strength);

        // Apply the actual effect if not already active
        if (!isRefresh) {
            applyBuffEffect(playerId, buffType, strength);
        }

        // Send feedback
        if (showMessage) {
            sendBuffMessage(playerId, buffType, strength, durationSeconds, isRefresh);
        }

        logger.at(Level.FINE).log("Applied %s buff to %s (strength=%.2f, duration=%.1fs, refresh=%s)",
            buffType, playerId, strength, durationSeconds, isRefresh);
    }

    /**
     * Check if a player has an active buff.
     */
    public boolean hasBuff(@Nonnull UUID playerId, @Nonnull TimedBuffType buffType) {
        var playerBuffs = activeBuffs.get(playerId);
        if (playerBuffs == null) return false;

        Long expiration = playerBuffs.get(buffType);
        return expiration != null && System.currentTimeMillis() < expiration;
    }

    /**
     * Get remaining duration of a buff in seconds.
     */
    public float getRemainingDuration(@Nonnull UUID playerId, @Nonnull TimedBuffType buffType) {
        var playerBuffs = activeBuffs.get(playerId);
        if (playerBuffs == null) return 0f;

        Long expiration = playerBuffs.get(buffType);
        if (expiration == null) return 0f;

        long remaining = expiration - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000f : 0f;
    }

    /**
     * Remove a specific buff from a player.
     */
    public void removeBuff(@Nonnull UUID playerId, @Nonnull TimedBuffType buffType) {
        var playerBuffs = activeBuffs.get(playerId);
        var playerStrengths = buffStrengths.get(playerId);

        if (playerBuffs != null) {
            playerBuffs.remove(buffType);
        }

        Float strength = playerStrengths != null ? playerStrengths.remove(buffType) : null;

        // Remove the actual effect
        removeBuffEffect(playerId, buffType, strength);

        logger.at(Level.FINE).log("Removed %s buff from %s", buffType, playerId);
    }

    /**
     * Remove all buffs from a player.
     */
    public void removeAllBuffs(@Nonnull UUID playerId) {
        var playerBuffs = activeBuffs.remove(playerId);
        var playerStrengths = buffStrengths.remove(playerId);

        if (playerBuffs != null) {
            for (var entry : playerBuffs.entrySet()) {
                Float strength = playerStrengths != null ? playerStrengths.get(entry.getKey()) : null;
                removeBuffEffect(playerId, entry.getKey(), strength);
            }
        }
    }

    /**
     * Clean up when player disconnects.
     */
    public void removePlayer(@Nonnull UUID playerId) {
        removeAllBuffs(playerId);
    }

    /**
     * Get all active buffs for a player with their remaining durations.
     * Used for HUD display.
     *
     * @param playerId The player's UUID
     * @return List of active buff info (type, remaining seconds)
     */
    @Nonnull
    public List<ActiveBuffInfo> getActiveBuffsForDisplay(@Nonnull UUID playerId) {
        List<ActiveBuffInfo> result = new ArrayList<>();
        var playerBuffs = activeBuffs.get(playerId);
        if (playerBuffs == null || playerBuffs.isEmpty()) {
            return result;
        }

        long now = System.currentTimeMillis();
        for (var entry : playerBuffs.entrySet()) {
            long remaining = entry.getValue() - now;
            if (remaining > 0) {
                result.add(new ActiveBuffInfo(entry.getKey(), remaining / 1000f));
            }
        }
        return result;
    }

    /**
     * Info about an active buff for HUD display.
     */
    public record ActiveBuffInfo(TimedBuffType type, float remainingSeconds) {}

    // ========== Private Methods ==========

    private void tick() {
        long now = System.currentTimeMillis();

        for (var playerEntry : activeBuffs.entrySet()) {
            UUID playerId = playerEntry.getKey();
            var playerBuffs = playerEntry.getValue();

            // Check each buff for expiration
            var iterator = playerBuffs.entrySet().iterator();
            while (iterator.hasNext()) {
                var buffEntry = iterator.next();
                if (now >= buffEntry.getValue()) {
                    // Buff expired
                    TimedBuffType buffType = buffEntry.getKey();
                    var playerStrengths = buffStrengths.get(playerId);
                    Float strength = playerStrengths != null ? playerStrengths.remove(buffType) : null;

                    removeBuffEffect(playerId, buffType, strength);
                    iterator.remove();

                    logger.at(Level.FINE).log("Buff %s expired for %s", buffType, playerId);

                    // Notify player
                    sendBuffExpiredMessage(playerId, buffType);
                }
            }
        }
    }

    private void applyBuffEffect(UUID playerId, TimedBuffType buffType, float strength) {
        switch (buffType) {
            case SPEED_BOOST -> {
                if (speedManager != null) {
                    // Atomically add to buff multiplier to prevent race conditions
                    // when multiple abilities trigger simultaneously
                    speedManager.addBuffMultiplier(playerId, strength);
                }
            }
            case HUNGER_PAUSE -> {
                if (metabolismSystem != null) {
                    metabolismSystem.pauseHungerDepletion(playerId, true);
                }
            }
            case STAMINA_PAUSE -> {
                if (metabolismSystem != null) {
                    metabolismSystem.pauseStaminaDepletion(playerId, true);
                }
            }
            // ENERGY_RESTORE, HEALTH_RESTORE, HUNGER_THIRST_RESTORE are instant - handled at trigger time
        }
    }

    private void removeBuffEffect(UUID playerId, TimedBuffType buffType, @Nullable Float strength) {
        switch (buffType) {
            case SPEED_BOOST -> {
                if (speedManager != null && strength != null) {
                    // Atomically subtract from buff multiplier to prevent race conditions
                    // The addBuffMultiplier method handles negative values and ensures non-negative result
                    speedManager.addBuffMultiplier(playerId, -strength);
                }
            }
            case HUNGER_PAUSE -> {
                if (metabolismSystem != null) {
                    metabolismSystem.pauseHungerDepletion(playerId, false);
                }
            }
            case STAMINA_PAUSE -> {
                if (metabolismSystem != null) {
                    metabolismSystem.pauseStaminaDepletion(playerId, false);
                }
            }
        }
    }

    private void sendBuffMessage(UUID playerId, TimedBuffType buffType, float strength, float duration, boolean isRefresh) {
        if (notificationModule == null) return;

        String action = isRefresh ? "refreshed" : "activated";
        String message = switch (buffType) {
            case SPEED_BOOST -> String.format("[Ability] Adrenaline Rush %s! +%.0f%% speed for %.0fs",
                action, strength * 100, duration);
            case HUNGER_PAUSE -> String.format("[Ability] Efficient Extraction %s! Hunger paused for %.0fs",
                action, duration);
            case STAMINA_PAUSE -> String.format("[Ability] Steady Hands %s! Stamina paused for %.0fs",
                action, duration);
            default -> String.format("[Ability] %s %s!", buffType.getDisplayName(), action);
        };

        notificationModule.sendChatAbility(playerId, message);
    }

    private void sendBuffExpiredMessage(UUID playerId, TimedBuffType buffType) {
        if (notificationModule == null) return;

        String message = String.format("[Ability] %s has worn off", buffType.getDisplayName());
        notificationModule.sendChatInfo(playerId, message);
    }

    /**
     * Types of timed buffs that can be applied.
     */
    public enum TimedBuffType {
        SPEED_BOOST("Speed Boost"),
        HUNGER_PAUSE("Hunger Pause"),
        STAMINA_PAUSE("Stamina Pause"),
        ENERGY_RESTORE("Energy Restore"),      // Instant effect
        HEALTH_RESTORE("Health Restore"),      // Instant effect
        HUNGER_THIRST_RESTORE("Nourishment");  // Instant effect

        private final String displayName;

        TimedBuffType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
