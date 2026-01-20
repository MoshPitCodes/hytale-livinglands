package com.livinglands.modules.metabolism;

import com.livinglands.api.AbstractModule;
import com.livinglands.core.hud.HudModule;
import com.livinglands.modules.metabolism.buff.BuffEffectsSystem;
import com.livinglands.modules.metabolism.buff.BuffsSystem;
import com.livinglands.modules.metabolism.commands.StatsCommand;
import com.livinglands.modules.metabolism.config.MetabolismModuleConfig;
import com.livinglands.modules.metabolism.consumables.ConsumableRegistry;
import com.livinglands.modules.metabolism.listeners.BedInteractionListener;
import com.livinglands.modules.metabolism.listeners.CombatDetectionListener;
import com.livinglands.modules.metabolism.listeners.MetabolismPlayerListener;
import com.livinglands.modules.metabolism.listeners.PlayerDeathSystem;
import com.livinglands.modules.metabolism.poison.PoisonRegistry;
import com.livinglands.modules.metabolism.ui.MetabolismHudElement;

import java.util.Set;

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
    private PlayerDeathSystem playerDeathSystem;

    public MetabolismModule() {
        super(ID, NAME, VERSION, Set.of(HudModule.ID)); // Depends on HUD module
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

        // Create metabolism system
        logger.at(java.util.logging.Level.INFO).log("[%s] Creating metabolism system...", name);
        system = new MetabolismSystem(config, logger, context.playerRegistry(), configDirectory);

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

        // Register commands
        logger.at(java.util.logging.Level.INFO).log("[%s] Registering commands...", name);
        context.commandRegistry().registerCommand(new StatsCommand(system, buffsSystem));

        // Register event listeners
        logger.at(java.util.logging.Level.INFO).log("[%s] Registering event listeners...", name);
        new MetabolismPlayerListener(this).register(context.eventRegistry());
        new BedInteractionListener(this).register(context.eventRegistry());
        new CombatDetectionListener(this).register(context.eventRegistry());

        // Register death detection ECS system (resets metabolism on death)
        playerDeathSystem = new PlayerDeathSystem(system, config, logger);
        context.entityStoreRegistry().registerSystem(playerDeathSystem);

        logger.at(java.util.logging.Level.INFO).log("[%s] Death detection registered (ECS)", name);
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
}
