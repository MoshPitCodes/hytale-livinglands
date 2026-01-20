package com.livinglands;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.livinglands.core.CoreModule;
import com.livinglands.core.ModuleManager;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.hud.HudModule;
import com.livinglands.core.notifications.NotificationModule;
import com.livinglands.modules.claims.ClaimsModule;
import com.livinglands.modules.economy.EconomyModule;
import com.livinglands.modules.groups.GroupsModule;
import com.livinglands.modules.leveling.LevelingModule;
import com.livinglands.modules.metabolism.MetabolismModule;
import com.livinglands.modules.traders.TradersModule;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Main plugin class for Living Lands Hytale RPG mod.
 *
 * This is a modular plugin that supports enable/disable of individual features:
 * - Metabolism (hunger, thirst, energy)
 * - Plot Claiming (future)
 * - Leveling (future)
 * - Groups (future)
 * - Economy (future)
 * - Traders (future)
 *
 * Server administrators can configure which modules are enabled via modules.json.
 *
 * Lifecycle:
 * 1. Constructor - Receive plugin initialization context
 * 2. setup() - Initialize core services, register modules, call module setup
 * 3. start() - Start all modules
 * 4. shutdown() - Shutdown all modules, save data
 */
public class LivingLandsPlugin extends JavaPlugin {

    private static final String MOD_NAME = "Living Lands";

    // Plugin directory for configs and data
    private Path pluginDirectory;

    // Core services (shared across modules)
    private PlayerRegistry playerRegistry;

    // Module management
    private ModuleManager moduleManager;

    /**
     * Plugin constructor with initialization context.
     *
     * @param init Plugin initialization context provided by Hytale server
     */
    public LivingLandsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    /**
     * Setup phase - Register all modules and initialize core services.
     */
    @Override
    protected void setup() {
        super.setup();

        getLogger().at(Level.FINE).log("========================================");
        getLogger().at(Level.FINE).log("%s v%s", MOD_NAME, ModVersion.get());
        getLogger().at(Level.FINE).log("Modular Architecture");
        getLogger().at(Level.FINE).log("========================================");

        try {
            // Initialize plugin directory
            pluginDirectory = getFile().getParent().resolve("LivingLands");
            getLogger().at(Level.FINE).log("Plugin directory: %s", pluginDirectory);

            // Initialize core services (shared across all modules)
            getLogger().at(Level.FINE).log("Initializing core services...");
            playerRegistry = new PlayerRegistry(getLogger());

            // Initialize module manager
            getLogger().at(Level.FINE).log("Initializing module manager...");
            moduleManager = new ModuleManager(getLogger(), pluginDirectory);
            moduleManager.loadConfig();
            moduleManager.setRegistries(getEventRegistry(), getCommandRegistry(), getEntityStoreRegistry(), playerRegistry);

            // Register all available modules
            registerModules();

            // Setup all enabled modules
            moduleManager.setupAll();

            getLogger().at(Level.FINE).log("Setup completed successfully");

        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Failed to setup Living Lands");
            throw e;
        }
    }

    /**
     * Registers all available modules with the module manager.
     * Order matters - core modules should be registered first.
     */
    private void registerModules() {
        getLogger().at(Level.FINE).log("Registering modules...");

        // Core infrastructure modules (register first, no dependencies)
        moduleManager.register(new CoreModule());        // Shared utilities - must be first
        moduleManager.register(new NotificationModule());
        moduleManager.register(new HudModule());

        // Core gameplay modules (depend on HUD)
        moduleManager.register(new MetabolismModule());
        moduleManager.register(new LevelingModule());

        // Future modules (register all - disabled by default in modules.json)
        moduleManager.register(new ClaimsModule());
        moduleManager.register(new GroupsModule());
        moduleManager.register(new EconomyModule());
        moduleManager.register(new TradersModule());  // Depends on economy - will auto-enable it
    }

    /**
     * Start phase - Start all enabled modules.
     */
    @Override
    protected void start() {
        getLogger().at(Level.FINE).log("Starting Living Lands...");

        try {
            moduleManager.startAll();

            getLogger().at(Level.FINE).log("========================================");
            getLogger().at(Level.FINE).log("Living Lands started successfully!");
            getLogger().at(Level.FINE).log("========================================");

        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Failed to start Living Lands");
            throw e;
        }
    }

    /**
     * Shutdown phase - Shutdown all modules and save data.
     */
    @Override
    protected void shutdown() {
        getLogger().at(Level.FINE).log("Shutting down Living Lands...");

        try {
            // Shutdown all modules (in reverse dependency order)
            if (moduleManager != null) {
                moduleManager.shutdownAll();
            }

            // Shutdown core services
            if (playerRegistry != null) {
                playerRegistry.shutdown();
            }

            getLogger().at(Level.FINE).log("Living Lands shutdown completed");

        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Error during shutdown");
        }
    }

    /**
     * Gets the plugin data directory.
     */
    public Path getPluginDirectory() {
        return pluginDirectory;
    }

    /**
     * Gets the player registry (shared core service).
     */
    public PlayerRegistry getPlayerRegistry() {
        return playerRegistry;
    }

    /**
     * Gets the module manager.
     */
    public ModuleManager getModuleManager() {
        return moduleManager;
    }
}
