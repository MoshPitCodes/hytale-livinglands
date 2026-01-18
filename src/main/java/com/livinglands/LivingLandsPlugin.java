package com.livinglands;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.livinglands.commands.StatsCommand;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.config.ConfigLoader;
import com.livinglands.core.config.ModConfig;
import com.livinglands.listeners.BedInteractionListener;
import com.livinglands.listeners.CombatDetectionListener;
import com.livinglands.listeners.DeathHandlerListener;
import com.livinglands.listeners.PlayerEventListener;

import java.nio.file.Path;
import com.livinglands.metabolism.EnergyStat;
import com.livinglands.metabolism.HungerStat;
import com.livinglands.metabolism.MetabolismSystem;
import com.livinglands.metabolism.ThirstStat;
import com.livinglands.metabolism.consumables.ConsumableRegistry;
import com.livinglands.metabolism.poison.PoisonRegistry;
import com.livinglands.ui.MetabolismHudManager;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Main plugin class for Living Lands Hytale RPG mod.
 *
 * Phase 1: Core infrastructure and Metabolism system
 * - Hunger and Thirst stats
 * - Tick-based depletion with activity multipliers
 * - Player and admin commands
 *
 * Phase 2: Energy/Tiredness system
 * - Energy stat with depletion
 * - Player event listeners for lifecycle
 *
 * Lifecycle:
 * 1. Constructor - Receive plugin initialization context
 * 2. setup() - Register commands, events, stats, components
 * 3. start() - Start systems (metabolism tick loop)
 * 4. shutdown() - Cleanup resources
 */
public class LivingLandsPlugin extends JavaPlugin {

    private static final String MOD_NAME = "Living Lands";
    private static final String VERSION = "1.0.0-beta";
    private static final String PHASE = "Phase 3: Complete Metabolism System";
    private static final String CONFIG_FILE = "config.json";

    // Configuration
    private ModConfig config;
    private Path configPath;

    // Core services
    private PlayerRegistry playerRegistry;

    // Systems
    private MetabolismSystem metabolismSystem;
    private MetabolismHudManager hudManager;

    /**
     * Plugin constructor with initialization context.
     *
     * @param init Plugin initialization context provided by Hytale server
     */
    public LivingLandsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    /**
     * Setup phase - Register all components.
     * Register commands, events, stats, and component types.
     */
    @Override
    protected void setup() {
        super.setup();

        getLogger().at(Level.INFO).log("========================================");
        getLogger().at(Level.INFO).log("%s v%s", MOD_NAME, VERSION);
        getLogger().at(Level.INFO).log("%s", PHASE);
        getLogger().at(Level.INFO).log("========================================");

        // Load configuration from file
        configPath = getFile().getParent().resolve("LivingLands").resolve(CONFIG_FILE);
        config = ConfigLoader.loadOrCreate(configPath, getLogger());
        logConfigSummary();

        getLogger().at(Level.INFO).log("Setting up Living Lands...");

        try {
            // Initialize core services first
            getLogger().at(Level.INFO).log("Initializing core services...");
            playerRegistry = new PlayerRegistry(getLogger());

            // Initialize consumable registry from config
            getLogger().at(Level.INFO).log("Initializing consumable registry...");
            ConsumableRegistry.initialize(config.consumables(), getLogger());
            getLogger().at(Level.INFO).log("Registered %d consumable items from config", ConsumableRegistry.getRegisteredCount());

            // Initialize poison registry from config
            getLogger().at(Level.INFO).log("Initializing poison registry...");
            PoisonRegistry.initialize(config.poison(), getLogger());
            getLogger().at(Level.INFO).log("Registered %d poisonous items from config", PoisonRegistry.getRegisteredCount());

            // Initialize custom stats
            getLogger().at(Level.INFO).log("Initializing custom stats...");
            HungerStat.initialize();
            ThirstStat.initialize();
            EnergyStat.initialize();

            // Initialize metabolism system (pass playerRegistry and plugin directory)
            getLogger().at(Level.INFO).log("Initializing metabolism system...");
            var pluginDirectory = getFile().getParent().resolve("LivingLands");
            metabolismSystem = new MetabolismSystem(config, getLogger(), playerRegistry, pluginDirectory);

            // Initialize HUD manager
            getLogger().at(Level.INFO).log("Initializing HUD manager...");
            hudManager = new MetabolismHudManager(metabolismSystem, playerRegistry, config, getLogger());

            // Register commands
            getLogger().at(Level.INFO).log("Registering commands...");
            getCommandRegistry().registerCommand(new StatsCommand(metabolismSystem));

            // Register event listeners
            getLogger().at(Level.INFO).log("Registering event listeners...");
            var playerEventListener = new PlayerEventListener(this);
            playerEventListener.register(getEventRegistry());

            // Note: Food consumption is now detected via FoodEffectDetector in MetabolismSystem tick
            // Consumable poison is handled by PoisonEffectsSystem (timed effects from eating poisonous items)
            // Native debuffs are handled by DebuffEffectsSystem (Hytale's poison, burn, stun, freeze, root, slow effects)

            // Register bed interaction listener for energy restoration
            var bedInteractionListener = new BedInteractionListener(this);
            bedInteractionListener.register(getEventRegistry());

            // Register combat detection listener for activity multiplier
            var combatDetectionListener = new CombatDetectionListener(this);
            combatDetectionListener.register(getEventRegistry());

            // Register death handler for respawn stat reset
            var deathHandlerListener = new DeathHandlerListener(this);
            deathHandlerListener.register(getEventRegistry());

            getLogger().at(Level.INFO).log("Setup completed successfully");

        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Failed to setup Living Lands");
            throw e;
        }
    }

