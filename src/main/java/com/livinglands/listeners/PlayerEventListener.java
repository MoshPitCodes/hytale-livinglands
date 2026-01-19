package com.livinglands.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.livinglands.LivingLandsPlugin;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.metabolism.MetabolismSystem;
import com.livinglands.ui.MetabolismHudManager;
import com.livinglands.leveling.LevelingModule;

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
public class PlayerEventListener {

    private final LivingLandsPlugin plugin;
    private final PlayerRegistry playerRegistry;
    private final MetabolismSystem metabolismSystem;
    private final MetabolismHudManager hudManager;
    private final LevelingModule levelingModule;

    /**
     * Creates a new player event listener.
     *
     * @param plugin The plugin instance
     */
    public PlayerEventListener(@Nonnull LivingLandsPlugin plugin) {
        this.plugin = plugin;
        this.playerRegistry = plugin.getPlayerRegistry();
        this.metabolismSystem = plugin.getMetabolismSystem();
        this.hudManager = plugin.getHudManager();
        this.levelingModule = plugin.getLevelingModule();
    }

    /**
     * Registers event handlers with the provided event registry.
     *
     * @param eventRegistry The event registry to register handlers with
     */
    public void register(@Nonnull EventRegistry eventRegistry) {
        // Register player connect event handler
        eventRegistry.register(PlayerConnectEvent.class, this::onPlayerConnect);

        // Register player ready event handler - ECS is fully initialized
        // Note: PlayerReadyEvent implements IEvent<String>, so we use registerGlobal
        eventRegistry.registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        // Register player disconnect event handler
        eventRegistry.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // NOTE: Game mode change detection is done by polling in MetabolismSystem
        // The ChangeGameModeEvent is an ECS event and requires different registration

        plugin.getLogger().at(Level.INFO).log("Registered player event listeners (connect, ready, disconnect)");
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

            // Initialize leveling for this player (only if enabled)
            if (levelingModule != null && levelingModule.isEnabled()) {
                levelingModule.initializePlayer(playerId);
            }

            plugin.getLogger().at(Level.INFO).log("Player connected: %s", playerId);

        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log(
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
    @SuppressWarnings("deprecation") // getPlayerRef() is deprecated but no alternative exists yet
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            // PlayerReadyEvent.getPlayerRef() returns Ref<EntityStore> (from PlayerEvent parent)
            var entityRef = event.getPlayerRef();
            var player = event.getPlayer();

            if (entityRef == null || !entityRef.isValid()) {
                plugin.getLogger().at(Level.FINE).log("PlayerReadyEvent has invalid entity ref");
                return;
            }

            var store = entityRef.getStore();
            // Note: Player.getPlayerRef() is deprecated but required for UI operations
            var playerRef = player.getPlayerRef();
            var playerId = playerRef.getUuid();

            // Set ECS references in central PlayerRegistry (including Player for game mode polling)
            playerRegistry.setEcsReferences(playerId, entityRef, store, null, playerRef, player);

            // Initialize HUD for this player
            var sessionOpt = playerRegistry.getSession(playerId);
            if (sessionOpt.isPresent() && hudManager != null) {
                hudManager.initializePlayerHud(playerId, sessionOpt.get());
            }

            // Apply leveling stat modifiers (only if enabled)
            if (levelingModule != null && levelingModule.isEnabled()) {
                levelingModule.applyStatModifiers(playerId);
            }

            plugin.getLogger().at(Level.FINE).log("ECS ready for player: %s", playerId);

        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log(
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

            // Remove HUD tracking
            if (hudManager != null) {
                hudManager.removePlayer(playerId);
            }

            // Remove metabolism tracking
            if (metabolismSystem != null) {
                metabolismSystem.removePlayer(playerId);
            }

            // Remove leveling tracking (only if enabled)
            if (levelingModule != null && levelingModule.isEnabled()) {
                levelingModule.removePlayer(playerId);
            }

            // Unregister from central PlayerRegistry
            playerRegistry.unregisterPlayer(playerId);

            plugin.getLogger().at(Level.INFO).log("Player disconnected: %s", playerId);

        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log(
                "Failed to handle player disconnect"
            );
        }
    }
}
