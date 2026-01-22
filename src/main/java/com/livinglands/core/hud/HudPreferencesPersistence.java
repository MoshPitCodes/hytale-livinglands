package com.livinglands.core.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Handles persistence of player HUD preferences to/from JSON files.
 * Each player's preferences are stored in: playerdata/hud/{uuid}.json
 */
public class HudPreferencesPersistence {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path dataDirectory;
    private final HytaleLogger logger;

    // In-memory cache of loaded preferences
    private final Map<UUID, HudPreferences> cache = new ConcurrentHashMap<>();

    /**
     * Creates a new HUD preferences persistence handler.
     *
     * @param pluginDirectory The plugin's data directory
     * @param logger Logger for status messages
     */
    public HudPreferencesPersistence(@Nonnull Path pluginDirectory, @Nonnull HytaleLogger logger) {
        this.dataDirectory = pluginDirectory.resolve("playerdata").resolve("hud");
        this.logger = logger;

        // Ensure data directory exists
        try {
            Files.createDirectories(dataDirectory);
            logger.at(Level.FINE).log("[HUD] Preferences directory: %s", dataDirectory);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("[HUD] Failed to create preferences directory");
        }
    }

    /**
     * Gets preferences for a player, loading from disk if not cached.
     *
     * @param playerId The player's UUID
     * @return HudPreferences for the player (never null)
     */
    @Nonnull
    public HudPreferences getPreferences(@Nonnull UUID playerId) {
        return cache.computeIfAbsent(playerId, this::loadFromDisk);
    }

    /**
     * Saves preferences for a player to disk.
     *
     * @param playerId The player's UUID
     * @param preferences The preferences to save
     * @return true if save was successful
     */
    public boolean save(@Nonnull UUID playerId, @Nonnull HudPreferences preferences) {
        // Update cache
        cache.put(playerId, preferences);

        // Save to disk
        var filePath = getPlayerFilePath(playerId);
        try {
            var json = GSON.toJson(preferences);
            Files.writeString(filePath, json);
            logger.at(Level.FINE).log("[HUD] Saved preferences for player: %s", playerId);
            return true;
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("[HUD] Failed to save preferences for player: %s", playerId);
            return false;
        }
    }

    /**
     * Saves the current cached preferences for a player.
     *
     * @param playerId The player's UUID
     * @return true if save was successful
     */
    public boolean save(@Nonnull UUID playerId) {
        var prefs = cache.get(playerId);
        if (prefs == null) {
            return true; // Nothing to save
        }
        return save(playerId, prefs);
    }

    /**
     * Loads preferences for a player and caches them.
     * Call this when a player joins.
     *
     * @param playerId The player's UUID
     * @return The loaded preferences
     */
    @Nonnull
    public HudPreferences load(@Nonnull UUID playerId) {
        var prefs = loadFromDisk(playerId);
        cache.put(playerId, prefs);
        return prefs;
    }

    /**
     * Removes a player from the cache.
     * Call this when a player leaves.
     *
     * @param playerId The player's UUID
     */
    public void unload(@Nonnull UUID playerId) {
        // Save before unloading
        save(playerId);
        cache.remove(playerId);
    }

    /**
     * Saves all cached preferences to disk.
     * Call this on server shutdown.
     */
    public void saveAll() {
        for (var entry : cache.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
        logger.at(Level.FINE).log("[HUD] Saved %d player preferences", cache.size());
    }

    /**
     * Loads preferences from disk.
     */
    private HudPreferences loadFromDisk(UUID playerId) {
        var filePath = getPlayerFilePath(playerId);

        if (!Files.exists(filePath)) {
            logger.at(Level.FINE).log("[HUD] No saved preferences for player %s, using defaults", playerId);
            return HudPreferences.defaults();
        }

        try {
            var json = Files.readString(filePath);
            var prefs = GSON.fromJson(json, HudPreferences.class);
            if (prefs == null) {
                return HudPreferences.defaults();
            }
            logger.at(Level.FINE).log("[HUD] Loaded preferences for player: %s", playerId);
            return prefs;
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "[HUD] Failed to load preferences for player %s, using defaults", playerId
            );
            return HudPreferences.defaults();
        }
    }

    private Path getPlayerFilePath(UUID playerId) {
        return dataDirectory.resolve(playerId.toString() + ".json");
    }
}
