package com.livinglands.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Handles loading and saving of the modules.json configuration file.
 */
public final class ModulesConfigLoader {

    private static final String MODULES_FILE = "modules.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private ModulesConfigLoader() {
        // Utility class
    }

    /**
     * Loads modules configuration from the plugin directory.
     * Creates default if file doesn't exist.
     *
     * @param pluginDirectory Plugin data directory (LivingLands/)
     * @param logger          Logger for status messages
     * @return Loaded or default modules configuration
     */
    @Nonnull
    public static ModulesConfig loadOrCreate(@Nonnull Path pluginDirectory,
                                              @Nonnull HytaleLogger logger) {
        var configPath = pluginDirectory.resolve(MODULES_FILE);

        try {
            if (Files.exists(configPath)) {
                return load(configPath, logger);
            } else {
                logger.at(Level.FINE).log("Modules config not found, creating default: %s", configPath);
                var defaultConfig = ModulesConfig.defaults();
                save(pluginDirectory, defaultConfig, logger);
                return defaultConfig;
            }
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                    "Failed to load modules config, using defaults");
            return ModulesConfig.defaults();
        }
    }

    /**
     * Loads modules configuration from an existing file.
     */
    @Nonnull
    public static ModulesConfig load(@Nonnull Path configPath,
                                      @Nonnull HytaleLogger logger) throws IOException {
        logger.at(Level.FINE).log("Loading modules config from: %s", configPath);
        var json = Files.readString(configPath);
        var config = GSON.fromJson(json, ModulesConfig.class);

        // Ensure defaults are present for any new modules
        var defaults = ModulesConfig.defaults();
        for (var entry : defaults.enabled.entrySet()) {
            if (!config.enabled.containsKey(entry.getKey())) {
                config.enabled.put(entry.getKey(), entry.getValue());
                logger.at(Level.FINE).log("Added missing module to config: %s = %s",
                        entry.getKey(), entry.getValue());
            }
        }

        return config;
    }

    /**
     * Saves modules configuration to the plugin directory.
     *
     * @param pluginDirectory Plugin data directory (LivingLands/)
     * @param config          Configuration to save
     * @param logger          Logger for status messages
     */
    public static void save(@Nonnull Path pluginDirectory,
                            @Nonnull ModulesConfig config,
                            @Nonnull HytaleLogger logger) {
        var configPath = pluginDirectory.resolve(MODULES_FILE);

        try {
            Files.createDirectories(pluginDirectory);
            var json = GSON.toJson(config);
            Files.writeString(configPath, json);
            logger.at(Level.FINE).log("Saved modules config to: %s", configPath);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to save modules config");
        }
    }
}
