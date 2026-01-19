package com.livinglands.modules.metabolism;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.persistence.PlayerDataPersistence;
import com.livinglands.modules.metabolism.config.MetabolismModuleConfig;
import com.livinglands.modules.metabolism.listeners.FoodConsumptionProcessor;
import com.livinglands.modules.metabolism.poison.PoisonEffectsSystem;
import com.livinglands.modules.metabolism.debuff.DebuffEffectsSystem;

import java.nio.file.Path;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

/**
 * Main system for managing player metabolism (hunger and thirst).
 *
 * Responsibilities:
 * - Periodic stat depletion based on activity
 * - Managing activity multipliers
 * - Coordinating with configuration
 * - Thread-safe player data management
 * - Applying debuffs based on stat levels
 */
public class MetabolismSystem {

    // Update frequency for metabolism depletion (1 second intervals)
    private static final long TICK_INTERVAL_MS = 1000L;

    // Update frequency for food effect detection (50ms to catch 100ms instant heals)
    private static final long EFFECT_DETECTION_INTERVAL_MS = 50L;

    private final MetabolismModuleConfig config;
    private final HytaleLogger logger;
    private final PlayerRegistry playerRegistry;
    private final Map<UUID, PlayerMetabolismData> playerData;
    private final ScheduledExecutorService executor;

    // Debuffs system for applying effects when stats are low
    private final DebuffsSystem debuffsSystem;

    // Poison effects system for timed damage from poisonous items
    private final PoisonEffectsSystem poisonEffectsSystem;

    // Native debuff effects system for all Hytale debuffs (poison, burn, stun, freeze, root, slow)
    private final DebuffEffectsSystem debuffEffectsSystem;

    // Food consumption processor for detecting food buff effects
    private final FoodConsumptionProcessor foodConsumptionProcessor;

    // Activity detector for reading player movement state from ECS
    private final ActivityDetector activityDetector;

    // Data persistence for saving/loading player stats
    private final PlayerDataPersistence persistence;

    private ScheduledFuture<?> tickTask;
    private ScheduledFuture<?> effectDetectionTask;
    private volatile boolean running = false;

