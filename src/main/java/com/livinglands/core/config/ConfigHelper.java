package com.livinglands.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Generic JSON configuration utilities.
 *
 * Provides common functionality for loading and saving JSON config files
 * used by modules and core systems.
 */
public final class ConfigHelper {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private ConfigHelper() {
        // Utility class
    }

    /**
     * Gets the shared Gson instance configured for config files.
     */
    @Nonnull
    public static Gson getGson() {
        return GSON;
    }

    /**
     * Loads a JSON config file, creating with defaults if not exists.
     *
     * @param configPath      Path to the config file
     * @param type            Class to deserialize to
     * @param defaultSupplier Supplier for default config if file doesn't exist
     * @param logger          Logger for status messages (can be null)
     * @param <T>             Config type
     * @return Loaded or default configuration
     */
    @Nonnull
    public static <T> T loadOrCreate(@Nonnull Path configPath,
                                      @Nonnull Class<T> type,
                                      @Nonnull Supplier<T> defaultSupplier,
                                      @Nullable HytaleLogger logger) {
        try {
            if (Files.exists(configPath)) {
                return load(configPath, type, logger);
            } else {
                if (logger != null) {
                    logger.at(Level.INFO).log("Config not found, creating default: %s", configPath);
                }
                var defaultConfig = defaultSupplier.get();
                save(configPath, defaultConfig, logger);
                return defaultConfig;
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(e).log(
                        "Failed to load config %s, using defaults", configPath);
            }
            return defaultSupplier.get();
        }
    }

    /**
     * Loads a JSON config file.
     *
     * @param configPath Path to the config file
     * @param type       Class to deserialize to
     * @param logger     Logger for status messages (can be null)
     * @param <T>        Config type
     * @return Loaded configuration
     * @throws IOException if file cannot be read
     */
    @Nonnull
    public static <T> T load(@Nonnull Path configPath,
                              @Nonnull Class<T> type,
                              @Nullable HytaleLogger logger) throws IOException {
        if (logger != null) {
            logger.at(Level.FINE).log("Loading config from: %s", configPath);
        }
        var json = Files.readString(configPath);
        return GSON.fromJson(json, type);
    }

    /**
     * Saves a config object to a JSON file.
     *
     * @param configPath Path to save to
     * @param config     Config object to save
     * @param logger     Logger for status messages (can be null)
     * @param <T>        Config type
     */
    public static <T> void save(@Nonnull Path configPath,
                                 @Nonnull T config,
                                 @Nullable HytaleLogger logger) {
        try {
            Files.createDirectories(configPath.getParent());
            var json = GSON.toJson(config);
            Files.writeString(configPath, json);
            if (logger != null) {
                logger.at(Level.FINE).log("Saved config to: %s", configPath);
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(e).log(
                        "Failed to save config to %s", configPath);
            }
        }
    }

    /**
     * Checks if a config file exists.
     *
     * @param configPath Path to check
     * @return true if file exists
     */
    public static boolean exists(@Nonnull Path configPath) {
        return Files.exists(configPath);
    }

    /**
     * Creates parent directories for a config file if they don't exist.
     *
     * @param configPath Path to the config file
     * @throws IOException if directories cannot be created
     */
    public static void ensureDirectoryExists(@Nonnull Path configPath) throws IOException {
        Files.createDirectories(configPath.getParent());
    }
}
