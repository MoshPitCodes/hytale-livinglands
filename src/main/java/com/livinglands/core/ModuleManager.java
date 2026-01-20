package com.livinglands.core;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.api.AbstractModule;
import com.livinglands.api.Module;
import com.livinglands.api.ModuleContext;
import com.livinglands.api.ModuleState;
import com.livinglands.core.config.ModulesConfig;
import com.livinglands.core.config.ModulesConfigLoader;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages the lifecycle of all modules.
 *
 * Responsibilities:
 * - Module registration
 * - Dependency resolution and auto-enabling
 * - Lifecycle orchestration (setup, start, shutdown)
 * - Inter-module access
 */
public final class ModuleManager {

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final HytaleLogger logger;
    private final Path pluginDirectory;
    private ModulesConfig modulesConfig;

    // Context dependencies (set before lifecycle methods)
    private EventRegistry eventRegistry;
    private CommandRegistry commandRegistry;
    private ComponentRegistryProxy<EntityStore> entityStoreRegistry;
    private PlayerRegistry playerRegistry;

    /**
     * Creates a new module manager.
     *
     * @param logger          The plugin logger
     * @param pluginDirectory Plugin data directory (LivingLands/)
     */
    public ModuleManager(@Nonnull HytaleLogger logger, @Nonnull Path pluginDirectory) {
        this.logger = logger;
        this.pluginDirectory = pluginDirectory;
    }

    /**
     * Sets the registries needed for module context.
     * Must be called before setupAll().
     */
    public void setRegistries(@Nonnull EventRegistry eventRegistry,
                               @Nonnull CommandRegistry commandRegistry,
                               @Nonnull ComponentRegistryProxy<EntityStore> entityStoreRegistry,
                               @Nonnull PlayerRegistry playerRegistry) {
        this.eventRegistry = eventRegistry;
        this.commandRegistry = commandRegistry;
        this.entityStoreRegistry = entityStoreRegistry;
        this.playerRegistry = playerRegistry;
    }

    /**
     * Loads the modules configuration from disk.
     */
    public void loadConfig() {
        this.modulesConfig = ModulesConfigLoader.loadOrCreate(pluginDirectory, logger);
    }

    /**
     * Registers a module with the manager.
     * Modules should be registered during plugin setup, before setupAll().
     *
     * @param module The module to register
     */
    public void register(@Nonnull Module module) {
        if (modules.containsKey(module.getId())) {
            logger.at(Level.WARNING).log("Module '%s' already registered, ignoring duplicate",
                    module.getId());
            return;
        }

        modules.put(module.getId(), module);
        logger.at(Level.FINE).log("Registered module: %s v%s", module.getName(), module.getVersion());
    }

    /**
     * Sets up all enabled modules in dependency order.
     *
     * This method:
     * 1. Auto-enables dependencies for enabled modules
     * 2. Resolves module order via topological sort
     * 3. Calls setup() on each enabled module
     */
    public void setupAll() {
        if (eventRegistry == null || commandRegistry == null || entityStoreRegistry == null || playerRegistry == null) {
            throw new IllegalStateException("Registries must be set before setupAll()");
        }

        logger.at(Level.FINE).log("========================================");
        logger.at(Level.FINE).log("Setting up modules...");
        logger.at(Level.FINE).log("========================================");

        // Auto-enable dependencies
        for (var module : modules.values()) {
            if (modulesConfig.isEnabled(module.getId())) {
                autoEnableDependencies(module);
            }
        }

        // Save config in case dependencies were auto-enabled
        ModulesConfigLoader.save(pluginDirectory, modulesConfig, logger);

        // Mark modules as enabled/disabled
        for (var module : modules.values()) {
            if (module instanceof AbstractModule abstractModule) {
                abstractModule.setEnabled(modulesConfig.isEnabled(module.getId()));
            }
        }

        // Resolve dependency order
        var orderedModules = resolveDependencyOrder();

        // Create context
        var context = new ModuleContext(
                logger,
                pluginDirectory,
                eventRegistry,
                commandRegistry,
                entityStoreRegistry,
                playerRegistry,
                this
        );

        // Setup each enabled module
        for (var module : orderedModules) {
            if (module.isEnabled()) {
                try {
                    module.setup(context);
                } catch (Exception e) {
                    logger.at(Level.SEVERE).withCause(e).log(
                            "Failed to setup module '%s'", module.getId());
                }
            } else {
                logger.at(Level.FINE).log("Module '%s' is disabled", module.getId());
            }
        }

        logModuleSummary();
    }

