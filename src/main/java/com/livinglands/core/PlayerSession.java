package com.livinglands.core;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
    private volatile Player player; // Player entity reference for game mode polling

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
     * Thread-safety: Sets references first, then the ready flag last to ensure
     * proper memory visibility via volatile write semantics.
     *
     * @param entityRef The player's entity reference
     * @param store The entity store
     * @param world The world the player is in
     * @param playerRef The player reference for UI/network operations
     * @param player The Player entity for game mode polling
     */
    public void setEcsReferences(@Nullable Ref<EntityStore> entityRef,
                                  @Nullable Store<EntityStore> store,
                                  @Nullable World world,
                                  @Nullable PlayerRef playerRef,
                                  @Nullable Player player) {
        // Set references first
        this.entityRef = entityRef;
        this.store = store;
        this.world = world;
        this.playerRef = playerRef;
        this.player = player;

        // Set ready flag LAST - volatile write creates happens-before relationship
        // This ensures all reference writes are visible before ecsReady becomes true
        this.ecsReady = (entityRef != null && entityRef.isValid() && store != null && world != null);
    }

    /**
     * Checks if ECS references are available and valid.
     *
     * Thread-safety: Only checks the volatile boolean flag. The flag is set based on
     * validation at the time of initialization. Callers should still perform null checks
     * on retrieved references as they may become invalid after this check returns.
     *
     * Note: This method deliberately does NOT re-check entityRef.isValid() to avoid
     * race conditions where the flag is true but the ref becomes invalid between check and use.
     * The volatile read provides visibility of all writes that happened before the flag was set.
     */
    public boolean isEcsReady() {
        return ecsReady;
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
     * Gets the player entity for direct access.
     */
    @Nullable
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the player's current game mode.
     * Polls directly from the Player entity for up-to-date value.
     */
    @Nonnull
    public GameMode getGameMode() {
        if (player != null) {
            try {
                return player.getGameMode();
            } catch (Exception e) {
                // Fall back to Adventure mode if polling fails
            }
        }
        return GameMode.Adventure;
    }

    /**
     * Checks if the player is in Creative mode.
     * Polls directly from the Player entity for up-to-date value.
     */
    public boolean isCreativeMode() {
        return getGameMode() == GameMode.Creative;
    }

    /**
     * Checks if the player is in Adventure (Survival) mode.
     */
    public boolean isSurvivalMode() {
        return getGameMode() == GameMode.Adventure;
    }

    /**
     * Executes an action on the WorldThread if ECS is ready.
     * This is the preferred way to perform ECS operations.
     *
     * IMPORTANT: This method dispatches the action asynchronously and returns immediately.
     * The action will be executed on the WorldThread at some point in the future.
     * There is NO guarantee about when or if the action completes successfully.
     *
     * Thread-safety: The action is queued on the WorldThread. If the world becomes
     * invalid between dispatch and execution, the action may fail or be dropped.
     * Actions should handle null references and invalid state gracefully.
     *
     * @param action The action to execute on the WorldThread (executed asynchronously)
     * @return true if the action was dispatched, false if ECS not ready
     */
    public boolean executeOnWorld(@Nonnull Runnable action) {
        if (!isEcsReady()) {
            return false;
        }
        World worldSnapshot = this.world;
        if (worldSnapshot == null) {
            return false;
        }
        worldSnapshot.execute(action);
        return true;
    }

    /**
     * Invalidates the session. Called on disconnect.
     *
     * Thread-safety: Sets the ready flag to false FIRST before nulling references.
     * The volatile write to ecsReady creates a happens-before relationship, ensuring
     * that subsequent reads of ecsReady will see all the null assignments.
     *
     * This prevents other threads from seeing ecsReady=true with null references.
     */
    public void invalidate() {
        // Set ready flag to false FIRST - volatile write creates happens-before relationship
        this.ecsReady = false;

        // Now null out all references - these writes happen-before subsequent reads of ecsReady
        this.entityRef = null;
        this.store = null;
        this.world = null;
        this.playerRef = null;
        this.player = null;
    }
}
