package com.livinglands.modules.leveling;

import com.livinglands.api.AbstractModule;
import com.livinglands.core.hud.HudModule;
import com.livinglands.modules.leveling.ability.AbilitySystem;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.XpCalculator;
import com.livinglands.modules.leveling.ui.SkillGuiElement;
import com.livinglands.modules.leveling.ui.SkillsPanelElement;
import com.livinglands.modules.leveling.util.PlayerPlacedBlockChecker;
import com.livinglands.modules.metabolism.MetabolismModule;

import java.util.Set;
import java.util.logging.Level;

/**
 * Leveling module for Living Lands.
 *
 * Provides a comprehensive profession leveling system:
 * - Five professions: Combat, Mining, Building, Logging, Gathering
 * - XP from gameplay activities
 * - Level progression with configurable XP curve
 * - Passive abilities that unlock at certain levels
 * - Integration with metabolism module for XP bonuses
 */
public final class LevelingModule extends AbstractModule {

    public static final String ID = "leveling";
    public static final String NAME = "Leveling System";
    public static final String VERSION = "1.0.1";

    private LevelingModuleConfig config;
    private LevelingSystem system;
    private LevelingDataPersistence persistence;
    private XpCalculator xpCalculator;
    private AbilitySystem abilitySystem;
    private SkillGuiElement skillGuiElement;
    private SkillsPanelElement panelElement;

    public LevelingModule() {
        super(ID, NAME, VERSION, Set.of(HudModule.ID)); // Depends on HUD module
    }

    @Override
    protected void onSetup() {
        // Load module configuration
        config = loadConfig("config.json", LevelingModuleConfig.class,
            LevelingModuleConfig::defaults);

        logger.at(Level.INFO).log("[%s] Loaded config: maxLevel=%d, baseXp=%d, exponent=%.2f",
            name, config.maxLevel, config.baseXp, config.xpExponent);

        // Initialize XP calculator
        xpCalculator = new XpCalculator(config.maxLevel, config.baseXp, config.xpExponent);

        // Initialize persistence
        persistence = new LevelingDataPersistence(configDirectory, logger, xpCalculator);

        // Initialize player-placed block tracking with persistence
        PlayerPlacedBlockChecker.initialize(configDirectory, logger);

        // Initialize the leveling system
        system = new LevelingSystem(config, logger, context.playerRegistry(), persistence);

        // Initialize the ability system
        abilitySystem = new AbilitySystem(system, config, logger);

        // Register HUD elements with HudModule
        logger.at(Level.INFO).log("[%s] Registering HUD elements...", name);
        var hudModuleOpt = context.moduleManager().getModule(HudModule.ID, HudModule.class);
        if (hudModuleOpt.isPresent()) {
            var hudModule = hudModuleOpt.get();

            // Skill GUI element (for temporary XP gain popups)
            skillGuiElement = new SkillGuiElement(system, config);
            skillGuiElement.setHudModule(hudModule);
            hudModule.registerElement(skillGuiElement);

            // Skills panel element (internal, used by /ll main command)
            panelElement = new SkillsPanelElement(system);
            panelElement.setHudModule(hudModule);
            hudModule.registerElement(panelElement);

            // Integrate with unified panel for /ll main command
            hudModule.setLevelingSystem(system);
            hudModule.setAbilitySystem(abilitySystem);
        } else {
            logger.at(Level.WARNING).log("[%s] HUD module not found, HUD elements not registered", name);
        }

        // Try to integrate with metabolism module (optional)
        context.moduleManager().getModule("metabolism", MetabolismModule.class)
            .ifPresent(metabolism -> {
                system.setMetabolismModule(metabolism);
                logger.at(Level.INFO).log("[%s] Metabolism integration enabled", name);
            });

        // Register player lifecycle listener
        var playerListener = new com.livinglands.modules.leveling.listeners.LevelingPlayerListener(
            this, system, logger
        );
        playerListener.register(context.eventRegistry());

        // Register XP listeners for all professions
        registerXpListeners();

        // Register ability handlers for passive abilities
        registerAbilityHandlers();

        // Register commands
        registerCommands();

        logger.at(Level.INFO).log("[%s] Module setup complete", name);
    }

