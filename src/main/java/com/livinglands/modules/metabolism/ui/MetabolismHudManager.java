package com.livinglands.modules.metabolism.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.PlayerSession;
import com.livinglands.modules.metabolism.MetabolismSystem;
import com.livinglands.modules.metabolism.config.MetabolismModuleConfig;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages metabolism HUD display for players.
 * Creates and updates custom stat bars showing hunger/thirst/energy levels.
 *
 * Uses Hytale's CustomUIHud system for native-looking stat bars
 * positioned in the top-left corner of the screen.
 */
public class MetabolismHudManager {

    // Update frequency for HUD display (more frequent for smooth bar updates)
    private static final long HUD_UPDATE_INTERVAL_MS = 1000L; // Every 1 second

    private final MetabolismSystem metabolismSystem;
    private final PlayerRegistry playerRegistry;
    private final MetabolismModuleConfig config;
    private final HytaleLogger logger;

    private final ScheduledExecutorService executor;
    private final Map<UUID, MetabolismStatsHud> playerHuds = new ConcurrentHashMap<>();

    private ScheduledFuture<?> updateTask;
    private volatile boolean running = false;

    public MetabolismHudManager(@Nonnull MetabolismSystem metabolismSystem,
                                 @Nonnull PlayerRegistry playerRegistry,
                                 @Nonnull MetabolismModuleConfig config,
                                 @Nonnull HytaleLogger logger) {
        this.metabolismSystem = metabolismSystem;
        this.playerRegistry = playerRegistry;
        this.config = config;
        this.logger = logger;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "LivingLands-HUD");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts the HUD update loop.
     */
    public void start() {
        if (running) {
            return;
        }

        updateTask = executor.scheduleAtFixedRate(
            this::updateAllPlayers,
            HUD_UPDATE_INTERVAL_MS,
            HUD_UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        running = true;
        logger.at(Level.INFO).log("Metabolism HUD manager started (update interval: %dms)", HUD_UPDATE_INTERVAL_MS);
    }

    /**
     * Stops the HUD update loop.
     */
    public void stop() {
        if (!running) {
            return;
        }

        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        playerHuds.clear();
        running = false;
    }

    /**
     * Initializes and shows the HUD for a player.
     * Should be called when player's ECS is ready.
     *
     * @param playerId The player's UUID
     * @param session The player's session with ECS access
     */
    public void initializePlayerHud(@Nonnull UUID playerId, @Nonnull PlayerSession session) {
        try {
            if (!session.isEcsReady()) {
                return;
            }

            var playerRef = session.getPlayerRef();
            if (playerRef == null) {
                logger.at(Level.FINE).log("No PlayerRef available for player %s", playerId);
                return;
            }

            var entityRef = session.getEntityRef();
            var store = session.getStore();
            if (entityRef == null || store == null) {
                return;
            }

            var metabolism = config.metabolism;

            // Get initial stat values
            var dataOpt = metabolismSystem.getPlayerData(playerId);
            double initialHunger = dataOpt.map(d -> d.getHunger()).orElse(100.0);
            double initialThirst = dataOpt.map(d -> d.getThirst()).orElse(100.0);
            double initialEnergy = dataOpt.map(d -> d.getEnergy()).orElse(100.0);

            // Execute on world thread for ECS access
            session.executeOnWorld(() -> {
                try {
                    // Get Player component to access HudManager
                    var player = store.getComponent(entityRef, Player.getComponentType());
                    if (player == null) {
                        return;
                    }

                    var playerHudManager = player.getHudManager();
                    if (playerHudManager == null) {
                        return;
                    }

                    // Create custom HUD with initial values
                    var hud = new MetabolismStatsHud(
                        playerRef,
                        metabolism.enableHunger,
                        metabolism.enableThirst,
                        metabolism.enableEnergy,
                        initialHunger,
                        initialThirst,
                        initialEnergy
                    );

                    // Register HUD with player's HudManager
                    playerHudManager.setCustomHud(playerRef, hud);

                    // Store for updates
                    playerHuds.put(playerId, hud);

                    // Show the HUD
                    hud.show();

                    logger.at(Level.INFO).log("Initialized metabolism HUD for player %s", playerId);

                } catch (Exception e) {
                    logger.at(Level.FINE).withCause(e).log("Error initializing HUD on world thread for player %s", playerId);
                }
            });

        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("Error setting up HUD for player %s", playerId);
        }
    }

    /**
     * Updates HUD for all online players.
     */
    private void updateAllPlayers() {
        try {
            for (var entry : playerHuds.entrySet()) {
                var playerId = entry.getKey();
                var hud = entry.getValue();

                updatePlayerHud(playerId, hud);
            }
        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("Error updating HUD for players");
        }
    }

    /**
     * Updates HUD for a single player.
     */
    private void updatePlayerHud(UUID playerId, MetabolismStatsHud hud) {
        try {
            var dataOpt = metabolismSystem.getPlayerData(playerId);
            if (dataOpt.isEmpty()) {
                return;
            }

            // Update the HUD with current values
            hud.updateStats(dataOpt.get());

        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("Error updating HUD for player %s", playerId);
        }
    }

    /**
     * Cleans up HUD for a player.
     */
    public void removePlayer(UUID playerId) {
        var hud = playerHuds.remove(playerId);
        if (hud != null) {
            // HUD will be cleaned up automatically when player disconnects
            logger.at(Level.FINE).log("Removed HUD for player %s", playerId);
        }
    }
}
