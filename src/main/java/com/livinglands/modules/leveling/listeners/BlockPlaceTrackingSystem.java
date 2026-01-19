package com.livinglands.modules.leveling.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.leveling.util.PlayerPlacedBlockChecker;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * ECS System for tracking when players place blocks.
 * Records placed block positions so they don't award XP when broken.
 */
public class BlockPlaceTrackingSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private final HytaleLogger logger;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    public BlockPlaceTrackingSystem(@Nonnull HytaleLogger logger) {
        super(PlaceBlockEvent.class);
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
                       PlaceBlockEvent event) {
        try {
            // Get player reference from the entity
            PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
            if (playerRef == null) {
                return;
            }

            // Get world ID
            var world = store.getExternalData().getWorld();
            if (world == null) {
                return;
            }

            String worldId = world.getName();
            var blockPosition = event.getTargetBlock();

            // Record this block as player-placed
            PlayerPlacedBlockChecker.recordBlockPlaced(worldId, blockPosition);

            logger.at(Level.INFO).log("Tracked player-placed block at %s in world %s (total tracked: %d)",
                blockPosition, worldId, PlayerPlacedBlockChecker.getTrackedCount());

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error tracking block placement");
        }
    }
}
