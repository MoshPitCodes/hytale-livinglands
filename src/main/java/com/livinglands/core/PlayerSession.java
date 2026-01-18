package com.livinglands.core;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Holds all runtime data for a single player session.
 *
 * This consolidates player-related data that was previously scattered across
 * multiple systems (DebuffsSystem, listeners, etc.) into a single location.
 *
 * Thread-safety: This class is designed to be accessed from multiple threads.
 * Individual fields are volatile or use thread-safe patterns.
 */
public class PlayerSession {

    private final UUID playerId;
    private final long connectedAt;

    // ECS references - set when PlayerReadyEvent fires
    private volatile Ref<EntityStore> entityRef;
    private volatile Store<EntityStore> store;
    private volatile World world;
    private volatile PlayerRef playerRef;

    // State flags
    private volatile boolean ecsReady = false;

    /**
     * Creates a new player session.
     *
     * @param playerId The player's UUID
     */
    public PlayerSession(@Nonnull UUID playerId) {
        this.playerId = playerId;
        this.connectedAt = System.currentTimeMillis();
    }

    /**
     * Gets the player's UUID.
     */
    @Nonnull
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Gets when the player connected (epoch millis).
     */
    public long getConnectedAt() {
        return connectedAt;
    }

    /**
     * Sets the ECS references for this player.
     * Called when PlayerReadyEvent fires and we have valid ECS access.
     *
     * @param entityRef The player's entity reference
     * @param store The entity store
     * @param world The world the player is in
     * @param playerRef The player reference for UI/network operations
     */
    public void setEcsReferences(@Nullable Ref<EntityStore> entityRef,
                                  @Nullable Store<EntityStore> store,
                                  @Nullable World world,
                                  @Nullable PlayerRef playerRef) {
        this.entityRef = entityRef;
        this.store = store;
        this.world = world;
        this.playerRef = playerRef;
        this.ecsReady = (entityRef != null && entityRef.isValid() && store != null && world != null);
    }

    /**
     * Checks if ECS references are available and valid.
     */
    public boolean isEcsReady() {
        return ecsReady && entityRef != null && entityRef.isValid();
    }

    /**
     * Gets the player's entity reference.
     */
    @Nullable
    public Ref<EntityStore> getEntityRef() {
        return entityRef;
    }

    /**
     * Gets the entity store.
     */
    @Nullable
    public Store<EntityStore> getStore() {
        return store;
    }

    /**
     * Gets the world the player is in.
     */
    @Nullable
    public World getWorld() {
        return world;
    }

    /**
     * Gets the player reference for UI/network operations.
     */
    @Nullable
    public PlayerRef getPlayerRef() {
        return playerRef;
    }

    /**
     * Executes an action on the WorldThread if ECS is ready.
     * This is the preferred way to perform ECS operations.
     *
     * @param action The action to execute on the WorldThread
     * @return true if the action was dispatched, false if ECS not ready
     */
    public boolean executeOnWorld(@Nonnull Runnable action) {
        if (!isEcsReady()) {
            return false;
        }
        world.execute(action);
        return true;
    }

    /**
     * Invalidates the session. Called on disconnect.
     */
    public void invalidate() {
        this.ecsReady = false;
        this.entityRef = null;
        this.store = null;
        this.world = null;
        this.playerRef = null;
    }
}