    /**
     * Start phase - Begin active systems.
     * Start metabolism tick loop and any background tasks.
     */
    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("Starting Living Lands...");

        try {
            // Start metabolism system if any stat is enabled
            var metabolism = config.metabolism();
            if (metabolism.enableHunger() || metabolism.enableThirst() || metabolism.enableEnergy()) {
                metabolismSystem.start();
                hudManager.start();
                getLogger().at(Level.INFO).log("Metabolism system and HUD started");
            } else {
                getLogger().at(Level.WARNING).log("Metabolism system disabled in configuration");
            }

            getLogger().at(Level.INFO).log("========================================");
            getLogger().at(Level.INFO).log("Living Lands started successfully!");
            getLogger().at(Level.INFO).log("Features enabled:");
            getLogger().at(Level.INFO).log("  - Hunger: %s", metabolism.enableHunger());
            getLogger().at(Level.INFO).log("  - Thirst: %s", metabolism.enableThirst());
            getLogger().at(Level.INFO).log("  - Energy: %s", metabolism.enableEnergy());
            getLogger().at(Level.INFO).log("========================================");

        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Failed to start Living Lands");
            throw e;
        }
    }

    /**
     * Shutdown phase - Cleanup resources.
     * Stop systems and save any pending data.
     */
    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("Shutting down Living Lands...");

        try {
            // Stop HUD manager
            if (hudManager != null) {
                hudManager.stop();
            }

            // Save all player data and stop metabolism system
            if (metabolismSystem != null) {
                metabolismSystem.saveAll();
                metabolismSystem.stop();
                getLogger().at(Level.INFO).log("Metabolism system stopped");
            }

            // Shutdown player registry
            if (playerRegistry != null) {
                playerRegistry.shutdown();
            }

            getLogger().at(Level.INFO).log("Living Lands shutdown completed");

        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Error during shutdown");
        }
    }

    /**
     * Gets the plugin configuration.
     * Exposed for testing and external access.
     */
    public ModConfig getModConfig() {
        return config;
    }

    /**
     * Gets the player registry.
     * Central registry for all player sessions and ECS references.
     */
    public PlayerRegistry getPlayerRegistry() {
        return playerRegistry;
    }

    /**
     * Gets the metabolism system.
     * Exposed for testing and external access.
     */
    public MetabolismSystem getMetabolismSystem() {
        return metabolismSystem;
    }

    /**
     * Gets the HUD manager.
     * Used by listeners to initialize player HUDs.
     */
    public MetabolismHudManager getHudManager() {
        return hudManager;
    }

    /**
     * Logs a summary of the loaded configuration.
     */
    private void logConfigSummary() {
        var m = config.metabolism();
        getLogger().at(Level.INFO).log("Configuration loaded from: %s", configPath);
        getLogger().at(Level.INFO).log("  Hunger: %s (depletion: %.0fs, threshold: %.0f)",
            m.enableHunger() ? "enabled" : "disabled", m.hungerDepletionRate(), m.starvationThreshold());
        getLogger().at(Level.INFO).log("  Thirst: %s (depletion: %.0fs, threshold: %.0f)",
            m.enableThirst() ? "enabled" : "disabled", m.thirstDepletionRate(), m.dehydrationThreshold());
        getLogger().at(Level.INFO).log("  Energy: %s (depletion: %.0fs, threshold: %.0f)",
            m.enableEnergy() ? "enabled" : "disabled", m.energyDepletionRate(), m.exhaustionThreshold());
    }
}
