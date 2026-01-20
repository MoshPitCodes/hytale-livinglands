package com.livinglands.core.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.event.KillFeedEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Centralized ECS system for broadcasting player death events to multiple listeners.
 *
 * This allows multiple modules (Metabolism, Leveling, etc.) to respond to player deaths
 * without each needing their own ECS system registration.
 *
 * Usage:
 * 1. Register this system with EntityStoreRegistry (done by CoreModule)
 * 2. Call addListener() from other modules to receive death notifications
 */
public class PlayerDeathBroadcaster extends EntityEventSystem<EntityStore, KillFeedEvent.DecedentMessage> {

    private final HytaleLogger logger;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    // Thread-safe list of death event listeners
    private final List<Consumer<UUID>> listeners = new CopyOnWriteArrayList<>();

    public PlayerDeathBroadcaster(@Nonnull HytaleLogger logger) {
        super(KillFeedEvent.DecedentMessage.class);
        this.logger = logger;
        this.playerRefType = PlayerRef.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int entityIndex,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       KillFeedEvent.DecedentMessage event) {
        try {
            // Get player reference from the entity (the one who died)
            PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
            if (playerRef == null) {
                return; // Not a player entity
            }

            UUID playerId = playerRef.getUuid();

            logger.at(Level.FINE).log("PlayerDeathBroadcaster: Player %s died, notifying %d listeners",
                playerId, listeners.size());

            // Notify all listeners
            for (Consumer<UUID> listener : listeners) {
                try {
                    listener.accept(playerId);
                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log(
                        "PlayerDeathBroadcaster: Error in death listener for player %s", playerId);
                }
            }

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("PlayerDeathBroadcaster: Error processing death event");
        }
    }

    /**
     * Add a listener to receive player death notifications.
     *
     * @param listener Consumer that receives the dead player's UUID
     */
    public void addListener(@Nonnull Consumer<UUID> listener) {
        listeners.add(listener);
        logger.at(Level.FINE).log("PlayerDeathBroadcaster: Added listener (total: %d)", listeners.size());
    }

    /**
     * Remove a listener.
     *
     * @param listener The listener to remove
     */
    public void removeListener(@Nonnull Consumer<UUID> listener) {
        listeners.remove(listener);
        logger.at(Level.FINE).log("PlayerDeathBroadcaster: Removed listener (total: %d)", listeners.size());
    }

    /**
     * Clear all listeners (for shutdown).
     */
    public void clearListeners() {
        listeners.clear();
    }
}
