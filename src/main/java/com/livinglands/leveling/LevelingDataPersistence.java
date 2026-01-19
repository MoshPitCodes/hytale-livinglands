package com.livinglands.leveling;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles persistence of player leveling data to/from JSON files.
 * Each player's data is stored in: playerdata/{uuid}_leveling.json
 *
 * Follows the same pattern as PlayerDataPersistence for consistency.
 */
public class LevelingDataPersistence {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path dataDirectory;
    private final HytaleLogger logger;

    /**
     * Creates a new leveling data persistence handler.
     *
     * @param pluginDirectory The plugin's data directory
     * @param logger Logger for status messages
     */
    public LevelingDataPersistence(@Nonnull Path pluginDirectory, @Nonnull HytaleLogger logger) {
        this.dataDirectory = pluginDirectory.resolve("playerdata");
        this.logger = logger;

        // Ensure data directory exists (shared with metabolism data)
        try {
            Files.createDirectories(dataDirectory);
            logger.at(Level.FINE).log("Leveling data directory: %s", dataDirectory);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to create leveling data directory");
        }
    }

    /**
     * Saves player leveling data to file.
     *
     * @param data The leveling data to save
     * @return true if save was successful
     */
    public boolean save(@Nonnull PlayerLevelingData data) {
        var playerId = data.playerId();
        var filePath = getPlayerFilePath(playerId);

        try {
            var jsonData = new LevelingDataJson(data);
            var json = GSON.toJson(jsonData);
            Files.writeString(filePath, json);
            logger.at(Level.FINE).log("Saved leveling data: %s (level=%d)", playerId, data.level());
            return true;
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to save leveling data: %s", playerId);
            return false;
        }
    }

    /**
     * Loads player leveling data from file.
     *
     * @param playerId The player's UUID
     * @return PlayerLevelingData with loaded values, or new level 1 data if no file exists
     */
    public PlayerLevelingData load(@Nonnull UUID playerId) {
        var filePath = getPlayerFilePath(playerId);

        if (!Files.exists(filePath)) {
            logger.at(Level.FINE).log("No saved leveling data for player %s, starting at level 1", playerId);
            return PlayerLevelingData.create(playerId);
        }

        try {
            var json = Files.readString(filePath);
            var jsonData = GSON.fromJson(json, LevelingDataJson.class);
            var data = jsonData.toPlayerLevelingData(playerId);
            logger.at(Level.INFO).log("Loaded leveling data: %s (level=%d, xp=%.0f, skillPoints=%d)",
                playerId, data.level(), data.experience(), data.skillPoints());
            return data;
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Failed to load leveling data for %s, starting at level 1", playerId
            );
            return PlayerLevelingData.create(playerId);
        }
    }

    /**
     * Checks if saved data exists for a player.
     *
     * @param playerId The player's UUID
     * @return true if saved data exists
     */
    public boolean hasData(@Nonnull UUID playerId) {
        return Files.exists(getPlayerFilePath(playerId));
    }

    /**
     * Deletes saved data for a player.
     *
     * @param playerId The player's UUID
     * @return true if deletion was successful or file didn't exist
     */
    public boolean delete(@Nonnull UUID playerId) {
        var filePath = getPlayerFilePath(playerId);
        try {
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to delete leveling data: %s", playerId);
            return false;
        }
    }

    private Path getPlayerFilePath(UUID playerId) {
        return dataDirectory.resolve(playerId.toString() + "_leveling.json");
    }

    /**
     * JSON representation of player leveling data.
     */
    private static class LevelingDataJson {
        public int level;
        public double experience;
        public int skillPoints;
        public double totalXpEarned;
        public long lastSaved;

        public LevelingDataJson() {}

        public LevelingDataJson(PlayerLevelingData data) {
            this.level = data.level();
            this.experience = data.experience();
            this.skillPoints = data.skillPoints();
            this.totalXpEarned = data.totalXpEarned();
            this.lastSaved = System.currentTimeMillis();
        }

        public PlayerLevelingData toPlayerLevelingData(UUID playerId) {
            return new PlayerLevelingData(
                playerId,
                level,
                experience,
                skillPoints,
                totalXpEarned
            );
        }
    }
}
