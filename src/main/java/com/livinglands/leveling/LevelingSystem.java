package com.livinglands.leveling;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.config.LevelingConfig;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Core system for managing player leveling.
 *
 * Responsibilities:
 * - Player leveling data management (thread-safe)
 * - Experience granting and level up handling
 * - Stat modifier application (Health/Stamina bonuses)
 * - Data persistence coordination
 *
 * Thread Safety:
 * - ConcurrentHashMap for player data
 * - World.execute() for all ECS/stat modifications
 */
public class LevelingSystem {

    // Modifier IDs for stat bonuses
    private static final String HEALTH_MODIFIER_ID = "leveling:health_bonus";
    private static final String STAMINA_MODIFIER_ID = "leveling:stamina_bonus";

    private final LevelingConfig config;
    private final HytaleLogger logger;
    private final PlayerRegistry playerRegistry;
    private final LevelingDataPersistence persistence;

    // Thread-safe player data storage
    private final Map<UUID, PlayerLevelingData> playerData;

    /**
     * Creates a new leveling system.
     *
     * @param config The leveling configuration
     * @param logger The logger for debug output
     * @param playerRegistry The central player registry
     * @param pluginDirectory The plugin's data directory for persistence
     */
    public LevelingSystem(@Nonnull LevelingConfig config,
                          @Nonnull HytaleLogger logger,
                          @Nonnull PlayerRegistry playerRegistry,
                          @Nonnull Path pluginDirectory) {
        this.config = config;
        this.logger = logger;
        this.playerRegistry = playerRegistry;
        this.persistence = new LevelingDataPersistence(pluginDirectory, logger);
        this.playerData = new ConcurrentHashMap<>();
    }

    /**
     * Initializes leveling data for a player.
     * Loads existing data from persistence if available.
     * Should be called when player connects.
     *
     * @param playerUuid The player's UUID
     */
    public void initializePlayer(@Nonnull UUID playerUuid) {
        // Load from persistence or create new data
        var data = persistence.load(playerUuid);
        playerData.put(playerUuid, data);

        logger.at(Level.INFO).log("Initialized leveling for player %s (level=%d, xp=%.0f)",
            playerUuid, data.level(), data.experience());
    }

    /**
     * Applies stat modifiers for a player's current level.
     * Must be called after ECS is ready (in PlayerReadyEvent).
     * Runs on WorldThread via world.execute().
     *
     * @param playerUuid The player's UUID
     */
    public void applyStatModifiers(@Nonnull UUID playerUuid) {
        var data = playerData.get(playerUuid);
        if (data == null) {
            logger.at(Level.WARNING).log("Cannot apply stat modifiers - no data for player %s", playerUuid);
            return;
        }

        var sessionOpt = playerRegistry.getSession(playerUuid);
        if (sessionOpt.isEmpty()) {
            logger.at(Level.WARNING).log("Cannot apply stat modifiers - no session for player %s", playerUuid);
            return;
        }

        var session = sessionOpt.get();
        if (!session.isEcsReady()) {
            logger.at(Level.WARNING).log("Cannot apply stat modifiers - ECS not ready for player %s", playerUuid);
            return;
        }

        var world = session.getWorld();
        var ref = session.getEntityRef();
        var store = session.getStore();

        if (world == null || ref == null || store == null) {
            logger.at(Level.WARNING).log("Cannot apply stat modifiers - missing ECS references for player %s", playerUuid);
            return;
        }

        // Calculate stat bonuses
        var level = data.level();
        var healthBonus = (level - 1) * config.healthPerLevel(); // No bonus at level 1
        var staminaBonus = (level - 1) * config.staminaPerLevel();

        // Apply modifiers on WorldThread
        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap == null) {
                    logger.at(Level.WARNING).log("Cannot apply stat modifiers - EntityStatMap not found for player %s", playerUuid);
                    return;
                }