    /**
     * Creates a new metabolism system.
     *
     * @param config The mod configuration
     * @param logger The logger for debug output
     * @param playerRegistry The central player registry
     * @param pluginDirectory The plugin's data directory for persistence
     */
    public MetabolismSystem(@Nonnull MetabolismModuleConfig config, @Nonnull HytaleLogger logger,
                            @Nonnull PlayerRegistry playerRegistry, @Nonnull Path pluginDirectory) {
        this.config = config;
        this.logger = logger;
        this.playerRegistry = playerRegistry;
        this.playerData = new ConcurrentHashMap<>();
        this.debuffsSystem = new DebuffsSystem(config.debuffs, logger, playerRegistry);
        this.poisonEffectsSystem = new PoisonEffectsSystem(config.poison, logger);
        this.poisonEffectsSystem.setMetabolismSystem(this, playerRegistry); // Wire back-reference for stat manipulation
        this.debuffEffectsSystem = new DebuffEffectsSystem(config.nativeDebuffs, logger);
        this.debuffEffectsSystem.setMetabolismSystem(this, playerRegistry); // Wire back-reference for stat manipulation
        this.foodConsumptionProcessor = new FoodConsumptionProcessor(logger, this, playerRegistry);
        this.activityDetector = new ActivityDetector(logger);
        this.persistence = new PlayerDataPersistence(pluginDirectory.resolve("playerdata"), logger);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "LivingLands-Metabolism");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts the metabolism system.
     * Begins periodic tick updates for all players.
     */
    public void start() {
        if (running) {
            return;
        }

        // Main metabolism tick (depletion, debuffs, poison) - every 1 second
        tickTask = executor.scheduleAtFixedRate(
            this::tick,
            0,
            TICK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        // High-frequency effect detection tick - every 50ms to catch instant heals (100ms duration)
        effectDetectionTask = executor.scheduleAtFixedRate(
            this::effectDetectionTick,
            0,
            EFFECT_DETECTION_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        running = true;
    }

    /**
     * Stops the metabolism system.
     */
    public void stop() {
        if (!running) {
            return;
        }

        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }

        if (effectDetectionTask != null) {
            effectDetectionTask.cancel(false);
            effectDetectionTask = null;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        running = false;
    }

    /**
     * Main tick update - called every 1 second.
     * Processes metabolism depletion, debuffs, and poison for all online players.
     */
    private void tick() {
        try {
            var currentTime = System.currentTimeMillis();

            // Process all tracked players (depletion, activity, debuffs)
            playerData.values().forEach(data -> processPlayer(data, currentTime));

            // Process active poison effects (from consumables)
            poisonEffectsSystem.processPoisonEffects();

            // Process native Hytale debuff effects (poison, burn, stun, freeze, root, slow)
            debuffEffectsSystem.processDebuffEffects();

        } catch (Exception e) {
            // Silently handle tick errors to avoid spam
        }
    }

    /**
     * High-frequency effect detection tick - called every 50ms.
     * Detects food consumption by monitoring for new food buff effects.
     * This runs faster than the main tick to catch instant heals (100ms duration).
     */
    private void effectDetectionTick() {
        try {
            // Process food consumption detection for all tracked players
            playerData.keySet().forEach(foodConsumptionProcessor::processPlayer);
        } catch (Exception e) {
            // Silently handle tick errors to avoid spam
        }
    }

    /**
     * Processes a single player's metabolism.
     * Skips processing if player is in Creative mode.
     *
     * @param data Player metabolism data
     * @param currentTime Current server time in milliseconds
     */
    private void processPlayer(PlayerMetabolismData data, long currentTime) {
        try {
            // Skip metabolism processing for Creative mode players
            var sessionOpt = playerRegistry.getSession(data.getPlayerUuid());
            if (sessionOpt.isPresent() && sessionOpt.get().isCreativeMode()) {
                return; // Metabolism paused in Creative mode
            }

            var metabolism = config.metabolism;

            // Detect real activity state from ECS
            detectAndUpdateActivity(data, currentTime);

            // Calculate activity multiplier
            var activity = metabolism.activityMultipliers;
            data.calculateActivityMultiplier(
                activity.idle,
                activity.walking,
                activity.sprinting,
                activity.swimming,
                activity.combat
            );

            // Deplete hunger if enabled
            if (metabolism.enableHunger) {
                depleteHunger(data, currentTime);
            }

            // Deplete thirst if enabled
            if (metabolism.enableThirst) {
                depleteThirst(data, currentTime);
            }

            // Deplete energy if enabled
            if (metabolism.enableEnergy) {
                depleteEnergy(data, currentTime);
            }

            // Process debuffs based on current stat levels
            debuffsSystem.processDebuffs(data.getPlayerUuid(), data);

        } catch (Exception e) {
            // Silently handle player processing errors
        }
    }

    /**
     * Detects player activity from ECS and updates metabolism data.
     */
    private void detectAndUpdateActivity(PlayerMetabolismData data, long currentTime) {
        var playerId = data.getPlayerUuid();
        var sessionOpt = playerRegistry.getSession(playerId);

        if (sessionOpt.isEmpty()) {
            // Fall back to idle if no session
            data.updateActivity(false, false, currentTime);
            return;
        }

        var session = sessionOpt.get();
        if (!session.isEcsReady()) {
            // ECS not ready, fall back to current state
            return;
        }

        var ref = session.getEntityRef();
        var store = session.getStore();
        var world = session.getWorld();

        if (ref == null || store == null || world == null) {
            return;
        }

        // Detect activity from ECS MovementStatesComponent
        var activityState = activityDetector.detectActivity(ref, store, world);
        activityDetector.updatePlayerActivity(data, activityState, currentTime);
    }

    /**
     * Depletes hunger based on time and activity.
     */
    private void depleteHunger(PlayerMetabolismData data, long currentTime) {
        var metabolism = config.metabolism;
        var depletionRateSeconds = metabolism.hungerDepletionRate;
        var activityMultiplier = data.getCurrentActivityMultiplier();

        // Calculate adjusted depletion rate
        var adjustedRateSeconds = depletionRateSeconds / activityMultiplier;
        var depletionIntervalMs = (long) (adjustedRateSeconds * 1000);

        // Check if enough time has passed
        if (currentTime - data.getLastHungerDepletion() >= depletionIntervalMs) {
            var currentHunger = data.getHunger();
            var newHunger = Math.max(currentHunger - 1.0, HungerStat.MIN_VALUE);

            data.setHunger(newHunger);
            data.setLastHungerDepletion(currentTime);
            data.addHungerDepleted(1.0);
        }
    }

    /**
     * Depletes thirst based on time and activity.
     */
    private void depleteThirst(PlayerMetabolismData data, long currentTime) {
        var metabolism = config.metabolism;
        var depletionRateSeconds = metabolism.thirstDepletionRate;
        var activityMultiplier = data.getCurrentActivityMultiplier();

        // Calculate adjusted depletion rate
        var adjustedRateSeconds = depletionRateSeconds / activityMultiplier;
        var depletionIntervalMs = (long) (adjustedRateSeconds * 1000);

        // Check if enough time has passed
        if (currentTime - data.getLastThirstDepletion() >= depletionIntervalMs) {
            var currentThirst = data.getThirst();
            var newThirst = Math.max(currentThirst - 1.0, ThirstStat.MIN_VALUE);

            data.setThirst(newThirst);
            data.setLastThirstDepletion(currentTime);
            data.addThirstDepleted(1.0);
        }
    }

    /**
     * Depletes energy based on time and activity.
     */
    private void depleteEnergy(PlayerMetabolismData data, long currentTime) {
        var metabolism = config.metabolism;
        var depletionRateSeconds = metabolism.energyDepletionRate;
        var activityMultiplier = data.getCurrentActivityMultiplier();

        // Calculate adjusted depletion rate
        var adjustedRateSeconds = depletionRateSeconds / activityMultiplier;
        var depletionIntervalMs = (long) (adjustedRateSeconds * 1000);

        // Check if enough time has passed
        if (currentTime - data.getLastEnergyDepletion() >= depletionIntervalMs) {
            var currentEnergy = data.getEnergy();
            var newEnergy = Math.max(currentEnergy - 1.0, EnergyStat.MIN_VALUE);

            data.setEnergy(newEnergy);
            data.setLastEnergyDepletion(currentTime);
            data.addEnergyDepleted(1.0);
        }
    }

    /**
     * Initializes metabolism for a player.
     * Loads existing data from persistence if available.
     * Should be called when player connects.
     *
     * @param playerUuid The player's UUID
     */
    public void initializePlayer(UUID playerUuid) {
        var metabolism = config.metabolism;

        // Load from persistence or use defaults
        var data = persistence.load(
            playerUuid,
            metabolism.initialHunger,
            metabolism.initialThirst,
            metabolism.initialEnergy
        );

        playerData.put(playerUuid, data);
    }

    /**
     * Removes a player from the metabolism system.
     * Saves data to persistence before removing.
     * Should be called when player disconnects.
     *
     * @param playerUuid The player's UUID
     */
    public void removePlayer(UUID playerUuid) {
        // Save to persistence before removing
        var data = playerData.get(playerUuid);
        if (data != null) {
            persistence.save(data);
        }

        playerData.remove(playerUuid);
        debuffsSystem.removePlayer(playerUuid);
        poisonEffectsSystem.removePlayer(playerUuid);
        debuffEffectsSystem.removePlayer(playerUuid);
        foodConsumptionProcessor.removePlayer(playerUuid);
    }

    /**
     * Saves all player data to persistence.
     * Should be called during server shutdown.
     */
    public void saveAll() {
        logger.at(java.util.logging.Level.INFO).log("Saving all player metabolism data...");
        int saved = 0;
        for (var data : playerData.values()) {
            if (persistence.save(data)) {
                saved++;
            }
        }
        logger.at(java.util.logging.Level.INFO).log("Saved %d player metabolism records", saved);
    }

    /**
     * Gets the metabolism data for a player.
     *
     * @param playerUuid The player's UUID
     * @return Optional containing the player's metabolism data, or empty if not found
     */
    public Optional<PlayerMetabolismData> getPlayerData(UUID playerUuid) {
        return Optional.ofNullable(playerData.get(playerUuid));
    }

    /**
     * Sets hunger for a player (admin command support).
     *
     * @param playerUuid The player's UUID
     * @param value The new hunger value
     * @return true if player exists and value was set, false otherwise
     */
    public boolean setHunger(UUID playerUuid, double value) {
        var data = playerData.get(playerUuid);
        if (data != null) {
            data.setHunger(value);
            return true;
        }
        return false;
    }

    /**
     * Sets thirst for a player (admin command support).
     *
     * @param playerUuid The player's UUID
     * @param value The new thirst value
     * @return true if player exists and value was set, false otherwise
     */
    public boolean setThirst(UUID playerUuid, double value) {
        var data = playerData.get(playerUuid);
        if (data != null) {
            data.setThirst(value);
            return true;
        }
        return false;
    }

    /**
     * Sets energy for a player (admin command support).
     *
     * @param playerUuid The player's UUID
     * @param value The new energy value
     * @return true if player exists and value was set, false otherwise
     */
    public boolean setEnergy(UUID playerUuid, double value) {
        var data = playerData.get(playerUuid);
        if (data != null) {
            data.setEnergy(value);
            return true;
        }
        return false;
    }

    /**
     * Restores hunger for a player (from consuming food).
     *
     * @param playerUuid The player's UUID
     * @param amount Amount of hunger to restore
     * @return The actual amount restored, or 0 if player not found
     */
    public double restoreHunger(UUID playerUuid, double amount) {
        var data = playerData.get(playerUuid);
        if (data != null) {
            return data.restoreHunger(amount);
        }
        return 0;
    }

    /**
     * Restores thirst for a player (from consuming drinks).
     *
     * @param playerUuid The player's UUID
     * @param amount Amount of thirst to restore
     * @return The actual amount restored, or 0 if player not found
     */
    public double restoreThirst(UUID playerUuid, double amount) {
        var data = playerData.get(playerUuid);
        if (data != null) {
            return data.restoreThirst(amount);
        }
        return 0;
    }

    /**
     * Restores energy for a player (from sleeping/resting).
     *
     * @param playerUuid The player's UUID
     * @param amount Amount of energy to restore
     * @return The actual amount restored, or 0 if player not found
     */
    public double restoreEnergy(UUID playerUuid, double amount) {
        var data = playerData.get(playerUuid);
        if (data != null) {
            return data.restoreEnergy(amount);
        }
        return 0;
    }

    /**
     * Marks a player as in combat (called when taking/dealing damage).
     *
     * @param playerUuid The player's UUID
     */
    public void enterCombat(UUID playerUuid) {
        var data = playerData.get(playerUuid);
        if (data != null) {
            data.enterCombat(System.currentTimeMillis());
        }
    }

    /**
     * Checks if system is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the number of tracked players.
     */
    public int getTrackedPlayerCount() {
        return playerData.size();
    }

    /**
     * Gets the UUIDs of all tracked players.
     * Used by poison effects system to check for native poison debuffs.
     */
    public java.util.Set<UUID> getTrackedPlayerIds() {
        return playerData.keySet();
    }

    /**
     * Gets the poison effects system.
     * Used by ItemConsumptionListener to apply poison effects.
     */
    public PoisonEffectsSystem getPoisonEffectsSystem() {
        return poisonEffectsSystem;
    }

    /**
     * Gets the native debuff effects system.
     * Handles metabolism drain from all Hytale debuffs (poison, burn, stun, freeze, root, slow).
     */
    public DebuffEffectsSystem getDebuffEffectsSystem() {
        return debuffEffectsSystem;
    }
}
