package com.livinglands.modules.leveling.util;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.interaction.components.PlacedByInteractionComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Utility to check if a block was placed by a player.
 * Used by XP systems to prevent XP rewards for breaking player-built structures.
 *
 * Uses a hybrid approach:
 * 1. First checks in-memory tracking of placed blocks (persisted to disk)
 * 2. Falls back to Hytale's PlacedByInteractionComponent (if available)
 *
 * Data is persisted per-world to survive server restarts.
 */
public final class PlayerPlacedBlockChecker {

    // In-memory tracking of player-placed blocks per world
    // Map: worldId -> Set of position keys ("x:y:z")
    private static final Map<String, Set<String>> worldPlacedBlocks = new ConcurrentHashMap<>();

    // Persistence handler (initialized by LevelingModule)
    private static PlacedBlockPersistence persistence;
    private static HytaleLogger logger;

    // Maximum tracked blocks per world (to prevent excessive memory/storage)
    private static final int MAX_BLOCKS_PER_WORLD = 500_000;

    // Auto-save interval tracking
    private static long lastAutoSave = System.currentTimeMillis();
    private static final long AUTO_SAVE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private PlayerPlacedBlockChecker() {}

    /**
     * Initialize the checker with persistence support.
     * Called by LevelingModule during setup.
     *
     * @param moduleDirectory The leveling module's data directory
     * @param hytaleLogger Logger for status messages
     */
    public static void initialize(@Nonnull Path moduleDirectory, @Nonnull HytaleLogger hytaleLogger) {
        logger = hytaleLogger;
        persistence = new PlacedBlockPersistence(moduleDirectory, logger);

        // Load persisted data
        var loadedData = persistence.loadAll();
        worldPlacedBlocks.clear();
        worldPlacedBlocks.putAll(loadedData);

        int totalBlocks = worldPlacedBlocks.values().stream().mapToInt(Set::size).sum();
        logger.at(Level.INFO).log("PlayerPlacedBlockChecker initialized with %d tracked blocks across %d worlds",
            totalBlocks, worldPlacedBlocks.size());
    }

    /**
     * Record that a block was placed by a player.
     * Called by PlaceBlockEvent listeners.
     *
     * @param worldId The world identifier
     * @param blockPosition The world position of the block
     */
    public static void recordBlockPlaced(@Nonnull String worldId,
                                          @Nonnull Vector3i blockPosition) {
        var worldSet = worldPlacedBlocks.computeIfAbsent(worldId,
            k -> ConcurrentHashMap.newKeySet());

        // Check if we need to trim this world's data
        if (worldSet.size() >= MAX_BLOCKS_PER_WORLD) {
            trimWorldData(worldId, worldSet);
        }

        String key = makePositionKey(blockPosition);
        worldSet.add(key);

        // Mark world as dirty for persistence
        if (persistence != null) {
            persistence.markDirty(worldId);
        }

        // Check for auto-save
        checkAutoSave();
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
        var worldSet = worldPlacedBlocks.get(worldId);
        if (worldSet != null) {
            String key = makePositionKey(blockPosition);
            if (worldSet.remove(key)) {
                // Mark world as dirty for persistence
                if (persistence != null) {
                    persistence.markDirty(worldId);
                }
            }
        }
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
            var worldSet = worldPlacedBlocks.get(worldId);
            if (worldSet != null) {
                String key = makePositionKey(blockPosition);
                if (worldSet.contains(key)) {
                    return true;
                }
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
     * Save all tracked data to disk.
     * Called on server shutdown and periodically.
     */
    public static void saveAll() {
        if (persistence != null) {
            persistence.saveAll(worldPlacedBlocks);
            lastAutoSave = System.currentTimeMillis();
        }
    }

    /**
     * Save only worlds with changes.
     */
    public static void saveDirty() {
        if (persistence != null && persistence.hasDirtyData()) {
            persistence.saveAllDirty(worldPlacedBlocks);
            lastAutoSave = System.currentTimeMillis();
        }
    }

    /**
     * Clear all tracked blocks. Called on server shutdown after saving.
     */
    public static void clearAll() {
        worldPlacedBlocks.clear();
    }

    /**
     * Get the total number of tracked placed blocks across all worlds.
     */
    public static int getTrackedCount() {
        return worldPlacedBlocks.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Get the number of tracked placed blocks for a specific world.
     */
    public static int getTrackedCount(@Nonnull String worldId) {
        var worldSet = worldPlacedBlocks.get(worldId);
        return worldSet != null ? worldSet.size() : 0;
    }

    /**
     * Get the number of tracked worlds.
     */
    public static int getTrackedWorldCount() {
        return worldPlacedBlocks.size();
    }

    /**
     * Create a position key from block coordinates.
     */
    private static String makePositionKey(Vector3i pos) {
        return pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    /**
     * Trim world data when it exceeds the maximum.
     * Removes approximately 10% of entries (oldest based on set iteration order).
     */
    private static void trimWorldData(String worldId, Set<String> worldSet) {
        int toRemove = worldSet.size() / 10; // Remove 10%
        if (toRemove < 1000) {
            toRemove = 1000; // Minimum removal
        }

        var iterator = worldSet.iterator();
        int removed = 0;
        while (iterator.hasNext() && removed < toRemove) {
            iterator.next();
            iterator.remove();
            removed++;
        }

        if (logger != null) {
            logger.at(Level.WARNING).log(
                "Trimmed %d placed block entries from world %s (was at limit %d)",
                removed, worldId, MAX_BLOCKS_PER_WORLD
            );
        }
    }

    /**
     * Check if we should auto-save dirty data.
     */
    private static void checkAutoSave() {
        long now = System.currentTimeMillis();
        if (now - lastAutoSave >= AUTO_SAVE_INTERVAL_MS) {
            saveDirty();
        }
    }
}
