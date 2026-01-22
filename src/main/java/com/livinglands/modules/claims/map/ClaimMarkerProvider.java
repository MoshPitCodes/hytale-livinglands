package com.livinglands.modules.claims.map;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import com.livinglands.modules.claims.data.PlotClaim;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Provides map visualization for claim areas.
 *
 * Renders claims as colored chunk overlays on the world map:
 * - Green: Own claims
 * - Blue: Claims you're trusted in
 * - Red: Admin claims
 * - Yellow: Preview (temporary)
 *
 * Each claimed chunk is rendered as a semi-transparent colored overlay.
 */
public class ClaimMarkerProvider {

    // Map image dimensions (pixels per chunk on the map)
    // Hytale map typically uses a smaller resolution per chunk
    private static final int MAP_IMAGE_SIZE = 32;

    // Overlay alpha (0-255, higher = more opaque)
    // Using a moderate value for visibility while still seeing terrain
    private static final int OVERLAY_ALPHA = 128;

    private final ClaimSystem claimSystem;
    private final ClaimsModuleConfig config;
    private final PlayerRegistry playerRegistry;
    private final HytaleLogger logger;

    public ClaimMarkerProvider(@Nonnull ClaimSystem claimSystem,
                               @Nonnull ClaimsModuleConfig config,
                               @Nonnull PlayerRegistry playerRegistry,
                               @Nonnull HytaleLogger logger) {
        this.claimSystem = claimSystem;
        this.config = config;
        this.playerRegistry = playerRegistry;
        this.logger = logger;
    }

    // === Preview Chunk Overlay Methods ===

    /**
     * Sends preview chunk overlays to a player for a pending claim.
     */
    public void sendPreviewOverlay(@Nonnull UUID playerId, @Nonnull ClaimSystem.PendingClaimPreview preview) {
        if (!config.showOnMap) return;

        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) return;

        Player player = sessionOpt.get().getPlayer();
        if (player == null) return;

