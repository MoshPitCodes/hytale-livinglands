package com.livinglands.modules.metabolism;

import com.livinglands.api.AbstractModule;
import com.livinglands.core.CoreModule;
import com.livinglands.core.events.PlayerDeathBroadcaster;
import com.livinglands.core.hud.HudModule;
import com.livinglands.core.util.SpeedManager;
import com.livinglands.modules.metabolism.buff.BuffEffectsSystem;
import com.livinglands.modules.metabolism.buff.BuffsSystem;
import com.livinglands.modules.metabolism.config.MetabolismModuleConfig;
import com.livinglands.modules.metabolism.consumables.ConsumableRegistry;
import com.livinglands.modules.metabolism.listeners.BedInteractionListener;
import com.livinglands.modules.metabolism.listeners.CombatDetectionListener;
import com.livinglands.modules.metabolism.listeners.MetabolismPlayerListener;
import com.livinglands.modules.metabolism.poison.PoisonRegistry;
import com.livinglands.modules.metabolism.ui.MetabolismHudElement;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Metabolism module for Living Lands.
 *
 * Provides comprehensive survival mechanics:
 * - Hunger, Thirst, and Energy stats with configurable depletion
 * - Activity-based depletion multipliers (sprint, swim, combat)
 * - Debuff effects when stats reach critical levels
 * - Food/drink consumption for stat restoration
 * - Poison effects from consumables and native debuffs
 * - Bed interaction for energy restoration
 * - HUD display of current stats
 */
public final class MetabolismModule extends AbstractModule {

    public static final String ID = "metabolism";
    public static final String NAME = "Metabolism System";
    public static final String VERSION = "1.1.2";

    private MetabolismModuleConfig config;
    private MetabolismSystem system;
    private MetabolismHudElement hudElement;
    private BuffsSystem buffsSystem;
    private BuffEffectsSystem buffEffectsSystem;
    private Consumer<UUID> deathListener;

    public MetabolismModule() {
        super(ID, NAME, VERSION, Set.of(CoreModule.ID, HudModule.ID)); // Depends on Core and HUD modules
    }

    @Override
    protected void onSetup() {
        // Load module config
        config = loadConfig("config.json", MetabolismModuleConfig.class,
                MetabolismModuleConfig::defaults);

        // Initialize consumable registry from config
        logger.at(java.util.logging.Level.INFO).log("[%s] Initializing consumable registry...", name);
        ConsumableRegistry.initialize(config.consumables, logger);
        logger.at(java.util.logging.Level.INFO).log("[%s] Registered %d consumable items",
                name, ConsumableRegistry.getRegisteredCount());

        // Initialize poison registry from config
        logger.at(java.util.logging.Level.INFO).log("[%s] Initializing poison registry...", name);
        PoisonRegistry.initialize(config.poison, logger);
        logger.at(java.util.logging.Level.INFO).log("[%s] Registered %d poisonous items",
                name, PoisonRegistry.getRegisteredCount());

        // Initialize custom stats
        logger.at(java.util.logging.Level.INFO).log("[%s] Initializing custom stats...", name);
        HungerStat.initialize();
        ThirstStat.initialize();
        EnergyStat.initialize();

        // Get utilities from CoreModule
        SpeedManager speedManager = null;
        PlayerDeathBroadcaster deathBroadcaster = null;
        var coreModuleOpt = context.moduleManager().getModule(CoreModule.ID, CoreModule.class);
        if (coreModuleOpt.isPresent()) {
            var coreModule = coreModuleOpt.get();
            speedManager = coreModule.getSpeedManager();
            deathBroadcaster = coreModule.getDeathBroadcaster();
            logger.at(java.util.logging.Level.INFO).log("[%s] SpeedManager and DeathBroadcaster obtained from CoreModule", name);
        } else {
            logger.at(java.util.logging.Level.WARNING).log("[%s] CoreModule not found, speed effects and death handling disabled", name);
        }

        // Create metabolism system
        logger.at(java.util.logging.Level.INFO).log("[%s] Creating metabolism system...", name);
        system = new MetabolismSystem(config, logger, context.playerRegistry(), configDirectory, speedManager);

        // Register HUD element with HudModule
        logger.at(java.util.logging.Level.INFO).log("[%s] Registering HUD element...", name);
        var hudModuleOpt = context.moduleManager().getModule(HudModule.ID, HudModule.class);
        if (hudModuleOpt.isPresent()) {
            var hudModule = hudModuleOpt.get();
            hudElement = new MetabolismHudElement(system, config);
            hudModule.registerElement(hudElement);

            // Integrate with unified panel for /ll main command
            hudModule.setMetabolismSystem(system);
        } else {
            logger.at(java.util.logging.Level.WARNING).log("[%s] HUD module not found, HUD element not registered", name);
        }

        // Initialize buff systems
        if (config.buffs.enabled) {
            logger.at(java.util.logging.Level.INFO).log("[%s] Initializing buff system...", name);
            buffsSystem = new BuffsSystem(config.buffs, logger, context.playerRegistry());
            buffsSystem.setDebuffsSystem(system.getDebuffsSystem());

            buffEffectsSystem = new BuffEffectsSystem(config.buffs, logger);
            buffEffectsSystem.setMetabolismSystem(system, context.playerRegistry());

            // Wire buff system into metabolism system
            system.setBuffSystems(buffsSystem, buffEffectsSystem);

            // Integrate with HudModule for /ll stats command
            if (hudModuleOpt.isPresent()) {
                hudModuleOpt.get().setBuffsSystem(buffsSystem);
            }
        }

        // Register event listeners
        logger.at(java.util.logging.Level.INFO).log("[%s] Registering event listeners...", name);
        new MetabolismPlayerListener(this).register(context.eventRegistry());
        new BedInteractionListener(this).register(context.eventRegistry());
        new CombatDetectionListener(this).register(context.eventRegistry());

        // Register death listener with CoreModule's death broadcaster
        if (deathBroadcaster != null) {
            deathListener = this::onPlayerDeath;
            deathBroadcaster.addListener(deathListener);
            logger.at(java.util.logging.Level.INFO).log("[%s] Death listener registered with CoreModule", name);
        }
    }

