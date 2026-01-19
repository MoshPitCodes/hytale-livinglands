package com.livinglands.modules.leveling.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Handles persistence of player-placed block positions to/from JSON files.
 * Data is stored per-world in LivingLands/leveling/placedblocks/{worldId}.json
 *
 * Storage format is a simple set of position keys: "x:y:z"
 * This keeps files compact while allowing efficient lookup.
 */
public final class PlacedBlockPersistence {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private static final Type SET_TYPE = new TypeToken<HashSet<String>>() {}.getType();

    private final Path dataDirectory;
    private final HytaleLogger logger;

    // Track which worlds have been modified and need saving
    private final Set<String> dirtyWorlds = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new placed block persistence handler.
     *
     * @param moduleDirectory The leveling module's data directory
     * @param logger Logger for status messages
     */
    public PlacedBlockPersistence(@Nonnull Path moduleDirectory, @Nonnull HytaleLogger logger) {
        this.dataDirectory = moduleDirectory.resolve("placedblocks");
        this.logger = logger;

        // Ensure data directory exists
        try {
            Files.createDirectories(dataDirectory);
            logger.at(Level.INFO).log("Placed blocks data directory: %s", dataDirectory);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to create placed blocks data directory");
        }
    }

    /**
     * Loads placed block positions for a specific world.
     *
     * @param worldId The world identifier
     * @return Set of position keys (format: "x:y:z")
     */
    @Nonnull
    public Set<String> loadWorld(@Nonnull String worldId) {
        var filePath = getWorldFilePath(worldId);

        if (!Files.exists(filePath)) {
            logger.at(Level.FINE).log("No placed blocks data for world %s", worldId);
            return new HashSet<>();
        }

        try {
            var json = Files.readString(filePath);
            Set<String> positions = GSON.fromJson(json, SET_TYPE);
            if (positions == null) {
                positions = new HashSet<>();
            }
            logger.at(Level.INFO).log("Loaded %d placed block positions for world: %s",
                positions.size(), worldId);
            return positions;
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Failed to load placed blocks data for world %s", worldId
            );
            return new HashSet<>();
        }
    }

    /**
     * Saves placed block positions for a specific world.
     *
     * @param worldId The world identifier
     * @param positions Set of position keys (format: "x:y:z")
     * @return true if save was successful
     */
    public boolean saveWorld(@Nonnull String worldId, @Nonnull Set<String> positions) {
        var filePath = getWorldFilePath(worldId);

        try {
            // If no positions, delete the file to save space
            if (positions.isEmpty()) {
                Files.deleteIfExists(filePath);
                logger.at(Level.FINE).log("Removed empty placed blocks file for world: %s", worldId);
                return true;
            }

            var json = GSON.toJson(positions);
            Files.writeString(filePath, json);
            dirtyWorlds.remove(worldId);
            logger.at(Level.FINE).log("Saved %d placed block positions for world: %s",
                positions.size(), worldId);
            return true;
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Failed to save placed blocks data for world: %s", worldId
            );
            return false;
        }
    }

    /**
     * Saves all dirty worlds from the provided data map.
     *
     * @param worldData Map of worldId -> set of position keys
     */
    public void saveAllDirty(@Nonnull Map<String, Set<String>> worldData) {
        for (String worldId : dirtyWorlds) {
            var positions = worldData.get(worldId);
            if (positions != null) {
                saveWorld(worldId, positions);
            }
        }
    }

    /**
     * Saves all worlds from the provided data map.
     *
     * @param worldData Map of worldId -> set of position keys
     */
    public void saveAll(@Nonnull Map<String, Set<String>> worldData) {
        int totalPositions = 0;
        for (var entry : worldData.entrySet()) {
            saveWorld(entry.getKey(), entry.getValue());
            totalPositions += entry.getValue().size();
        }
        logger.at(Level.INFO).log("Saved placed blocks data: %d positions across %d worlds",
            totalPositions, worldData.size());
    }

    /**
     * Loads all world data from disk.
     *
     * @return Map of worldId -> set of position keys
     */
    @Nonnull
    public Map<String, Set<String>> loadAll() {
        Map<String, Set<String>> worldData = new ConcurrentHashMap<>();

        try {
            if (!Files.exists(dataDirectory)) {
                return worldData;
            }

            try (var stream = Files.list(dataDirectory)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        String worldId = filename.substring(0, filename.length() - 5); // Remove .json
                        Set<String> positions = loadWorld(worldId);
                        if (!positions.isEmpty()) {
                            worldData.put(worldId, ConcurrentHashMap.newKeySet());
                            worldData.get(worldId).addAll(positions);
                        }
                    });
            }

            int totalPositions = worldData.values().stream().mapToInt(Set::size).sum();
            logger.at(Level.INFO).log("Loaded placed blocks data: %d positions across %d worlds",
                totalPositions, worldData.size());

        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to load placed blocks data");
        }

        return worldData;
    }

    /**
     * Mark a world as having modified data that needs saving.
     *
     * @param worldId The world identifier
     */
    public void markDirty(@Nonnull String worldId) {
        dirtyWorlds.add(worldId);
    }

    /**
     * Check if any worlds have unsaved changes.
     *
     * @return true if there are dirty worlds
     */
    public boolean hasDirtyData() {
        return !dirtyWorlds.isEmpty();
    }

    /**
     * Get the file path for a world's data file.
     */
    private Path getWorldFilePath(String worldId) {
        // Sanitize world ID for use as filename
        String safeWorldId = worldId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return dataDirectory.resolve(safeWorldId + ".json");
    }

    /**
     * Get the data directory path.
     */
    public Path getDataDirectory() {
        return dataDirectory;
    }
}
