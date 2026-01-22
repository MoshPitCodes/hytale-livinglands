package com.livinglands.modules.claims;

import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.livinglands.api.AbstractModule;
import com.livinglands.api.ModuleUIProvider;
import com.livinglands.core.CoreModule;
import com.livinglands.core.hud.HudModule;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import com.livinglands.modules.claims.listeners.ClaimNPCBurnSystem;
import com.livinglands.modules.claims.listeners.ClaimProtectionListener;
import com.livinglands.modules.claims.listeners.ClaimsPlayerListener;
import com.livinglands.modules.claims.map.ClaimMarkerProvider;
import com.livinglands.modules.claims.map.ClaimsMapUpdateSystem;
import com.livinglands.modules.claims.map.ClaimsWorldMapProvider;
import com.livinglands.modules.claims.ui.ClaimsUIProvider;
import com.livinglands.core.notifications.NotificationModule;
import com.livinglands.modules.leveling.LevelingModule;
import com.livinglands.modules.leveling.LevelingSystem;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Plot/Land Claims module for Living Lands.
 *
 * Provides chunk-based land protection and ownership features:
 * - Multi-chunk rectangular plot claims (e.g., 3x3, 5x8)
 * - Permission tiers: Owner, Trusted, Accessor
 * - Per-player configurable claim limits with leveling integration
 * - Protection for blocks, containers, and interactions
 * - Map visualization with colored corner markers
 * - Admin priority claims
 */
public final class ClaimsModule extends AbstractModule {

    public static final String ID = "claims";
    public static final String NAME = "Land Claims";
    public static final String VERSION = "1.0.0";

    private ClaimsModuleConfig config;
    private ClaimDataPersistence persistence;
    private ClaimSystem system;
    private ClaimProtectionListener protectionListener;
    private ClaimNPCBurnSystem npcBurnSystem;
    private ClaimMarkerProvider markerProvider;
    private ClaimsUIProvider uiProvider;
    private ClaimsPlayerListener playerListener;

    private ScheduledExecutorService scheduler;

    public ClaimsModule() {
        super(ID, NAME, VERSION, Set.of(CoreModule.ID, HudModule.ID)); // Depends on Core and HUD modules
    }

