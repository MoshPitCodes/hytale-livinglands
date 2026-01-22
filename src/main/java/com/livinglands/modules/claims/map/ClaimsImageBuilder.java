package com.livinglands.modules.claims.map;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.universe.world.World;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import com.livinglands.modules.claims.data.PlotClaim;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Logger;

/**
 * Image builder that applies claim color overlays to terrain images.
 *
 * <p>This implementation:
 * <ol>
 *   <li>Accepts a base terrain image (rendered by ChunkWorldMap)</li>
 *   <li>Checks if the chunk is claimed</li>
 *   <li>Applies semi-transparent claim color overlay by tinting pixels</li>
 * </ol>
 * </p>
 *
 * <p>Color Format: RGBA as {@code (r << 24) | (g << 16) | (b << 8) | alpha}</p>
 */
public class ClaimsImageBuilder {

    private static final Logger LOGGER = Logger.getLogger("com.livinglands.modules.claims.map");
    private static final float CLAIM_OVERLAY_ALPHA = 0.85f;  // 85% claim color, 15% terrain

    private final long chunkIndex;
    private final int imageWidth;
    private final int imageHeight;
    private final World world;
    private final ClaimSystem claimSystem;
    private final ClaimsModuleConfig config;

    private MapImage image;

    /**
     * Creates a new claims image builder.
     *
     * @param chunkIndex   The chunk index (packed X/Z coordinates)
     * @param imageWidth   Width of the output image in pixels
     * @param imageHeight  Height of the output image in pixels
     * @param world        The world containing the chunk
     * @param claimSystem  The claim system for checking ownership
     * @param config       The claims module configuration
     */
    private ClaimsImageBuilder(long chunkIndex, int imageWidth, int imageHeight, @Nonnull World world,
                               @Nullable ClaimSystem claimSystem, @Nullable ClaimsModuleConfig config) {
        this.chunkIndex = chunkIndex;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.world = world;
        this.claimSystem = claimSystem;
        this.config = config;
    }

    /**
     * Builds a claims map image for a specific chunk by applying claim overlay to base terrain.
     * This method is synchronous - the overlay is applied immediately.
     *
     * @param chunkIndex    The chunk index
     * @param imageWidth    Image width in pixels
     * @param imageHeight   Image height in pixels
     * @param world         The world
     * @param baseImage     The base terrain image (from ChunkWorldMap)
     * @param claimSystem   The claim system (nullable if claims module disabled)
     * @param config        The claims config (nullable if claims module disabled)
     * @return The built image builder with overlay applied
     */
    @Nonnull
    public static ClaimsImageBuilder build(long chunkIndex, int imageWidth, int imageHeight,
                                           @Nonnull World world,
                                           @Nullable MapImage baseImage,
                                           @Nullable ClaimSystem claimSystem,
                                           @Nullable ClaimsModuleConfig config) {
        var builder = new ClaimsImageBuilder(chunkIndex, imageWidth, imageHeight, world, claimSystem, config);
        builder.applyClaimOverlay(baseImage);
        return builder;
    }

    /**
     * Applies claim color overlay to the base terrain image.
     *
     * <p>If the chunk is claimed, blends the claim color with all terrain pixels.</p>
     */
    private void applyClaimOverlay(@Nullable MapImage baseImage) {
        // If no base image, create a default gray image
        if (baseImage == null) {
            int[] pixels = new int[imageWidth * imageHeight];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = 0x808080FF;  // Gray default (RGBA)
            }
            this.image = new MapImage(imageWidth, imageHeight, pixels);
            return;
        }

        // Check if claims module is available and if this chunk is claimed
        if (claimSystem == null || config == null) {
            LOGGER.fine("[ClaimsImageBuilder] Skipping - claimSystem=" + (claimSystem != null) + ", config=" + (config != null));
            this.image = baseImage;
            return;
        }

        // Get chunk coordinates from index
        // Index format: (chunkX << 32) | (chunkZ & 0xFFFFFFFFL)
        int chunkX = (int) (chunkIndex >> 32);
        int chunkZ = (int) chunkIndex;

        String worldName = world.getName();

        // Check if this chunk is claimed
        PlotClaim claim = claimSystem.getClaimAt(worldName, chunkX, chunkZ);
        if (claim == null) {
            // No claim - return terrain as-is
            this.image = baseImage;
            return;
        }

        // Found a claim - log it
        LOGGER.fine("[ClaimsImageBuilder] Found claim at chunk (" + chunkX + ", " + chunkZ + ") in world '" + worldName + "' - owner: " + claim.getOwnerName());

