package com.livinglands.modules.leveling;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.modules.leveling.profession.XpCalculator;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles persistence of player leveling data to/from JSON files.
 * Data is stored in LivingLands/leveling/playerdata/{uuid}.json
 */
public class LevelingDataPersistence {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path dataDirectory;
    private final HytaleLogger logger;
    private final XpCalculator xpCalculator;

    /**
     * Creates a new leveling data persistence handler.
     *
     * @param moduleDirectory The leveling module's data directory
     * @param logger Logger for status messages
     * @param xpCalculator XP calculator for default values
     */
    public LevelingDataPersistence(@Nonnull Path moduleDirectory,
                                    @Nonnull HytaleLogger logger,
                                    @Nonnull XpCalculator xpCalculator) {
        this.dataDirectory = moduleDirectory.resolve("playerdata");
        this.logger = logger;
        this.xpCalculator = xpCalculator;

        // Ensure data directory exists
        try {
            Files.createDirectories(dataDirectory);
            logger.at(Level.INFO).log("Leveling player data directory: %s", dataDirectory);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to create leveling player data directory");
        }
    }

    /**
     * Saves player leveling data to file.
     *
     * @param data The leveling data to save
     * @return true if save was successful
     */
    public boolean save(@Nonnull PlayerLevelingData data) {
        var playerId = data.getPlayerId();
        var filePath = getPlayerFilePath(playerId);

        try {
            data.setLastSaveTime(System.currentTimeMillis());
            var json = GSON.toJson(data);
            Files.writeString(filePath, json);
            data.clearDirty();
            logger.at(Level.FINE).log("Saved leveling data for player: %s", playerId);
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
     * @return PlayerLevelingData with loaded or default values
     */
    public PlayerLevelingData load(@Nonnull UUID playerId) {
        var filePath = getPlayerFilePath(playerId);

        if (!Files.exists(filePath)) {
            logger.at(Level.FINE).log("No leveling data for player %s, creating new", playerId);
            return PlayerLevelingData.createNew(playerId, xpCalculator);
        }

        try {
            var json = Files.readString(filePath);
            var data = GSON.fromJson(json, PlayerLevelingData.class);

            // Ensure player ID is set (may be null after deserialization)
            data.setPlayerId(playerId);

            // Validate and repair any missing data
            data.validateAndRepair(xpCalculator);

            logger.at(Level.INFO).log("Loaded leveling data for player: %s (%s)",
                playerId, data);
            return data;
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Failed to load leveling data for %s, creating new", playerId
            );
            return PlayerLevelingData.createNew(playerId, xpCalculator);
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

    /**
     * Get the file path for a player's data file.
     */
    private Path getPlayerFilePath(UUID playerId) {
        return dataDirectory.resolve(playerId.toString() + ".json");
    }

    /**
     * Get the data directory path.
     */
    public Path getDataDirectory() {
        return dataDirectory;
    }
}
