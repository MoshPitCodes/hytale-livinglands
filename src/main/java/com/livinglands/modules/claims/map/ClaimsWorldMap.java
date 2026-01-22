package com.livinglands.modules.claims.map;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.map.WorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapSettings;
import com.hypixel.hytale.server.core.universe.world.worldmap.provider.chunk.ChunkWorldMap;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Custom world map implementation that renders terrain with claim overlays.
 *
 * <p>For each chunk, this generates a map image that includes:
 * <ul>
 *   <li>The normal terrain rendering (blocks, fluids, shading)</li>
 *   <li>A semi-transparent color overlay for claimed chunks</li>
 * </ul>
 * </p>
 *
 * <p>This uses the same approach as SimpleClaims: re-rendering the terrain
 * with claim colors applied during the image generation phase.</p>
 */
public class ClaimsWorldMap implements IWorldMap {

    private static final Logger LOGGER = Logger.getLogger("com.livinglands.modules.claims.map");

    private final ClaimSystem claimSystem;
    private final ClaimsModuleConfig config;

    /**
     * Creates a new claims world map.
     *
     * @param claimSystem The claim system for checking ownership
     * @param config      The claims module configuration
     */
    public ClaimsWorldMap(@Nullable ClaimSystem claimSystem, @Nullable ClaimsModuleConfig config) {
        this.claimSystem = claimSystem;
        this.config = config;
        LOGGER.log(Level.FINE, "[ClaimsWorldMap] Created new instance - claimSystem: {0}, config: {1}",
            new Object[]{claimSystem != null, config != null});
    }

    @Override
    @Nonnull
    public WorldMapSettings getWorldMapSettings() {
        // Use the same settings as the default chunk world map
        return ChunkWorldMap.INSTANCE.getWorldMapSettings();
    }

    @Override
    @Nonnull
    public CompletableFuture<WorldMap> generate(@Nonnull World world, int imageWidth, int imageHeight,
                                                 @Nonnull LongSet chunkIndices) {
        // First, generate base terrain images using the default ChunkWorldMap
        return ChunkWorldMap.INSTANCE.generate(world, imageWidth, imageHeight, chunkIndices)
            .thenApply(baseWorldMap -> {
                // Now apply claim overlays to each base image (synchronously)
                WorldMap worldMap = new WorldMap(chunkIndices.size());

                int claimedCount = 0;
                for (long chunkIndex : chunkIndices) {
                    // Get the base terrain image for this chunk
                    MapImage baseImage = baseWorldMap.getChunks().get(chunkIndex);

                    // Build claims overlay on top of base image
                    ClaimsImageBuilder builder = ClaimsImageBuilder.build(
                        chunkIndex, imageWidth, imageHeight, world, baseImage, claimSystem, config
                    );

                    MapImage resultImage = builder.getImage();
                    worldMap.getChunks().put(builder.getIndex(), resultImage);

                    // Check if this was a claimed chunk (image differs from base)
                    if (baseImage != null && resultImage != baseImage) {
                        claimedCount++;
                    }
                }

                if (claimedCount > 0) {
                    LOGGER.fine("[ClaimsWorldMap] Applied overlays to " + claimedCount + " claimed chunks out of " + chunkIndices.size());
                }

                return worldMap;
            });
    }

    @Override
    @Nonnull
    public CompletableFuture<Map<String, MapMarker>> generatePointsOfInterest(@Nonnull World world) {
        // No custom points of interest - return empty map
        return CompletableFuture.completedFuture(Collections.emptyMap());
    }
}