    /**
     * Starts all modules that were successfully set up.
     */
    public void startAll() {
        logger.at(Level.FINE).log("========================================");
        logger.at(Level.FINE).log("Starting modules...");
        logger.at(Level.FINE).log("========================================");

        for (var module : modules.values()) {
            if (module.getState() == ModuleState.SETUP) {
                try {
                    module.start();
                } catch (Exception e) {
                    logger.at(Level.SEVERE).withCause(e).log(
                            "Failed to start module '%s'", module.getId());
                }
            }
        }
    }

    /**
     * Shuts down all modules in reverse dependency order.
     */
    public void shutdownAll() {
        logger.at(Level.FINE).log("========================================");
        logger.at(Level.FINE).log("Shutting down modules...");
        logger.at(Level.FINE).log("========================================");

        // Shutdown in reverse order
        var orderedModules = new ArrayList<>(resolveDependencyOrder());
        Collections.reverse(orderedModules);

        for (var module : orderedModules) {
            if (module.getState().isActive()) {
                try {
                    module.shutdown();
                } catch (Exception e) {
                    logger.at(Level.SEVERE).withCause(e).log(
                            "Error shutting down module '%s'", module.getId());
                }
            }
        }
    }

    /**
     * Gets a module by ID and expected type.
     *
     * @param moduleId Module ID
     * @param type     Expected module class
     * @param <T>      Module type
     * @return Optional containing the module if found, enabled, and of correct type
     */
    @Nonnull
    public <T extends Module> Optional<T> getModule(@Nonnull String moduleId,
                                                     @Nonnull Class<T> type) {
        var module = modules.get(moduleId);
        if (module != null && module.isEnabled() && type.isInstance(module)) {
            return Optional.of(type.cast(module));
        }
        return Optional.empty();
    }

    /**
     * Checks if a module is registered and enabled.
     *
     * @param moduleId Module ID
     * @return true if module is registered and enabled
     */
    public boolean isModuleEnabled(@Nonnull String moduleId) {
        var module = modules.get(moduleId);
        return module != null && module.isEnabled();
    }

    /**
     * Gets all registered modules.
     */
    @Nonnull
    public Collection<Module> getAllModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    /**
     * Gets the modules configuration.
     */
    @Nonnull
    public ModulesConfig getModulesConfig() {
        return modulesConfig;
    }

    /**
     * Auto-enables dependencies for a module recursively.
     */
    private void autoEnableDependencies(@Nonnull Module module) {
        for (var depId : module.getDependencies()) {
            if (!modulesConfig.isEnabled(depId)) {
                var depModule = modules.get(depId);
                if (depModule != null) {
                    logger.at(Level.FINE).log(
                            "Auto-enabling dependency '%s' for module '%s'",
                            depId, module.getId());
                    modulesConfig.setEnabled(depId, true);
                    // Recursively check dependencies of the dependency
                    autoEnableDependencies(depModule);
                } else {
                    logger.at(Level.WARNING).log(
                            "Module '%s' depends on unknown module '%s'",
                            module.getId(), depId);
                }
            }
        }
    }

    /**
     * Resolves modules into dependency order using topological sort.
     *
     * @return List of modules in dependency order (dependencies first)
     */
    @Nonnull
    private List<Module> resolveDependencyOrder() {
        var result = new ArrayList<Module>();
        var visited = new HashSet<String>();
        var visiting = new HashSet<String>();

        for (var module : modules.values()) {
            visit(module, visited, visiting, result);
        }

        return result;
    }

    /**
     * DFS visit for topological sort.
     */
    private void visit(@Nonnull Module module,
                       @Nonnull Set<String> visited,
                       @Nonnull Set<String> visiting,
                       @Nonnull List<Module> result) {
        var id = module.getId();

        if (visited.contains(id)) {
            return;
        }

        if (visiting.contains(id)) {
            throw new IllegalStateException(
                    "Circular dependency detected involving module: " + id);
        }

        visiting.add(id);

        for (var depId : module.getDependencies()) {
            var depModule = modules.get(depId);
            if (depModule != null) {
                visit(depModule, visited, visiting, result);
            }
        }

        visiting.remove(id);
        visited.add(id);
        result.add(module);
    }

    /**
     * Logs a summary of module states.
     */
    private void logModuleSummary() {
        logger.at(Level.FINE).log("========================================");
        logger.at(Level.FINE).log("Module Status:");
        for (var module : modules.values()) {
            var status = switch (module.getState()) {
                case DISABLED -> "DISABLED";
                case SETUP -> "READY";
                case STARTED -> "RUNNING";
                case STOPPED -> "STOPPED";
                case ERROR -> "ERROR";
            };
            logger.at(Level.FINE).log("  - %s: %s", module.getName(), status);
        }
        logger.at(Level.FINE).log("========================================");
    }
}
