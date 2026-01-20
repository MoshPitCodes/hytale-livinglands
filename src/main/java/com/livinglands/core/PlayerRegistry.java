package com.livinglands.core;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Central registry for all player sessions.
 *
 * This consolidates player reference management that was previously scattered
 * across multiple systems (DebuffsSystem, listeners, etc.).
 *
 * Responsibilities:
 * - Track all connected players and their sessions
 * - Manage ECS reference lifecycle (Ref, Store, World)
 * - Provide thread-safe access to player data
 * - Handle player connect/disconnect lifecycle
 *
 * Usage:
 * - Call registerPlayer() on PlayerConnectEvent
 * - Call setEcsReferences() on PlayerReadyEvent
 * - Call unregisterPlayer() on PlayerDisconnectEvent
 * - Use getSession() or forEachSession() to access player data
 */
public class PlayerRegistry {

    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final HytaleLogger logger;

    /**
     * Creates a new player registry.
     *
     * @param logger The logger for debug output
     */
    public PlayerRegistry(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Registers a new player session.
     * Should be called on PlayerConnectEvent.
     *
     * @param playerId The player's UUID
     * @return The created session
     */
    @Nonnull
    public PlayerSession registerPlayer(@Nonnull UUID playerId) {
        var session = new PlayerSession(playerId);
        var existing = sessions.putIfAbsent(playerId, session);

        if (existing != null) {
            logger.at(Level.WARNING).log(
                "Player %s already registered, reusing existing session", playerId
            );
            return existing;
        }

        logger.at(Level.FINE).log("Registered player session: %s", playerId);
        return session;
    }

    /**
     * Sets ECS references for a player.
     * Should be called on PlayerReadyEvent when ECS is fully initialized.
     *
     * @param playerId The player's UUID
     * @param entityRef The player's entity reference
     * @param store The entity store
     * @param world The world (can be extracted from store if null)
     * @param playerRef The player reference for UI/network operations
     * @param player The Player entity for game mode polling
     */
    public void setEcsReferences(@Nonnull UUID playerId,
                                  @Nullable Ref<EntityStore> entityRef,
                                  @Nullable Store<EntityStore> store,
                                  @Nullable World world,
                                  @Nullable PlayerRef playerRef,
                                  @Nullable com.hypixel.hytale.server.core.entity.entities.Player player) {
        var session = sessions.get(playerId);
        if (session == null) {
            logger.at(Level.WARNING).log(
                "Cannot set ECS refs for unregistered player: %s", playerId
            );
            return;
        }

        // Try to extract world from store if not provided
        if (world == null && store != null) {
            try {
                var externalData = store.getExternalData();
                if (externalData != null) {
                    world = externalData.getWorld();
                }
            } catch (Exception e) {
                logger.at(Level.FINE).log("Could not extract world from store");
            }
        }

        session.setEcsReferences(entityRef, store, world, playerRef, player);

        if (session.isEcsReady()) {
            logger.at(Level.FINE).log("ECS ready for player: %s", playerId);
        } else {
            logger.at(Level.WARNING).log(
                "ECS refs set but not ready for player: %s (ref=%s, store=%s, world=%s)",
                playerId,
                entityRef != null ? "valid=" + entityRef.isValid() : "null",
                store != null ? "present" : "null",
                world != null ? "present" : "null"
            );
        }
    }

    /**
     * Convenience method to set ECS references, extracting World from Store.
     */
    public void setEcsReferences(@Nonnull UUID playerId,
                                  @Nullable Ref<EntityStore> entityRef,
                                  @Nullable Store<EntityStore> store) {
        setEcsReferences(playerId, entityRef, store, null, null, null);
    }

    /**
     * Unregisters a player session.
     * Should be called on PlayerDisconnectEvent.
     *
     * @param playerId The player's UUID
     */
    public void unregisterPlayer(@Nonnull UUID playerId) {
        var session = sessions.remove(playerId);
        if (session != null) {
            session.invalidate();
            logger.at(Level.FINE).log("Unregistered player session: %s", playerId);
        }
    }

    /**
     * Gets a player's session if it exists.
     *
     * @param playerId The player's UUID
     * @return Optional containing the session, or empty if not found
     */
    @Nonnull
    public Optional<PlayerSession> getSession(@Nonnull UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    /**
     * Gets a player's session, throwing if not found.
     *
     * @param playerId The player's UUID
     * @return The session
     * @throws IllegalStateException if player not registered
     */
    @Nonnull
    public PlayerSession requireSession(@Nonnull UUID playerId) {
        var session = sessions.get(playerId);
        if (session == null) {
            throw new IllegalStateException("Player not registered: " + playerId);
        }
        return session;
    }

    /**
     * Checks if a player is registered.
     *
     * @param playerId The player's UUID
     * @return true if registered
     */
    public boolean isRegistered(@Nonnull UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * Checks if a player's ECS references are ready.
     *
     * @param playerId The player's UUID
     * @return true if ECS is ready
     */
    public boolean isEcsReady(@Nonnull UUID playerId) {
        var session = sessions.get(playerId);
        return session != null && session.isEcsReady();
    }

    /**
     * Executes an action for each registered session.
     *
     * @param action The action to execute
     */
    public void forEachSession(@Nonnull Consumer<PlayerSession> action) {
        sessions.values().forEach(action);
    }

    /**
     * Executes an action for each session with ECS ready.
     *
     * @param action The action to execute
     */
    public void forEachEcsReadySession(@Nonnull Consumer<PlayerSession> action) {
        sessions.values().stream()
            .filter(PlayerSession::isEcsReady)
            .forEach(action);
    }

    /**
     * Gets all registered player IDs.
     *
     * @return Collection of player UUIDs
     */
    @Nonnull
    public Collection<UUID> getRegisteredPlayerIds() {
        return sessions.keySet();
    }

    /**
     * Gets all registered sessions.
     *
     * @return Collection of player sessions
     */
    @Nonnull
    public Collection<PlayerSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * Gets the number of registered players.
     */
    public int getPlayerCount() {
        return sessions.size();
    }

    /**
     * Gets the number of players with ECS ready.
     */
    public int getEcsReadyCount() {
        return (int) sessions.values().stream()
            .filter(PlayerSession::isEcsReady)
            .count();
    }

    /**
     * Clears all sessions. Should only be called on plugin shutdown.
     */
    public void shutdown() {
        sessions.values().forEach(PlayerSession::invalidate);
        sessions.clear();
        logger.at(Level.INFO).log("PlayerRegistry shutdown complete");
    }
}
