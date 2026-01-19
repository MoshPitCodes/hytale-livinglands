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

/**
 * Utility to check if a block was placed by a player.
 * Used by XP systems to prevent XP rewards for breaking player-built structures.
 */
public final class PlayerPlacedBlockChecker {

    private PlayerPlacedBlockChecker() {}

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
            // Calculate chunk index from block position
            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.getX(), blockPosition.getZ());

            // Get the chunk (must be loaded since we're responding to a break event)
            WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
            if (chunk == null) {
                return false; // Chunk not loaded, assume natural
            }

            // Calculate local coordinates within the chunk
            int localX = ChunkUtil.localCoordinate(blockPosition.getX());
            int localY = blockPosition.getY();
            int localZ = ChunkUtil.localCoordinate(blockPosition.getZ());

            // Get the block component holder at this position
            Holder<ChunkStore> blockHolder = chunk.getBlockComponentHolder(localX, localY, localZ);
            if (blockHolder == null) {
                return false; // No block components, definitely not player-placed
            }

            // Check if PlacedByInteractionComponent exists
            var placedByComponent = blockHolder.getComponent(
                PlacedByInteractionComponent.getComponentType()
            );

            // If the component exists and has a UUID, it was player-placed
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
    public static java.util.UUID getPlacedByPlayer(@Nonnull World world,
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
}
