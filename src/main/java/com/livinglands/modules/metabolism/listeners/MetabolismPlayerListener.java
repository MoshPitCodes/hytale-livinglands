package com.livinglands.modules.metabolism.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.hud.HudModule;
import com.livinglands.modules.metabolism.MetabolismModule;
import com.livinglands.modules.metabolism.MetabolismSystem;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Event listener for player connection and disconnection events.
 *
 * Handles:
 * - Player connect: Register with PlayerRegistry, initialize metabolism data
 * - Player ready: Set ECS references in PlayerRegistry
 * - Player disconnect: Unregister from PlayerRegistry, clean up metabolism data
 *
 * Uses Consumer-based event registration with Hytale's EventRegistry.
 */
public class MetabolismPlayerListener {

    private final MetabolismModule module;
    private final HytaleLogger logger;
    private PlayerRegistry playerRegistry;
    private MetabolismSystem metabolismSystem;
    private HudModule hudModule;

    /**
     * Creates a new player event listener.
     *
     * @param module The metabolism module instance
     */
    public MetabolismPlayerListener(@Nonnull MetabolismModule module) {
        this.module = module;
        this.logger = module.getContext().logger();
    }

    /**
     * Registers event handlers with the provided event registry.
     *
     * @param eventRegistry The event registry to register handlers with
     */
    public void register(@Nonnull EventRegistry eventRegistry) {
        // Get services from module context
        this.playerRegistry = module.getContext().playerRegistry();
        this.metabolismSystem = module.getSystem();

        // Get HudModule for player HUD initialization
        module.getContext().moduleManager().getModule(HudModule.ID, HudModule.class)
            .ifPresent(hud -> this.hudModule = hud);

        // Register player connect event handler
        eventRegistry.register(PlayerConnectEvent.class, this::onPlayerConnect);

        // Register player ready event handler - ECS is fully initialized
        // Note: PlayerReadyEvent implements IEvent<String>, so we use registerGlobal
        eventRegistry.registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        // Register player disconnect event handler
        eventRegistry.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // NOTE: Game mode change detection is done by polling in MetabolismSystem
        // The ChangeGameModeEvent is an ECS event and requires different registration

        logger.at(Level.FINE).log("[Metabolism] Registered player event listeners");
    }

    /**
     * Handles player connection events.
     * Registers player with PlayerRegistry and initializes metabolism data.
     *
     * @param event The player connect event
     */
    private void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        try {
            var playerRef = event.getPlayerRef();
            var playerId = playerRef.getUuid();

            // Register with central PlayerRegistry
            playerRegistry.registerPlayer(playerId);

            // Initialize metabolism for this player
            if (metabolismSystem != null) {
                metabolismSystem.initializePlayer(playerId);
            }

            logger.at(Level.FINE).log("Player connected: %s", playerId);

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Failed to handle player connect"
            );
        }
    }

    /**
     * Handles player ready events.
     * Sets ECS references in PlayerRegistry once the player entity is fully set up.
     *
     * @param event The player ready event
     */
    @SuppressWarnings("removal") // Player.getPlayerRef() is deprecated for removal but no alternative exists yet
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            // PlayerReadyEvent.getPlayerRef() returns Ref<EntityStore> (from PlayerEvent parent)
            var entityRef = event.getPlayerRef();
            var player = event.getPlayer();

            if (entityRef == null || !entityRef.isValid()) {
                logger.at(Level.FINE).log("PlayerReadyEvent has invalid entity ref");
                return;
            }

            var store = entityRef.getStore();
            // Note: Player.getPlayerRef() is deprecated but required for UI operations
            var playerRef = player.getPlayerRef();
            var playerId = playerRef.getUuid();

            // Set ECS references in central PlayerRegistry (including Player for game mode polling)
            playerRegistry.setEcsReferences(playerId, entityRef, store, null, playerRef, player);

            // Initialize HUD for this player (HudModule handles single combined HUD)
            if (hudModule != null) {
                hudModule.initializePlayer(playerId);
            }

            logger.at(Level.FINE).log("ECS ready for player: %s", playerId);

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Failed to set ECS references in PlayerReadyEvent"
            );
        }
    }

    /**
     * Handles player disconnection events.
     * Unregisters from PlayerRegistry and cleans up metabolism data.
     *
     * @param event The player disconnect event
     */
    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        try {
            var playerRef = event.getPlayerRef();
            var playerId = playerRef.getUuid();

            // Remove HUD tracking (HudModule handles cleanup)
            if (hudModule != null) {
                hudModule.removePlayer(playerId);
            }

            // Remove metabolism tracking
            if (metabolismSystem != null) {
                metabolismSystem.removePlayer(playerId);
            }

            // Unregister from central PlayerRegistry
            playerRegistry.unregisterPlayer(playerId);

            logger.at(Level.FINE).log("Player disconnected: %s", playerId);

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "Failed to handle player disconnect"
            );
        }
    }
}