    @Override
    protected void onSetup() {
        // Load module configuration
        config = loadConfig("config.json", ClaimsModuleConfig.class, ClaimsModuleConfig::defaults);

        logger.at(Level.FINE).log("[%s] Loaded config: maxPlots=%d, maxChunks=%d, levelingIntegration=%b",
            name, config.maxPlotsPerPlayer, config.maxChunksPerPlot, config.levelingIntegration);

        // Initialize persistence
        persistence = new ClaimDataPersistence(configDirectory, logger);

        // Initialize claim system
        system = new ClaimSystem(config, logger, context.playerRegistry(), persistence);

        // Load existing claims
        system.loadAllClaims();

        // Try to integrate with leveling module (optional)
        if (config.levelingIntegration) {
            context.moduleManager().getModule(LevelingModule.ID, LevelingModule.class)
                .ifPresent(levelingModule -> {
                    LevelingSystem levelingSystem = levelingModule.getSystem();
                    system.setLevelingSystem(levelingSystem);
                    logger.at(Level.FINE).log("[%s] Leveling integration enabled", name);
                });
        }

        // Initialize map marker provider
        markerProvider = new ClaimMarkerProvider(system, config, context.playerRegistry(), logger);

        // Wire marker provider to claim system for callbacks
        system.setMarkerProvider(markerProvider);

        // Set the claim system and config for the world map provider
        // This allows the provider to access these instances when generating map images
        ClaimsWorldMapProvider.setClaimSystemAndConfig(system, config);

        // Register map visualization if enabled
        if (config.showOnMap) {
            // Register the custom world map provider codec
            ClaimsWorldMapProvider.registerCodec();
            logger.at(Level.FINE).log("[%s] Registered world map provider codec: %s", name, ClaimsWorldMapProvider.ID);

            // Register AddWorldEvent listener to set the provider on each world
            context.eventRegistry().registerGlobal(AddWorldEvent.class, event -> {
                var world = event.getWorld();
                var worldConfig = world.getWorldConfig();

                // Skip temporary worlds
                if (worldConfig.isDeleteOnRemove()) {
                    logger.at(Level.FINE).log("[%s] Skipping temporary world: %s", name, world.getName());
                    return;
                }

                // Set our custom claims world map provider
                worldConfig.setWorldMapProvider(new ClaimsWorldMapProvider());
                logger.at(Level.FINE).log("[%s] Set claims world map provider for world: %s", name, world.getName());
            });

            // Register the map update system for handling claim changes
            context.chunkStoreRegistry().registerSystem(new ClaimsMapUpdateSystem());
            logger.at(Level.FINE).log("[%s] Registered map update system", name);
        }

        // Get notification module for centralized messaging
        NotificationModule notificationModule = context.moduleManager()
            .getModule(NotificationModule.ID, NotificationModule.class)
            .orElse(null);

        // Register protection listener
        protectionListener = new ClaimProtectionListener(system, config, notificationModule, logger);
        protectionListener.register(context.entityStoreRegistry());

        // Register NPC burn system (applies fire damage to hostile NPCs in protected claims)
        npcBurnSystem = new ClaimNPCBurnSystem(system, logger);
        npcBurnSystem.register(context.entityStoreRegistry());

        // Initialize UI provider
        uiProvider = new ClaimsUIProvider(system, config, notificationModule, logger);

        // Register player lifecycle listener (handles disconnect cleanup)
        playerListener = new ClaimsPlayerListener(system, logger);
        playerListener.setUIProvider(uiProvider);
        playerListener.register(context.eventRegistry());

        // Register commands
        registerCommands();

        logger.at(Level.FINE).log("[%s] Module setup complete", name);
    }

    private void registerCommands() {
        // Wire claims subcommands to the main /ll command via HudModule
        context.moduleManager().getModule(HudModule.ID, HudModule.class)
            .ifPresent(hudModule -> {
                var llCommand = hudModule.getLivingLandsCommand();
                if (llCommand != null) {
                    llCommand.setClaimsModule(system, config, context.playerRegistry());
                    logger.at(Level.FINE).log("[%s] Claims commands wired to /ll command", name);
                }
            });

        logger.at(Level.FINE).log("[%s] Commands ready (registered via /ll subcommands)", name);
    }

    @Override
    protected void onStart() {
        if (system == null) {
            return;
        }

        // Start background tasks
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LivingLands-Claims");
            t.setDaemon(true);
            return t;
        });

        // Clean up expired previews every 10 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                system.cleanupExpiredPreviews();
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[%s] Error in preview cleanup", name);
            }
        }, 10, 10, TimeUnit.SECONDS);

        // Auto-save claims every 5 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                system.saveAllClaims();
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[%s] Error in auto-save", name);
            }
        }, 5, 5, TimeUnit.MINUTES);

        logger.at(Level.FINE).log("[%s] Module started", name);
    }

    @Override
    protected void onShutdown() {
        // Stop scheduler
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown claim system (saves all claims and waits for pending operations)
        if (system != null) {
            system.shutdown();
        }

        logger.at(Level.FINE).log("[%s] Module shutdown complete", name);
    }

    // === Public Accessors ===

    /**
     * Gets the claim system.
     */
    public ClaimSystem getSystem() {
        return system;
    }

    /**
     * Gets the module configuration.
     */
    public ClaimsModuleConfig getConfig() {
        return config;
    }

    /**
     * Gets the map marker provider.
     */
    public ClaimMarkerProvider getMarkerProvider() {
        return markerProvider;
    }

    /**
     * Checks if the module is enabled and functional.
     */
    public boolean isFunctional() {
        return super.isEnabled() && config != null && system != null;
    }

    @Override
    public ModuleUIProvider getUIProvider() {
        return uiProvider;
    }
}
