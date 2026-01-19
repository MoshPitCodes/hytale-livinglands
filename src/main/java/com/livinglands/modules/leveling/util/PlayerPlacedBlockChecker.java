package com.livinglands.modules.leveling.util;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.interaction.components.PlacedByInteractionComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility to check if a block was placed by a player.
 * Used by XP systems to prevent XP rewards for breaking player-built structures.
 *
 * Uses a hybrid approach:
 * 1. First checks Hytale's PlacedByInteractionComponent (if available)
 * 2. Falls back to in-memory tracking of placed blocks
 */
public final class PlayerPlacedBlockChecker {

    // In-memory tracking of player-placed blocks (world:x:y:z -> placer UUID)
    // Key format: "worldId:x:y:z"
    private static final Set<String> placedBlockPositions = ConcurrentHashMap.newKeySet();

    // Maximum tracked blocks per world (to prevent memory issues)
    private static final int MAX_TRACKED_BLOCKS = 100_000;

    private PlayerPlacedBlockChecker() {}

    /**
     * Record that a block was placed by a player.
     * Called by PlaceBlockEvent listeners.
     *
     * @param worldId The world identifier
     * @param blockPosition The world position of the block
     */
    public static void recordBlockPlaced(@Nonnull String worldId,
                                          @Nonnull Vector3i blockPosition) {
        // Cleanup if too many tracked blocks
        if (placedBlockPositions.size() >= MAX_TRACKED_BLOCKS) {
            // Clear half of the oldest entries (simple cleanup strategy)
            int toRemove = MAX_TRACKED_BLOCKS / 2;
            var iterator = placedBlockPositions.iterator();
            while (iterator.hasNext() && toRemove > 0) {
                iterator.next();
                iterator.remove();
                toRemove--;
            }
        }

        String key = makeKey(worldId, blockPosition);
        placedBlockPositions.add(key);
    }

    /**
     * Record that a block was broken (remove from tracking).
     * Called by BreakBlockEvent listeners.
     *
     * @param worldId The world identifier
     * @param blockPosition The world position of the block
     */
    public static void recordBlockBroken(@Nonnull String worldId,
                                          @Nonnull Vector3i blockPosition) {
        String key = makeKey(worldId, blockPosition);
        placedBlockPositions.remove(key);
    }

    /**
     * Check if a block at the given position was placed by a player.
     *
     * @param store The entity store
     * @param blockPosition The world position of the block
     * @return true if the block was placed by a player, false if naturally generated or unknown
     */
    public static boolean isPlayerPlaced(@Nonnull Store<EntityStore> store,
                                          @Nonnull Vector3i blockPosition) {
        try {
            // Get the world from the store
            World world = store.getExternalData().getWorld();
            if (world == null) {
                return false; // Can't determine, assume natural
            }

            return isPlayerPlaced(world, blockPosition);
        } catch (Exception e) {
            // If we can't determine, assume natural (don't penalize player)
            return false;
        }
    }

    /**
     * Check if a block at the given position was placed by a player.
     *
     * @param world The world
     * @param blockPosition The world position of the block
     * @return true if the block was placed by a player, false if naturally generated or unknown
     */
    public static boolean isPlayerPlaced(@Nonnull World world,
                                          @Nonnull Vector3i blockPosition) {
        try {
            // First check in-memory tracking
            String worldId = world.getName();
            String key = makeKey(worldId, blockPosition);
            if (placedBlockPositions.contains(key)) {
                return true;
            }

            // Fall back to checking PlacedByInteractionComponent
            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.getX(), blockPosition.getZ());

            WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
            if (chunk == null) {
                return false; // Chunk not loaded, assume natural
            }

            int localX = ChunkUtil.localCoordinate(blockPosition.getX());
            int localY = blockPosition.getY();
            int localZ = ChunkUtil.localCoordinate(blockPosition.getZ());

            Holder<ChunkStore> blockHolder = chunk.getBlockComponentHolder(localX, localY, localZ);
            if (blockHolder == null) {
                return false; // No block components, definitely not player-placed
            }

            var placedByComponent = blockHolder.getComponent(
                PlacedByInteractionComponent.getComponentType()
            );

            return placedByComponent != null && placedByComponent.getWhoPlacedUuid() != null;

        } catch (Exception e) {
            // If we can't determine, assume natural (don't penalize player)
            return false;
        }
    }

    /**
     * Get the UUID of the player who placed a block, if any.
     *
     * @param world The world
     * @param blockPosition The world position of the block
     * @return The UUID of the player who placed the block, or null if naturally generated
     */
    @Nullable
    public static UUID getPlacedByPlayer(@Nonnull World world,
                                          @Nonnull Vector3i blockPosition) {
        try {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.getX(), blockPosition.getZ());
            WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
            if (chunk == null) {
                return null;
            }

            int localX = ChunkUtil.localCoordinate(blockPosition.getX());
            int localY = blockPosition.getY();
            int localZ = ChunkUtil.localCoordinate(blockPosition.getZ());

            Holder<ChunkStore> blockHolder = chunk.getBlockComponentHolder(localX, localY, localZ);
            if (blockHolder == null) {
                return null;
            }

            var placedByComponent = blockHolder.getComponent(
                PlacedByInteractionComponent.getComponentType()
            );

            return placedByComponent != null ? placedByComponent.getWhoPlacedUuid() : null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Clear all tracked blocks. Called on server shutdown.
     */
    public static void clearAll() {
        placedBlockPositions.clear();
    }

    /**
     * Get the number of tracked placed blocks.
     */
    public static int getTrackedCount() {
        return placedBlockPositions.size();
    }

    private static String makeKey(String worldId, Vector3i pos) {
        return worldId + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }
}
