package com.livinglands.modules.leveling;

import com.livinglands.api.AbstractModule;
import com.livinglands.core.CoreModule;
import com.livinglands.core.events.PlayerDeathBroadcaster;
import com.livinglands.core.hud.HudModule;
import com.livinglands.core.notifications.NotificationModule;
import com.livinglands.core.util.SpeedManager;
import com.livinglands.modules.leveling.ability.AbilitySystem;
import com.livinglands.modules.leveling.ability.PermanentBuffManager;
import com.livinglands.modules.leveling.ability.TimedBuffManager;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.XpCalculator;
import com.livinglands.modules.leveling.ui.SkillGuiElement;
import com.livinglands.modules.leveling.ui.SkillsPanelElement;
import com.livinglands.modules.leveling.util.PlayerPlacedBlockChecker;
import com.livinglands.modules.metabolism.MetabolismModule;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
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
    private TimedBuffManager timedBuffManager;
    private PermanentBuffManager permanentBuffManager;
    private SkillGuiElement skillGuiElement;
    private SkillsPanelElement panelElement;
    private Consumer<UUID> deathListener;
    @Nullable
    private NotificationModule notificationModule;

    public LevelingModule() {
        super(ID, NAME, VERSION, Set.of(CoreModule.ID, HudModule.ID)); // Depends on Core and HUD modules
    }

    @Override
    protected void onSetup() {
        // Load module configuration
        config = loadConfig("config.json", LevelingModuleConfig.class,
            LevelingModuleConfig::defaults);

        logger.at(Level.FINE).log("[%s] Loaded config: maxLevel=%d, baseXp=%d, exponent=%.2f",
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
        abilitySystem = new AbilitySystem(system, config, context.playerRegistry(), logger);

        // Initialize timed buff manager
        timedBuffManager = new TimedBuffManager(context.playerRegistry(), logger);
        abilitySystem.setTimedBuffManager(timedBuffManager);

        // Initialize permanent buff manager
        permanentBuffManager = new PermanentBuffManager(context.playerRegistry(), system, config, logger);
        abilitySystem.setPermanentBuffManager(permanentBuffManager);

        // Wire ability system back to leveling system for XP boosts
        system.setAbilitySystem(abilitySystem);

        // Get utilities from CoreModule
        context.moduleManager().getModule(CoreModule.ID, CoreModule.class)
            .ifPresent(core -> {
                // SpeedManager for ability speed effects
                SpeedManager speedManager = core.getSpeedManager();
                timedBuffManager.setSpeedManager(speedManager);
                permanentBuffManager.setSpeedManager(speedManager);
                logger.at(Level.FINE).log("[%s] SpeedManager obtained from CoreModule", name);

                // Register death listener for XP penalty
                PlayerDeathBroadcaster deathBroadcaster = core.getDeathBroadcaster();
                deathListener = this::onPlayerDeath;
                deathBroadcaster.addListener(deathListener);
                logger.at(Level.FINE).log("[%s] Death penalty listener registered with CoreModule", name);
            });

        // Register HUD elements with HudModule
        logger.at(Level.FINE).log("[%s] Registering HUD elements...", name);
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
            hudModule.setTimedBuffManager(timedBuffManager);
        } else {
            logger.at(Level.WARNING).log("[%s] HUD module not found, HUD elements not registered", name);
        }

        // Try to integrate with metabolism module (optional, for metabolism-affecting abilities)
        context.moduleManager().getModule("metabolism", MetabolismModule.class)
            .ifPresent(metabolism -> {
                system.setMetabolismModule(metabolism);
                // Wire metabolism system to ability managers for metabolism effects
                var metabolismSystem = metabolism.getSystem();
                timedBuffManager.setMetabolismSystem(metabolismSystem);
                abilitySystem.setMetabolismSystem(metabolismSystem);
                // Wire permanent buff manager to metabolism system for Survivalist ability
                metabolismSystem.setPermanentBuffManager(permanentBuffManager);
                logger.at(Level.FINE).log("[%s] Metabolism integration enabled (including Survivalist ability)", name);
            });

        // Try to integrate with notification module (optional, for ability unlock notifications)
        context.moduleManager().getModule(NotificationModule.ID, NotificationModule.class)
            .ifPresent(notifications -> {
                this.notificationModule = notifications;
                abilitySystem.setNotificationModule(notifications);
                timedBuffManager.setNotificationModule(notifications);
                permanentBuffManager.setNotificationModule(notifications);
                logger.at(Level.FINE).log("[%s] Notification integration enabled", name);
            });

        // Register player lifecycle listener
        var playerListener = new com.livinglands.modules.leveling.listeners.LevelingPlayerListener(
            this, system, logger
        );
        playerListener.setAbilitySystem(abilitySystem);
        playerListener.register(context.eventRegistry());

        // Register XP listeners for all professions
        registerXpListeners();

        // Register ability handlers for passive abilities
        registerAbilityHandlers();

        // Register commands
        registerCommands();

        logger.at(Level.FINE).log("[%s] Module setup complete", name);
    }

    private void registerXpListeners() {
        var entityStoreRegistry = context.entityStoreRegistry();
        var playerRegistry = context.playerRegistry();

        // Block placement tracking system (tracks player-placed blocks)
        entityStoreRegistry.registerSystem(
            new com.livinglands.modules.leveling.listeners.BlockPlaceTrackingSystem(logger)
        );

        // Mining XP system (ECS - responds to BreakBlockEvent for ores)
        // Wire with MiningAbilityHandler for Tier 2 abilities
        var miningAbilityHandler = new com.livinglands.modules.leveling.ability.handlers.MiningAbilityHandler(
            abilitySystem, playerRegistry, logger
        );
        var miningXpSystem = new com.livinglands.modules.leveling.listeners.MiningXpSystem(system, config, logger);
        miningXpSystem.setAbilityHandler(miningAbilityHandler);
        entityStoreRegistry.registerSystem(miningXpSystem);

        // Logging XP system (ECS - responds to BreakBlockEvent for logs)
        // Wire with LoggingAbilityHandler for Tier 2 abilities
        var loggingAbilityHandler = new com.livinglands.modules.leveling.ability.handlers.LoggingAbilityHandler(
            abilitySystem, playerRegistry, logger
        );
        var loggingXpSystem = new com.livinglands.modules.leveling.listeners.LoggingXpSystem(system, config, logger);
        loggingXpSystem.setAbilityHandler(loggingAbilityHandler);
        entityStoreRegistry.registerSystem(loggingXpSystem);

        // Building XP system (ECS - responds to PlaceBlockEvent)
        // Wire with BuildingAbilityHandler for Tier 2 abilities
        var buildingAbilityHandler = new com.livinglands.modules.leveling.ability.handlers.BuildingAbilityHandler(
            abilitySystem, playerRegistry, logger
        );
        var buildingXpSystem = new com.livinglands.modules.leveling.listeners.BuildingXpSystem(system, config, logger);
        buildingXpSystem.setAbilityHandler(buildingAbilityHandler);
        entityStoreRegistry.registerSystem(buildingXpSystem);

        // Gathering XP system (ECS - responds to InteractivelyPickupItemEvent)
        // Wire with GatheringAbilityHandler for Tier 2 abilities
        var gatheringAbilityHandler = new com.livinglands.modules.leveling.ability.handlers.GatheringAbilityHandler(
            abilitySystem, playerRegistry, logger
        );
        var gatheringXpSystem = new com.livinglands.modules.leveling.listeners.GatheringXpSystem(system, config, logger);
        gatheringXpSystem.setAbilityHandler(gatheringAbilityHandler);
        entityStoreRegistry.registerSystem(gatheringXpSystem);

        // Combat XP system (ECS - responds to KillFeedEvent)
        // Create CombatAbilityHandler first so we can wire it to CombatXpSystem
        var combatAbilityHandler = new com.livinglands.modules.leveling.ability.handlers.CombatAbilityHandler(
            abilitySystem, playerRegistry, logger
        );
        var combatXpSystem = new com.livinglands.modules.leveling.listeners.CombatXpSystem(system, config, logger);
        combatXpSystem.setAbilityHandler(combatAbilityHandler);
        entityStoreRegistry.registerSystem(combatXpSystem);

        logger.at(Level.FINE).log("[%s] Registered all profession XP systems (ECS)", name);
    }

    private void registerAbilityHandlers() {
        // All ability handlers are now wired directly to their respective XP systems
        // in registerXpListeners() - this ensures abilities are triggered when XP is awarded.
        //
        // Handler wiring:
        // - CombatAbilityHandler -> CombatXpSystem (kills)
        // - MiningAbilityHandler -> MiningXpSystem (ore breaks)
        // - LoggingAbilityHandler -> LoggingXpSystem (wood breaks)
        // - BuildingAbilityHandler -> BuildingXpSystem (block placements)
        // - GatheringAbilityHandler -> GatheringXpSystem (item pickups)
        //
        // Tier 1 abilities (XP boosts) are handled in LevelingSystem.awardXp()
        // Tier 2 abilities (triggered effects) are handled by the ability handlers
        // Tier 3 abilities (permanent buffs) are handled by PermanentBuffManager

        logger.at(Level.FINE).log("[%s] Ability handlers wired to XP systems", name);
    }

    private void registerCommands() {
        var commandRegistry = context.commandRegistry();

        // /setlevel command (admin)
        commandRegistry.registerCommand(
            new com.livinglands.modules.leveling.commands.SetLevelCommand(system)
        );

        logger.at(Level.FINE).log("[%s] Registered commands: /setlevel", name);
    }

    @Override
    protected void onStart() {
        // Start the leveling system background tasks
        system.start();

        // Start the timed buff manager tick loop
        if (timedBuffManager != null) {
            timedBuffManager.start();
        }

        // Set up UI callbacks
        setupUiCallbacks();

        logger.at(Level.FINE).log("[%s] Module started", name);
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

        // Death penalty notification callback - send chat messages
        system.setDeathPenaltyCallback((playerId, event) -> {
            if (notificationModule == null) return;

            // Send header message
            notificationModule.sendChatHex(playerId, "[Death Penalty] You lost XP in the following professions:", "#F85149");

            // Send details for each affected profession
            for (var penalty : event.penalties()) {
                String msg = String.format("  - %s (Lv.%d): -%d XP (%.0f%% -> %.0f%%)",
                    penalty.profession().getDisplayName(),
                    penalty.level(),
                    penalty.xpLost(),
                    calculateProgressPercent(penalty.xpBefore(), penalty.profession(), penalty.level()),
                    calculateProgressPercent(penalty.xpAfter(), penalty.profession(), penalty.level())
                );
                notificationModule.sendChatHex(playerId, msg, "#FF9999");
            }
        });
    }

    /**
     * Calculate progress percentage towards next level.
     */
    private float calculateProgressPercent(long currentXp, com.livinglands.modules.leveling.profession.ProfessionType profession, int level) {
        long xpToNextLevel = xpCalculator.getXpToNextLevel(level);
        if (xpToNextLevel <= 0) return 100f;
        return (currentXp * 100f) / xpToNextLevel;
    }

    /**
     * Handle player death - apply XP penalty and save immediately.
     */
    private void onPlayerDeath(UUID playerId) {
        logger.at(Level.FINE).log("[%s] onPlayerDeath called for player %s - applying death penalty", name, playerId);

        // Apply the death penalty
        system.applyDeathPenalty(playerId);

        // Force immediate save to persist the XP loss
        var dataOpt = system.getPlayerData(playerId);
        dataOpt.ifPresent(data -> {
            persistence.save(data);
            logger.at(Level.FINE).log("[%s] Saved leveling data for player %s after death penalty", name, playerId);
        });
    }

    @Override
    protected void onShutdown() {
        // Remove death listener
        if (deathListener != null) {
            context.moduleManager().getModule(CoreModule.ID, CoreModule.class)
                .ifPresent(core -> core.getDeathBroadcaster().removeListener(deathListener));
        }

        // Save placed block tracking data
        logger.at(Level.FINE).log("[%s] Saving placed block tracking data...", name);
        PlayerPlacedBlockChecker.saveAll();
        PlayerPlacedBlockChecker.clearAll();

        // Stop the timed buff manager
        if (timedBuffManager != null) {
            timedBuffManager.shutdown();
        }

        // Stop the system and save all data
        if (system != null) {
            system.stop();
        }

        logger.at(Level.FINE).log("[%s] Module shutdown complete", name);
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

    @Override
    public com.livinglands.api.ModuleUIProvider getUIProvider() {
        if (system != null && config != null) {
            return new com.livinglands.modules.leveling.ui.LevelingUIProvider(system, config, logger);
        }
        return null;
    }
}
