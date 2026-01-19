package com.livinglands.core.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.modules.metabolism.PlayerMetabolismData;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles persistence of player metabolism data to/from JSON files.
 * Each player's data is stored in a separate file: playerdata/{uuid}.json
 */
public class PlayerDataPersistence {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path dataDirectory;
    private final HytaleLogger logger;

    /**
     * Creates a new player data persistence handler.
     *
     * @param pluginDirectory The plugin's data directory
     * @param logger Logger for status messages
     */
    public PlayerDataPersistence(@Nonnull Path pluginDirectory, @Nonnull HytaleLogger logger) {
        this.dataDirectory = pluginDirectory.resolve("playerdata");
        this.logger = logger;

        // Ensure data directory exists
        try {
            Files.createDirectories(dataDirectory);
            logger.at(Level.INFO).log("Player data directory: %s", dataDirectory);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to create player data directory");
        }
    }

    /**
     * Saves player metabolism data to file.
     *
     * @param data The metabolism data to save
     * @return true if save was successful
     */
    public boolean save(@Nonnull PlayerMetabolismData data) {
        var playerId = data.getPlayerUuid();
        var filePath = getPlayerFilePath(playerId);

        try {
            var jsonData = new PlayerDataJson(data);
            var json = GSON.toJson(jsonData);
            Files.writeString(filePath, json);
            logger.at(Level.FINE).log("Saved player data: %s", playerId);
            return true;
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to save player data: %s", playerId);
            return false;
        }
    }

    /**
     * Loads player metabolism data from file.
     *
     * @param playerId The player's UUID
     * @param defaultHunger Default hunger if no data exists
     * @param defaultThirst Default thirst if no data exists
     * @param defaultEnergy Default energy if no data exists
     * @return PlayerMetabolismData with loaded or default values
     */
    public PlayerMetabolismData load(@Nonnull UUID playerId,
                                      double defaultHunger,
                                      double defaultThirst,
                                      double defaultEnergy) {
        var filePath = getPlayerFilePath(playerId);

        if (!Files.exists(filePath)) {
            logger.at(Level.FINE).log("No saved data for player %s, using defaults", playerId);
            return new PlayerMetabolismData(playerId, defaultHunger, defaultThirst, defaultEnergy);
        }

        try {
            var json = Files.readString(filePath);
            var jsonData = GSON.fromJson(json, PlayerDataJson.class);
            var data = jsonData.toPlayerMetabolismData(playerId);
            logger.at(Level.INFO).log("Loaded player data: %s (hunger=%.0f, thirst=%.0f, energy=%.0f)",
                playerId, data.getHunger(), data.getThirst(), data.getEnergy());
            return data;
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Failed to load player data for %s, using defaults", playerId
            );
            return new PlayerMetabolismData(playerId, defaultHunger, defaultThirst, defaultEnergy);
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
            logger.at(Level.WARNING).withCause(e).log("Failed to delete player data: %s", playerId);
            return false;
        }
    }

    private Path getPlayerFilePath(UUID playerId) {
        return dataDirectory.resolve(playerId.toString() + ".json");
    }

    /**
     * JSON representation of player metabolism data.
     */
    private static class PlayerDataJson {
        public double hunger;
        public double thirst;
        public double energy;
        public long lastSaved;

        // Stats tracking
        public double totalHungerDepleted;
        public double totalThirstDepleted;
        public double totalEnergyDepleted;
        public double totalHungerRestored;
        public double totalThirstRestored;
        public double totalEnergyRestored;

        public PlayerDataJson() {}

        public PlayerDataJson(PlayerMetabolismData data) {
            this.hunger = data.getHunger();
            this.thirst = data.getThirst();
            this.energy = data.getEnergy();
            this.lastSaved = System.currentTimeMillis();
            this.totalHungerDepleted = data.getTotalHungerDepleted();
            this.totalThirstDepleted = data.getTotalThirstDepleted();
            this.totalEnergyDepleted = data.getTotalEnergyDepleted();
            this.totalHungerRestored = data.getTotalHungerRestored();
            this.totalThirstRestored = data.getTotalThirstRestored();
            this.totalEnergyRestored = data.getTotalEnergyRestored();
        }

        public PlayerMetabolismData toPlayerMetabolismData(UUID playerId) {
            return new PlayerMetabolismData(playerId, hunger, thirst, energy);
            // Note: Stats tracking is not restored to avoid exploits
        }
    }
}