        try {
            int color = parseColor(config.previewClaimColor);
            var chunks = createChunkOverlays(preview.startChunkX(), preview.startChunkZ(),
                preview.endChunkX(), preview.endChunkZ(), color);

            var packet = new UpdateWorldMap(chunks.toArray(new MapChunk[0]), null, null);
            player.getPlayerConnection().write(packet);

            logger.at(Level.FINE).log("[Claims] Sent %d preview chunk overlays to player %s",
                chunks.size(), playerId.toString().substring(0, 8));
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to send preview overlay");
        }
    }

    /**
     * Removes preview overlays by sending empty/null chunks.
     * Note: The map system may need the actual terrain to be re-sent to clear overlays.
     */
    public void removePreviewOverlay(@Nonnull UUID playerId, @Nonnull ClaimSystem.PendingClaimPreview preview) {
        if (!config.showOnMap) return;

        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) return;

        Player player = sessionOpt.get().getPlayer();
        if (player == null) return;

        try {
            // Send transparent/clear overlays to remove the preview
            var chunks = createChunkOverlays(preview.startChunkX(), preview.startChunkZ(),
                preview.endChunkX(), preview.endChunkZ(), 0x00000000); // Fully transparent

            var packet = new UpdateWorldMap(chunks.toArray(new MapChunk[0]), null, null);
            player.getPlayerConnection().write(packet);

            logger.at(Level.FINE).log("[Claims] Cleared preview overlay for player %s",
                playerId.toString().substring(0, 8));
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to clear preview overlay");
        }
    }

    // === Claim Chunk Overlay Methods ===

    /**
     * Sends claim chunk overlays to a player.
     */
    public void sendClaimOverlay(@Nonnull UUID playerId, @Nonnull PlotClaim claim) {
        if (!config.showOnMap) return;

        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) return;

        Player player = sessionOpt.get().getPlayer();
        if (player == null) return;

        try {
            int color = determineColor(claim, playerId);
            var chunks = createChunkOverlaysFromSet(claim.getChunks(), color);

            var packet = new UpdateWorldMap(chunks.toArray(new MapChunk[0]), null, null);
            player.getPlayerConnection().write(packet);

            logger.at(Level.FINE).log("[Claims] Sent %d claim chunk overlays to player %s",
                chunks.size(), playerId.toString().substring(0, 8));
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to send claim overlay");
        }
    }

    /**
     * Broadcasts claim overlays to all online players when a claim is created.
     */
    public void broadcastClaimCreated(@Nonnull PlotClaim claim) {
        if (!config.showOnMap) return;

        for (var session : playerRegistry.getAllSessions()) {
            UUID playerId = session.getPlayerId();
            sendClaimOverlay(playerId, claim);
        }
    }

    /**
     * Broadcasts claim removal (sends transparent overlays) to all online players.
     */
    public void broadcastClaimDeleted(@Nonnull PlotClaim claim) {
        if (!config.showOnMap) return;

        for (var session : playerRegistry.getAllSessions()) {
            UUID playerId = session.getPlayerId();

            var sessionOpt = playerRegistry.getSession(playerId);
            if (sessionOpt.isEmpty()) continue;

            Player player = sessionOpt.get().getPlayer();
            if (player == null) continue;

            try {
                // Send transparent overlays to clear the claim area
                var chunks = createChunkOverlaysFromSet(claim.getChunks(), 0x00000000);

                var packet = new UpdateWorldMap(chunks.toArray(new MapChunk[0]), null, null);
                player.getPlayerConnection().write(packet);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to clear claim overlay");
            }
        }
    }

    /**
     * Sends all claim overlays for a world to a player (e.g., on login).
     */
    public void sendAllClaimsToPlayer(@Nonnull UUID playerId, @Nonnull String worldId) {
        if (!config.showOnMap) return;

        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) return;

        Player player = sessionOpt.get().getPlayer();
        if (player == null) return;

        try {
            var claims = claimSystem.getClaimsInWorld(worldId);
            List<MapChunk> allChunks = new ArrayList<>();

            for (PlotClaim claim : claims) {
                int color = determineColor(claim, playerId);
                allChunks.addAll(createChunkOverlaysFromSet(claim.getChunks(), color));
            }

            if (!allChunks.isEmpty()) {
                var packet = new UpdateWorldMap(allChunks.toArray(new MapChunk[0]), null, null);
                player.getPlayerConnection().write(packet);

                logger.at(Level.FINE).log("[Claims] Sent %d claim chunk overlays to player %s for world %s",
                    allChunks.size(), playerId.toString().substring(0, 8), worldId);
            }
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to send all claim overlays");
        }
    }

    // === Internal Methods ===

    /**
     * Creates MapChunk overlays from a set of chunk keys.
     * This is the primary method for claim rendering.
     */
    private List<MapChunk> createChunkOverlaysFromSet(@Nonnull java.util.Set<Long> chunkKeys, int color) {
        List<MapChunk> chunks = new ArrayList<>();

        for (long key : chunkKeys) {
            int x = PlotClaim.chunkX(key);
            int z = PlotClaim.chunkZ(key);
            MapImage image = createColoredImage(color);
            MapChunk chunk = new MapChunk(x, z, image);
            chunks.add(chunk);
        }

        return chunks;
    }

    /**
     * Creates MapChunk overlays for a rectangular area.
     * Used for previews which are still rectangular.
     */
    private List<MapChunk> createChunkOverlays(int startChunkX, int startChunkZ,
                                                int endChunkX, int endChunkZ, int color) {
        List<MapChunk> chunks = new ArrayList<>();

        for (int x = startChunkX; x <= endChunkX; x++) {
            for (int z = startChunkZ; z <= endChunkZ; z++) {
                MapImage image = createColoredImage(color);
                MapChunk chunk = new MapChunk(x, z, image);
                chunks.add(chunk);
            }
        }

        return chunks;
    }

    /**
     * Creates a solid colored MapImage for a chunk.
     */
    private MapImage createColoredImage(int color) {
        int[] data = new int[MAP_IMAGE_SIZE * MAP_IMAGE_SIZE];

        // Fill with the color (ARGB format)
        for (int i = 0; i < data.length; i++) {
            data[i] = color;
        }

        return new MapImage(MAP_IMAGE_SIZE, MAP_IMAGE_SIZE, data);
    }

    /**
     * Determines the overlay color based on viewer's relationship to claim.
     */
    private int determineColor(@Nonnull PlotClaim claim, @Nonnull UUID viewerId) {
        String hexColor;

        if (claim.isAdminClaim()) {
            hexColor = config.adminClaimColor;  // Red
        } else if (claim.isOwner(viewerId)) {
            hexColor = config.playerClaimColor;  // Green (your claim)
        } else if (claim.hasAccessorPermission(viewerId)) {
            hexColor = config.trustedClaimColor;  // Blue (trusted)
        } else {
            hexColor = config.playerClaimColor;  // Green (others' claims)
        }

        return parseColor(hexColor);
    }

    /**
     * Parses a hex color string to packed int with overlay alpha.
     * Hytale MapImage uses RGBA format (Red, Green, Blue, Alpha) based on SimpleClaims reference.
     */
    private int parseColor(String hexColor) {
        try {
            // Remove # if present
            if (hexColor.startsWith("#")) {
                hexColor = hexColor.substring(1);
            }

            int rgb = Integer.parseInt(hexColor, 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            // Pack as RGBA format (SimpleClaims format): Red, Green, Blue, Alpha
            // Format: (r << 24) | (g << 16) | (b << 8) | a
            return (r << 24) | (g << 16) | (b << 8) | OVERLAY_ALPHA;
        } catch (NumberFormatException e) {
            // Default to semi-transparent green (in RGBA): #4CAF50
            return (0x4C << 24) | (0xAF << 16) | (0x50 << 8) | OVERLAY_ALPHA;
        }
    }

    // === Callback Methods for ClaimSystem ===

    /**
     * Called when a preview is created.
     */
    public void onPreviewCreated(@Nonnull UUID playerId, @Nonnull ClaimSystem.PendingClaimPreview preview) {
        sendPreviewOverlay(playerId, preview);
    }

    /**
     * Called when a preview is cancelled or expires.
     */
    public void onPreviewRemoved(@Nonnull UUID playerId, @Nonnull ClaimSystem.PendingClaimPreview preview) {
        removePreviewOverlay(playerId, preview);
    }

    /**
     * Called when a claim is created.
     */
    public void onClaimCreated(@Nonnull PlotClaim claim) {
        broadcastClaimCreated(claim);
    }

    /**
     * Called when a claim is deleted.
     */
    public void onClaimDeleted(@Nonnull PlotClaim claim) {
        broadcastClaimDeleted(claim);
    }

    /**
     * Called when a claim is updated (e.g., chunk removed).
     * Re-broadcasts the claim to all players.
     */
    public void onClaimUpdated(@Nonnull PlotClaim claim) {
        // For updates, we can rebroadcast the claim overlay
        // Players will get the updated chunk overlay
        broadcastClaimCreated(claim);
    }

    /**
     * Called when a single chunk is unclaimed from a claim.
     * Clears the overlay for that specific chunk.
     */
    public void onChunkUnclaimed(int chunkX, int chunkZ) {
        if (!config.showOnMap) return;

        for (var session : playerRegistry.getAllSessions()) {
            UUID playerId = session.getPlayerId();

            var sessionOpt = playerRegistry.getSession(playerId);
            if (sessionOpt.isEmpty()) continue;

            Player player = sessionOpt.get().getPlayer();
            if (player == null) continue;

            try {
                // Send transparent overlay to clear this specific chunk
                MapImage image = createColoredImage(0x00000000);
                MapChunk chunk = new MapChunk(chunkX, chunkZ, image);

                var packet = new UpdateWorldMap(new MapChunk[]{chunk}, null, null);
                player.getPlayerConnection().write(packet);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to clear chunk overlay");
            }
        }
    }

    /**
     * Called when a player logs in.
     */
    public void onPlayerLogin(@Nonnull UUID playerId, @Nonnull String worldId) {
        sendAllClaimsToPlayer(playerId, worldId);
    }
}
