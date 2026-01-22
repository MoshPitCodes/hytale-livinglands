package com.livinglands.modules.claims.map;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Ticking system that handles map updates when claims change.
 *
 * <p>When a claim is created, deleted, or modified, this system:
 * <ol>
 *   <li>Clears the affected chunks from the map cache</li>
 *   <li>Clears the chunks from all players' map trackers</li>
 *   <li>Forces map regeneration with the new claim colors</li>
 * </ol>
 * </p>
 *
 * <p>This runs every 3 seconds to batch updates and avoid excessive regeneration.</p>
 */
public class ClaimsMapUpdateSystem extends DelayedSystem<ChunkStore> {

    private static final Logger LOGGER = Logger.getLogger("com.livinglands.modules.claims.map");

    /**
     * Queue of chunk indices that need map updates, organized by world name.
     */
    private static final Map<String, LongSet> updateQueue = new ConcurrentHashMap<>();

    /**
     * Set of world names that have pending updates.
     */
    private static final Set<String> worldsNeedingUpdates = ConcurrentHashMap.newKeySet();

    public ClaimsMapUpdateSystem() {
        // Run every 3 seconds
        super(3.0f);
    }

    @Override
    public void delayedTick(float deltaTime, int tickCount, @Nonnull Store<ChunkStore> store) {
        ChunkStore chunkStore = store.getExternalData();
        World world = chunkStore.getWorld();
        String worldName = world.getName();

        // Check if this world has pending updates
        if (!updateQueue.containsKey(worldName)) {
            return;
        }

        LongSet chunksToUpdate = updateQueue.get(worldName);
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return;
        }

        // Copy and clear the queue before processing
        LongSet chunksToProcess = new LongOpenHashSet(chunksToUpdate);
        updateQueue.remove(worldName);
        worldsNeedingUpdates.remove(worldName);

        LOGGER.fine("[ClaimsMapUpdateSystem] Processing " + chunksToProcess.size() + " chunks for map update in world: " + worldName);

        // Process the updates
        world.execute(() -> {
            // Clear the map cache for these chunks
            world.getWorldMapManager().clearImagesInChunks(chunksToProcess);

            // Clear chunks from all players' map trackers
            int playerCount = 0;
            for (Player player : world.getPlayers()) {
                if (player != null && player.getWorldMapTracker() != null) {
                    player.getWorldMapTracker().clearChunks(chunksToProcess);
                    playerCount++;
                }
            }
            LOGGER.fine("[ClaimsMapUpdateSystem] Cleared map cache and trackers for " + playerCount + " players");
        });
    }

    /**
     * Queues a chunk for map update.
     *
     * @param worldName  The world name
     * @param chunkIndex The chunk index
     */
    public static void queueChunkUpdate(@Nonnull String worldName, long chunkIndex) {
        updateQueue.computeIfAbsent(worldName, k -> new LongOpenHashSet()).add(chunkIndex);
        worldsNeedingUpdates.add(worldName);
        LOGGER.fine("[ClaimsMapUpdateSystem] Queued chunk " + chunkIndex + " for map update in world: " + worldName);
    }

    /**
     * Queues multiple chunks for map update.
     *
     * @param worldName    The world name
     * @param chunkIndices The chunk indices
     */
    public static void queueChunksUpdate(@Nonnull String worldName, @Nonnull LongSet chunkIndices) {
        updateQueue.computeIfAbsent(worldName, k -> new LongOpenHashSet()).addAll(chunkIndices);
        worldsNeedingUpdates.add(worldName);
    }

    /**
     * Checks if there are pending updates for a world.
     */
    public static boolean hasPendingUpdates(@Nonnull String worldName) {
        return worldsNeedingUpdates.contains(worldName);
    }

    /**
     * Gets the update queue (for testing/debugging).
     */
    public static Map<String, LongSet> getUpdateQueue() {
        return updateQueue;
    }
}
