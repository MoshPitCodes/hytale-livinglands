package com.livinglands.leveling;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.config.LevelingConfig;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Main coordinator for the Leveling module.
 *
 * Responsibilities:
 * - Module lifecycle (enable/disable)
 * - Coordinating LevelingSystem
 * - Graceful handling of disabled state
 *
 * Module Pattern:
 * - isEnabled() - Check if module is active
 * - start() - Initialize and start module
 * - stop() - Cleanup and shutdown
 *
 * This module can be toggled on/off via config without affecting other systems.
 */
public class LevelingModule {

    private final LevelingConfig config;
    private final HytaleLogger logger;
    private final PlayerRegistry playerRegistry;
    private final Path pluginDirectory;

    // Core system (null if disabled)
    private LevelingSystem levelingSystem;

    // Module state
    private boolean running = false;

    /**
     * Creates a new leveling module.
     *
     * @param config The leveling configuration
     * @param logger The logger for debug output
     * @param playerRegistry The central player registry
     * @param pluginDirectory The plugin's data directory
     */
    public LevelingModule(@Nonnull LevelingConfig config,
                          @Nonnull HytaleLogger logger,
                          @Nonnull PlayerRegistry playerRegistry,
                          @Nonnull Path pluginDirectory) {
        this.config = config;
        this.logger = logger;
        this.playerRegistry = playerRegistry;
        this.pluginDirectory = pluginDirectory;
    }

    /**
     * Checks if the leveling module is enabled.
     *
     * @return true if module is enabled in config
     */
    public boolean isEnabled() {
        return config.enabled();
    }

    /**
     * Starts the leveling module.
     * Only starts if enabled in configuration.
     */
    public void start() {
        if (!config.enabled()) {
            logger.at(Level.INFO).log("Leveling module disabled in configuration");
            return;
        }

        if (running) {
            logger.at(Level.WARNING).log("Leveling module already running");
            return;
        }

        try {
            logger.at(Level.INFO).log("Starting leveling module...");

            // Initialize leveling system
            levelingSystem = new LevelingSystem(config, logger, playerRegistry, pluginDirectory);

            running = true;

            logger.at(Level.INFO).log("Leveling module started successfully");
            logger.at(Level.INFO).log("  - Max Level: %d", config.maxLevel());
            logger.at(Level.INFO).log("  - Base XP: %.0f", config.baseXpPerLevel());
            logger.at(Level.INFO).log("  - Health per Level: +%.0f", config.healthPerLevel());
            logger.at(Level.INFO).log("  - Stamina per Level: +%.0f", config.staminaPerLevel());

        } catch (Exception e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to start leveling module");
            throw e;
        }
    }

    /**
     * Stops the leveling module.
     * Saves all player data before shutdown.
     */
    public void stop() {
        if (!running) {
            return;
        }

        try {
            logger.at(Level.INFO).log("Stopping leveling module...");

            // Save all player data
            if (levelingSystem != null) {
                levelingSystem.saveAll();
            }

            running = false;
            logger.at(Level.INFO).log("Leveling module stopped");

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error stopping leveling module");
        }
    }

    /**
     * Initializes leveling for a player (on connect).
     * No-op if module is disabled.
     *
     * @param playerUuid The player's UUID
     */
    public void initializePlayer(@Nonnull UUID playerUuid) {
        if (!isEnabled() || levelingSystem == null) {
            return;
        }

        levelingSystem.initializePlayer(playerUuid);
    }

    /**
     * Applies stat modifiers for a player (after ECS ready).
     * No-op if module is disabled.
     *
     * @param playerUuid The player's UUID
     */
    public void applyStatModifiers(@Nonnull UUID playerUuid) {
        if (!isEnabled() || levelingSystem == null) {
            return;
        }

        levelingSystem.applyStatModifiers(playerUuid);
    }

    /**
     * Removes a player from the leveling system (on disconnect).
     * No-op if module is disabled.
     *
     * @param playerUuid The player's UUID
     */
    public void removePlayer(@Nonnull UUID playerUuid) {
        if (!isEnabled() || levelingSystem == null) {
            return;
        }

        levelingSystem.removePlayer(playerUuid);
    }

    /**
     * Grants experience to a player.
     * No-op if module is disabled.
     *
     * @param playerUuid The player's UUID
     * @param amount Amount of XP to grant
     * @return true if player leveled up, false otherwise
     */
    public boolean grantExperience(@Nonnull UUID playerUuid, double amount) {
        if (!isEnabled() || levelingSystem == null) {
            return false;
        }

        return levelingSystem.grantExperience(playerUuid, amount);
    }

    /**
     * Sets a player's level directly (admin command support).
     * No-op if module is disabled.
     *
     * @param playerUuid The player's UUID
     * @param newLevel The new level
     * @return true if successful, false otherwise
     */
    public boolean setLevel(@Nonnull UUID playerUuid, int newLevel) {
        if (!isEnabled() || levelingSystem == null) {
            return false;
        }

        return levelingSystem.setLevel(playerUuid, newLevel);
    }

    /**
     * Gets the leveling system.
     * Returns null if module is disabled.
     *
     * @return The leveling system, or null if disabled
     */
    public LevelingSystem getLevelingSystem() {
        return levelingSystem;
    }

    /**
     * Checks if the module is running.
     *
     * @return true if module is running
     */
    public boolean isRunning() {
        return running;
    }
}