    private void registerXpListeners() {
        var entityStoreRegistry = context.entityStoreRegistry();

        // Block placement tracking system (tracks player-placed blocks)
        entityStoreRegistry.registerSystem(
            new com.livinglands.modules.leveling.listeners.BlockPlaceTrackingSystem(logger)
        );

        // Mining XP system (ECS - responds to BreakBlockEvent for ores)
        entityStoreRegistry.registerSystem(
            new com.livinglands.modules.leveling.listeners.MiningXpSystem(system, config, logger)
        );

        // Logging XP system (ECS - responds to BreakBlockEvent for logs)
        entityStoreRegistry.registerSystem(
            new com.livinglands.modules.leveling.listeners.LoggingXpSystem(system, config, logger)
        );

        // Building XP system (ECS - responds to PlaceBlockEvent)
        entityStoreRegistry.registerSystem(
            new com.livinglands.modules.leveling.listeners.BuildingXpSystem(system, config, logger)
        );

        // Gathering XP system (ECS - responds to InteractivelyPickupItemEvent)
        entityStoreRegistry.registerSystem(
            new com.livinglands.modules.leveling.listeners.GatheringXpSystem(system, config, logger)
        );

        // Combat XP system (ECS - responds to KillFeedEvent)
        entityStoreRegistry.registerSystem(
            new com.livinglands.modules.leveling.listeners.CombatXpSystem(system, config, logger)
        );

        logger.at(Level.INFO).log("[%s] Registered all profession XP systems (ECS)", name);
    }

    private void registerAbilityHandlers() {
        var eventRegistry = context.eventRegistry();
        var playerRegistry = context.playerRegistry();

        // Combat abilities (Critical Strike, Lifesteal)
        new com.livinglands.modules.leveling.ability.handlers.CombatAbilityHandler(
            abilitySystem, playerRegistry, logger
        ).register(eventRegistry);

        // Mining abilities (Double Ore, Lucky Strike)
        new com.livinglands.modules.leveling.ability.handlers.MiningAbilityHandler(
            abilitySystem, playerRegistry, logger
        ).register(eventRegistry);

        // Logging abilities (Efficient Chopping, Bark Collector)
        new com.livinglands.modules.leveling.ability.handlers.LoggingAbilityHandler(
            abilitySystem, playerRegistry, logger
        ).register(eventRegistry);

        // Building abilities (Material Saver)
        new com.livinglands.modules.leveling.ability.handlers.BuildingAbilityHandler(
            abilitySystem, playerRegistry, logger
        ).register(eventRegistry);

        // Gathering abilities (Double Harvest, Rare Find)
        new com.livinglands.modules.leveling.ability.handlers.GatheringAbilityHandler(
            abilitySystem, playerRegistry, logger
        ).register(eventRegistry);

        logger.at(Level.INFO).log("[%s] Registered all profession ability handlers", name);
    }

    private void registerCommands() {
        var commandRegistry = context.commandRegistry();

        // /setlevel command (admin)
        commandRegistry.registerCommand(
            new com.livinglands.modules.leveling.commands.SetLevelCommand(system)
        );

        logger.at(Level.INFO).log("[%s] Registered commands: /setlevel", name);
    }

    @Override
    protected void onStart() {
        // Start the leveling system background tasks
        system.start();

        // Set up UI callbacks
        setupUiCallbacks();

        logger.at(Level.INFO).log("[%s] Module started", name);
    }

    private void setupUiCallbacks() {
        // Level-up notification callback
        system.setLevelUpCallback((playerId, event) -> {
            var sessionOpt = context.playerRegistry().getSession(playerId);
            sessionOpt.ifPresent(session -> {
                if (config.showLevelUpTitles) {
                    com.livinglands.modules.leveling.ui.LevelUpNotification.show(
                        session, event.profession(), event.newLevel(), logger
                    );
                }
            });
        });

        // XP gain notification callback - update skill GUI element
        system.setXpGainCallback((playerId, event) -> {
            // Record XP gain in skill GUI element for display
            if (skillGuiElement != null) {
                skillGuiElement.recordXpGain(playerId, event.profession(), event.xpGained());
            }

            logger.at(Level.FINE).log("Player %s gained %d %s XP",
                playerId, event.xpGained(), event.profession().getDisplayName());
        });
    }

    @Override
    protected void onShutdown() {
        // Save placed block tracking data
        logger.at(Level.INFO).log("[%s] Saving placed block tracking data...", name);
        PlayerPlacedBlockChecker.saveAll();
        PlayerPlacedBlockChecker.clearAll();

        // Stop the system and save all data
        if (system != null) {
            system.stop();
        }

        logger.at(Level.INFO).log("[%s] Module shutdown complete", name);
    }

    /**
     * Get the leveling system.
     */
    public LevelingSystem getSystem() {
        return system;
    }

    /**
     * Get the module configuration.
     */
    public LevelingModuleConfig getConfig() {
        return config;
    }

    /**
     * Get the XP calculator.
     */
    public XpCalculator getXpCalculator() {
        return xpCalculator;
    }

    /**
     * Get the data persistence handler.
     */
    public LevelingDataPersistence getPersistence() {
        return persistence;
    }

    /**
     * Get the ability system.
     */
    public AbilitySystem getAbilitySystem() {
        return abilitySystem;
    }
}