    /**
     * Handle player death - reset metabolism to initial values.
     */
    private void onPlayerDeath(UUID playerId) {
        var dataOpt = system.getPlayerData(playerId);
        if (dataOpt.isPresent()) {
            var data = dataOpt.get();
            var metabolism = config.metabolism;

            logger.at(java.util.logging.Level.FINE).log(
                "[%s] Player %s died (hunger=%.0f, thirst=%.0f, energy=%.0f) - resetting metabolism",
                name, playerId, data.getHunger(), data.getThirst(), data.getEnergy()
            );

            // Reset to initial values
            data.reset(
                System.currentTimeMillis(),
                metabolism.initialHunger,
                metabolism.initialThirst,
                metabolism.initialEnergy
            );

            logger.at(java.util.logging.Level.FINE).log(
                "[%s] Reset metabolism for player %s on death (hunger=%.0f, thirst=%.0f, energy=%.0f)",
                name, playerId, metabolism.initialHunger, metabolism.initialThirst, metabolism.initialEnergy
            );
        }
    }

    @Override
    protected void onStart() {
        var metabolism = config.metabolism;
        if (metabolism.enableHunger || metabolism.enableThirst || metabolism.enableEnergy) {
            system.start();

            logger.at(java.util.logging.Level.INFO).log("[%s] Features enabled:", name);
            logger.at(java.util.logging.Level.INFO).log("[%s]   - Hunger: %s", name, metabolism.enableHunger);
            logger.at(java.util.logging.Level.INFO).log("[%s]   - Thirst: %s", name, metabolism.enableThirst);
            logger.at(java.util.logging.Level.INFO).log("[%s]   - Energy: %s", name, metabolism.enableEnergy);
        } else {
            logger.at(java.util.logging.Level.WARNING).log(
                    "[%s] All metabolism features disabled in configuration", name);
        }
    }

    @Override
    protected void onShutdown() {
        // Remove death listener
        if (deathListener != null) {
            var coreModuleOpt = context.moduleManager().getModule(CoreModule.ID, CoreModule.class);
            coreModuleOpt.ifPresent(core -> core.getDeathBroadcaster().removeListener(deathListener));
        }

        if (system != null) {
            system.saveAll();
            system.stop();
        }
    }

    /**
     * Gets the metabolism system.
     */
    public MetabolismSystem getSystem() {
        return system;
    }

    /**
     * Gets the module configuration.
     */
    public MetabolismModuleConfig getConfig() {
        return config;
    }

    @Override
    public com.livinglands.api.ModuleUIProvider getUIProvider() {
        if (system != null && config != null) {
            return new com.livinglands.modules.metabolism.ui.MetabolismUIProvider(system, config, logger);
        }
        return null;
    }
}