        // Get claim color
        int claimColor = getClaimColor(claim);
        if (claimColor == 0) {
            LOGGER.warning("[ClaimsImageBuilder] Claim color is 0 (invalid) - skipping overlay");
            this.image = baseImage;
            return;
        }

        // Extract claim RGB components
        int claimR = (claimColor >> 24) & 0xFF;
        int claimG = (claimColor >> 16) & 0xFF;
        int claimB = (claimColor >> 8) & 0xFF;

        LOGGER.fine("[ClaimsImageBuilder] Claim color RGB: R=" + claimR + ", G=" + claimG + ", B=" + claimB);

        // Modify the pixel data IN PLACE (don't clone - modify the actual data)
        int[] pixels = baseImage.data;

        // Log some before/after pixel values for debugging
        int sampleBefore = pixels.length > 0 ? pixels[0] : 0;

        // Apply claim color overlay to each pixel
        for (int i = 0; i < pixels.length; i++) {
            int terrainColor = pixels[i];

            // Extract terrain RGB (RGBA format)
            int terrainR = (terrainColor >> 24) & 0xFF;
            int terrainG = (terrainColor >> 16) & 0xFF;
            int terrainB = (terrainColor >> 8) & 0xFF;
            int terrainA = terrainColor & 0xFF;

            // Blend: result = terrain * (1-alpha) + claim * alpha
            int blendedR = (int)(terrainR * (1 - CLAIM_OVERLAY_ALPHA) + claimR * CLAIM_OVERLAY_ALPHA);
            int blendedG = (int)(terrainG * (1 - CLAIM_OVERLAY_ALPHA) + claimG * CLAIM_OVERLAY_ALPHA);
            int blendedB = (int)(terrainB * (1 - CLAIM_OVERLAY_ALPHA) + claimB * CLAIM_OVERLAY_ALPHA);

            // Clamp values
            blendedR = Math.min(255, Math.max(0, blendedR));
            blendedG = Math.min(255, Math.max(0, blendedG));
            blendedB = Math.min(255, Math.max(0, blendedB));

            // Pack back to RGBA
            pixels[i] = (blendedR << 24) | (blendedG << 16) | (blendedB << 8) | terrainA;
        }

        int sampleAfter = pixels.length > 0 ? pixels[0] : 0;
        LOGGER.fine("[ClaimsImageBuilder] Sample pixel: before=0x" + Integer.toHexString(sampleBefore) +
            ", after=0x" + Integer.toHexString(sampleAfter) + ", changed=" + (sampleBefore != sampleAfter));

        // Return the same image with modified pixels (no need to create new MapImage)
        this.image = baseImage;
        LOGGER.fine("[ClaimsImageBuilder] Modified existing MapImage with " + pixels.length + " pixels");
    }

    /**
     * Gets the appropriate color for a claim.
     *
     * @param claim The claim
     * @return RGBA color, or 0 if no color should be applied
     */
    private int getClaimColor(@Nonnull PlotClaim claim) {
        String hexColor;

        // Custom marker color takes precedence
        if (claim.getMarkerColor() != null) {
            hexColor = claim.getMarkerColor();
        } else if (claim.isAdminClaim()) {
            hexColor = config.adminClaimColor;
        } else {
            hexColor = config.playerClaimColor;
        }

        return parseHexColor(hexColor);
    }

    /**
     * Parses a hex color string to RGBA integer.
     *
     * <p>Supports formats: "#RRGGBB", "#RGB", "RRGGBB"</p>
     *
     * @param hexColor Hex color string
     * @return RGBA color, or 0 if parsing fails
     */
    private int parseHexColor(@Nullable String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return 0;
        }

        try {
            // Remove # prefix if present
            String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;

            // Parse hex to int
            int rgb = Integer.parseInt(hex, 16);

            // Expand 3-digit hex to 6-digit
            if (hex.length() == 3) {
                int r = (rgb >> 8) & 0xF;
                int g = (rgb >> 4) & 0xF;
                int b = rgb & 0xF;
                r = (r << 4) | r;
                g = (g << 4) | g;
                b = (b << 4) | b;
                rgb = (r << 16) | (g << 8) | b;
            }

            // Extract RGB
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            // Return RGBA with full opacity
            return (r << 24) | (g << 16) | (b << 8) | 0xFF;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // === Getters ===

    /**
     * Gets the chunk index.
     */
    public long getIndex() {
        return chunkIndex;
    }

    /**
     * Gets the generated map image.
     */
    @Nonnull
    public MapImage getImage() {
        return image;
    }
}
