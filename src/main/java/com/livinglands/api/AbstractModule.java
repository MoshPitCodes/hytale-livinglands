package com.livinglands.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Base implementation for Living Lands modules.
 *
 * Provides common functionality:
 * - Configuration loading and saving
 * - Lifecycle state management
 * - Access to shared services via ModuleContext
 *
 * Subclasses must implement:
 * - onSetup() - Module-specific setup logic
 * - onStart() - Module-specific start logic
 * - onShutdown() - Module-specific shutdown logic
 */
public abstract non-sealed class AbstractModule implements Module {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    protected final String id;
    protected final String name;
    protected final String version;
    protected final Set<String> dependencies;

    protected ModuleContext context;
    protected ModuleState state = ModuleState.DISABLED;
    protected HytaleLogger logger;
    protected Path configDirectory;
    protected boolean enabled = false;

    /**
     * Creates a new module with the given metadata.
     *
     * @param id           Unique module identifier
     * @param name         Human-readable name
     * @param version      Module version
     * @param dependencies Set of module IDs this depends on
     */
    protected AbstractModule(@Nonnull String id, @Nonnull String name,
                             @Nonnull String version, @Nonnull Set<String> dependencies) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.dependencies = Set.copyOf(dependencies);
    }

    @Override
    @Nonnull
    public String getId() {
        return id;
    }

    @Override
    @Nonnull
    public String getName() {
        return name;
    }

    @Override
    @Nonnull
    public String getVersion() {
        return version;
    }

    @Override
    @Nonnull
    public Set<String> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    @Nonnull
    public ModuleState getState() {
        return state;
    }

    /**
     * Sets whether this module is enabled.
     * Called by ModuleManager based on modules.json.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public final void setup(@Nonnull ModuleContext context) {
        if (!enabled) {
            return;
        }

        if (!state.canTransitionTo(ModuleState.SETUP)) {
            throw new IllegalStateException(
                    "Cannot setup module '%s' from state %s".formatted(id, state));
        }

        this.context = context;
        this.logger = context.logger();
        this.configDirectory = context.pluginDirectory().resolve(id);

        try {
            // Ensure config directory exists
            Files.createDirectories(configDirectory);

            logger.at(Level.INFO).log("[%s] Setting up module v%s...", name, version);

            // Call subclass setup
            onSetup();

            state = ModuleState.SETUP;
            logger.at(Level.INFO).log("[%s] Setup complete", name);

        } catch (Exception e) {
            state = ModuleState.ERROR;
            logger.at(Level.SEVERE).withCause(e).log("[%s] Setup failed", name);
            throw new RuntimeException("Module setup failed: " + id, e);
        }
    }

    @Override
    public final void start() {
        if (!enabled || state != ModuleState.SETUP) {
            return;
        }

        if (!state.canTransitionTo(ModuleState.STARTED)) {
            throw new IllegalStateException(
                    "Cannot start module '%s' from state %s".formatted(id, state));
        }

        try {
            logger.at(Level.INFO).log("[%s] Starting...", name);

            // Call subclass start
            onStart();

            state = ModuleState.STARTED;
            logger.at(Level.INFO).log("[%s] Started successfully", name);

        } catch (Exception e) {
            state = ModuleState.ERROR;
            logger.at(Level.SEVERE).withCause(e).log("[%s] Start failed", name);
            throw new RuntimeException("Module start failed: " + id, e);
        }
    }

    @Override
    public final void shutdown() {
        if (state != ModuleState.SETUP && state != ModuleState.STARTED) {
            return;
        }

        try {
            logger.at(Level.INFO).log("[%s] Shutting down...", name);

            // Call subclass shutdown
            onShutdown();

            state = ModuleState.STOPPED;
            logger.at(Level.INFO).log("[%s] Shutdown complete", name);

        } catch (Exception e) {
            state = ModuleState.ERROR;
            logger.at(Level.SEVERE).withCause(e).log("[%s] Shutdown error", name);
        }
    }

    /**
     * Gets the module context.
     * Only available after setup() is called.
     *
     * @return The module context
     */
    public ModuleContext getContext() {
        return context;
    }

    /**
     * Module-specific setup logic.
     * Called during the setup phase after context is initialized.
     *
     * Implementations should:
     * - Load configuration via loadConfig()
     * - Register commands and events
     * - Initialize systems (but not start background tasks)
     */
    protected abstract void onSetup();

    /**
     * Module-specific start logic.
     * Called during the start phase after all modules are set up.
     *
     * Implementations should:
     * - Start tick loops and scheduled tasks
     * - Begin background processing
     */
    protected abstract void onStart();

    /**
     * Module-specific shutdown logic.
     * Called during the shutdown phase.
     *
     * Implementations should:
     * - Save all data
     * - Stop background tasks
     * - Release resources
     */
    protected abstract void onShutdown();

    /**
     * Loads a configuration file from the module's config directory.
     * Creates the file with defaults if it doesn't exist.
     *
     * @param filename        Config filename (e.g., "config.json")
     * @param type            Class to deserialize to
     * @param defaultSupplier Supplier for default config if file doesn't exist
     * @param <T>             Config type
     * @return Loaded or default configuration
     */
    protected <T> T loadConfig(@Nonnull String filename, @Nonnull Class<T> type,
                               @Nonnull Supplier<T> defaultSupplier) {
        var configPath = configDirectory.resolve(filename);

        try {
            if (Files.exists(configPath)) {
                var json = Files.readString(configPath);
                var config = GSON.fromJson(json, type);
                logger.at(Level.INFO).log("[%s] Loaded config from %s", name, configPath);
                return config;
            } else {
                var defaultConfig = defaultSupplier.get();
                saveConfig(filename, defaultConfig);
                logger.at(Level.INFO).log("[%s] Created default config at %s", name, configPath);
                return defaultConfig;
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log(
                    "[%s] Failed to load config %s, using defaults", name, filename);
            return defaultSupplier.get();
        }
    }

    /**
     * Saves a configuration object to the module's config directory.
     *
     * @param filename Config filename
     * @param config   Config object to save
     * @param <T>      Config type
     */
    protected <T> void saveConfig(@Nonnull String filename, @Nonnull T config) {
        var configPath = configDirectory.resolve(filename);

        try {
            Files.createDirectories(configPath.getParent());
            var json = GSON.toJson(config);
            Files.writeString(configPath, json);
            logger.at(Level.FINE).log("[%s] Saved config to %s", name, configPath);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log(
                    "[%s] Failed to save config %s", name, filename);
        }
    }

    /**
     * Gets a dependency module by ID and type.
     * Only call this after setup when dependencies are guaranteed available.
     *
     * @param moduleId Module ID
     * @param type     Expected module class
     * @param <T>      Module type
     * @return The dependency module
     * @throws IllegalStateException if dependency not found or wrong type
     */
    protected <T extends Module> T requireDependency(@Nonnull String moduleId,
                                                      @Nonnull Class<T> type) {
        return context.getModule(moduleId, type)
                .orElseThrow(() -> new IllegalStateException(
                        "Required dependency '%s' not found for module '%s'"
                                .formatted(moduleId, id)));
    }
}