                // TODO: Apply Health and Stamina modifiers using the Hytale stat modifier API
                // The stat modifier API is still evolving in the Hytale Server API.
                // For now, we prepare the infrastructure and log the bonuses.
                // Future implementation will use statMap.putModifier() with proper modifier types.
                //
                // Planned implementation:
                // if (healthBonus > 0) {
                //     statMap.putModifier(
                //         DefaultEntityStatTypes.getHealth(),
                //         HEALTH_MODIFIER_ID,
                //         new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, healthBonus)
                //     );
                // }
                // if (staminaBonus > 0) {
                //     statMap.putModifier(
                //         DefaultEntityStatTypes.getStamina(),
                //         STAMINA_MODIFIER_ID,
                //         new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, staminaBonus)
                //     );
                // }

                logger.at(Level.INFO).log("Prepared stat modifiers for player %s (level %d): +%.0f health, +%.0f stamina",
                    playerUuid, level, healthBonus, staminaBonus);
                logger.at(Level.FINE).log("Note: Stat modifier application pending Hytale API finalization");

            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Failed to prepare stat modifiers for player %s", playerUuid);
            }
        });
    }

    /**
     * Grants experience to a player.
     * Handles level ups automatically and applies stat modifiers.
     *
     * @param playerUuid The player's UUID
     * @param amount Amount of XP to grant
     * @return true if player leveled up, false otherwise
     */
    public boolean grantExperience(@Nonnull UUID playerUuid, double amount) {
        var data = playerData.get(playerUuid);
        if (data == null) {
            logger.at(Level.WARNING).log("Cannot grant XP - no data for player %s", playerUuid);
            return false;
        }

        var result = data.addExperience(
            amount,
            config.baseXpPerLevel(),
            config.xpScalingFactor(),
            config.maxLevel(),
            config.skillPointsPerLevel()
        );

        // Update stored data
        playerData.put(playerUuid, result.newData());

        if (result.leveledUp()) {
            handleLevelUp(playerUuid, result.newData(), result.levelsGained());
        }

        return result.leveledUp();
    }

    /**
     * Handles level up event.
     * Re-applies stat modifiers with new bonuses.
     */
    private void handleLevelUp(@Nonnull UUID playerUuid, @Nonnull PlayerLevelingData newData, int levelsGained) {
        logger.at(Level.INFO).log("Player %s leveled up! (%d -> %d, gained %d levels)",
            playerUuid, newData.level() - levelsGained, newData.level(), levelsGained);

        // Re-apply stat modifiers with new bonuses
        applyStatModifiers(playerUuid);

        // TODO: Broadcast level up message to player
        // TODO: Play level up sound/effects
    }

    /**
     * Sets a player's level directly (admin command support).
     *
     * @param playerUuid The player's UUID
     * @param newLevel The new level
     * @return true if successful, false otherwise
     */
    public boolean setLevel(@Nonnull UUID playerUuid, int newLevel) {
        var data = playerData.get(playerUuid);
        if (data == null) {
            return false;
        }

        var newData = data.setLevel(newLevel, config.maxLevel(), config.skillPointsPerLevel());
        playerData.put(playerUuid, newData);

        // Re-apply stat modifiers
        applyStatModifiers(playerUuid);

        return true;
    }

    /**
     * Removes a player from the leveling system.
     * Saves data to persistence before removing.
     * Should be called when player disconnects.
     *
     * @param playerUuid The player's UUID
     */
    public void removePlayer(@Nonnull UUID playerUuid) {
        var data = playerData.remove(playerUuid);
        if (data != null) {
            persistence.save(data);
        }
    }

    /**
     * Saves all player data to persistence.
     * Should be called during server shutdown.
     */
    public void saveAll() {
        logger.at(Level.INFO).log("Saving all player leveling data...");
        var saved = 0;
        for (var data : playerData.values()) {
            if (persistence.save(data)) {
                saved++;
            }
        }
        logger.at(Level.INFO).log("Saved %d player leveling records", saved);
    }

    /**
     * Gets the leveling data for a player.
     *
     * @param playerUuid The player's UUID
     * @return Optional containing the player's leveling data, or empty if not found
     */
    public Optional<PlayerLevelingData> getPlayerData(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(playerData.get(playerUuid));
    }

    /**
     * Gets the number of tracked players.
     */
    public int getTrackedPlayerCount() {
        return playerData.size();
    }
}
