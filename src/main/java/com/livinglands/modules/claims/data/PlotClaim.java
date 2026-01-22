package com.livinglands.modules.claims.data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a plot claim containing one or more chunks.
 *
 * Claims can contain any set of chunks (not necessarily rectangular).
 * Each claim has an owner, optional trusted players with permissions, and flags.
 *
 * Note: In Hytale, chunks are 32x32x32 blocks (not 16x16 like Minecraft).
 */
public class PlotClaim {

    /**
     * Size of a chunk in blocks (Hytale uses 32x32 chunks horizontally).
     */
    public static final int CHUNK_SIZE = 32;

    /**
     * Bit shift for dividing by chunk size (log2(32) = 5).
     */
    public static final int CHUNK_SHIFT = 5;
    private UUID id;
    private UUID owner;
    private String ownerName;           // Cached for display
    private String worldId;
    private String name;                // User-defined plot name (null = auto-generated)

    // Set of claimed chunk keys (packed as (chunkX << 32) | (chunkZ & 0xFFFFFFFFL))
    private Set<Long> chunks;

    // Cached bounding box (computed from chunks, for compatibility)
    private transient int startChunkX;
    private transient int startChunkZ;
    private transient int endChunkX;
    private transient int endChunkZ;
    private transient boolean boundsComputed;

    private long createdAt;
    private Map<UUID, ClaimPermission> permissions;
    private ClaimFlags flags;
    private boolean adminClaim;         // Admin claims have priority
    private String markerColor;         // Hex color for map display (null = use default)

    /**
     * Default constructor for JSON deserialization.
     */
    public PlotClaim() {
        this.chunks = new HashSet<>();
        this.permissions = new HashMap<>();
        this.flags = new ClaimFlags();
    }

    /**
     * Create a new plot claim with specific chunks.
     *
     * @param owner      The UUID of the claim owner
     * @param ownerName  The display name of the owner
     * @param worldId    The world ID where the claim is located
     * @param chunkKeys  Set of chunk keys (packed as (chunkX << 32) | (chunkZ & 0xFFFFFFFFL))
     */
    public PlotClaim(@Nonnull UUID owner, @Nonnull String ownerName, @Nonnull String worldId,
                     @Nonnull Set<Long> chunkKeys) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.ownerName = ownerName;
        this.worldId = worldId;
        this.chunks = new HashSet<>(chunkKeys);
        this.createdAt = System.currentTimeMillis();
        this.permissions = new HashMap<>();
        this.flags = new ClaimFlags();
        this.adminClaim = false;
    }

    /**
     * Create a new plot claim from rectangular bounds (legacy compatibility).
     *
     * @param owner      The UUID of the claim owner
     * @param ownerName  The display name of the owner
     * @param worldId    The world ID where the claim is located
     * @param startChunkX Northwest corner X (chunk coordinate)
     * @param startChunkZ Northwest corner Z (chunk coordinate)
     * @param endChunkX   Southeast corner X (chunk coordinate)
     * @param endChunkZ   Southeast corner Z (chunk coordinate)
     */
    public PlotClaim(@Nonnull UUID owner, @Nonnull String ownerName, @Nonnull String worldId,
                     int startChunkX, int startChunkZ, int endChunkX, int endChunkZ) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.ownerName = ownerName;
        this.worldId = worldId;
        this.chunks = new HashSet<>();

        // Add all chunks in the rectangle
        int minX = Math.min(startChunkX, endChunkX);
        int maxX = Math.max(startChunkX, endChunkX);
        int minZ = Math.min(startChunkZ, endChunkZ);
        int maxZ = Math.max(startChunkZ, endChunkZ);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                this.chunks.add(chunkKey(x, z));
            }
        }

        this.createdAt = System.currentTimeMillis();
        this.permissions = new HashMap<>();
        this.flags = new ClaimFlags();
        this.adminClaim = false;
    }

    /**
     * Creates a chunk key from coordinates.
     */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Extracts chunk X from a chunk key.
     */
    public static int chunkX(long key) {
        return (int) (key >> 32);
    }

    /**
     * Extracts chunk Z from a chunk key.
     */
    public static int chunkZ(long key) {
        return (int) key;
    }

    // === Dimension calculations ===

    /**
     * Computes and caches the bounding box from chunks.
     */
    private void computeBounds() {
        if (boundsComputed || chunks == null || chunks.isEmpty()) {
            return;
        }

        startChunkX = Integer.MAX_VALUE;
        startChunkZ = Integer.MAX_VALUE;
        endChunkX = Integer.MIN_VALUE;
        endChunkZ = Integer.MIN_VALUE;

        for (long key : chunks) {
            int x = chunkX(key);
            int z = chunkZ(key);
            startChunkX = Math.min(startChunkX, x);
            startChunkZ = Math.min(startChunkZ, z);
            endChunkX = Math.max(endChunkX, x);
            endChunkZ = Math.max(endChunkZ, z);
        }

        boundsComputed = true;
    }

    /**
     * Get the width of the claim bounding box in chunks (X dimension).
     */
    public int getWidthChunks() {
        computeBounds();
        return endChunkX - startChunkX + 1;
    }

    /**
     * Get the length of the claim bounding box in chunks (Z dimension).
     */
    public int getLengthChunks() {
        computeBounds();
        return endChunkZ - startChunkZ + 1;
    }

    /**
     * Get the total number of chunks in this claim.
     */
    public int getTotalChunks() {
        return chunks != null ? chunks.size() : 0;
    }

    /**
     * Check if a specific chunk is within this claim.
     *
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return true if the chunk is within this claim
     */
    public boolean containsChunk(int chunkX, int chunkZ) {
        return chunks != null && chunks.contains(chunkKey(chunkX, chunkZ));
    }

    /**
     * Check if a block position is within this claim.
     *
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return true if the block is within this claim
     */
    public boolean containsBlock(int blockX, int blockZ) {
        int chunkX = blockX >> CHUNK_SHIFT;  // Divide by 32
        int chunkZ = blockZ >> CHUNK_SHIFT;
        return containsChunk(chunkX, chunkZ);
    }

    /**
     * Gets all chunk keys in this claim.
     */
    @Nonnull
    public Set<Long> getChunks() {
        return chunks != null ? chunks : Set.of();
    }

    /**
     * Adds a chunk to this claim.
     */
    public void addChunk(int chunkX, int chunkZ) {
        if (chunks == null) {
            chunks = new HashSet<>();
        }
        chunks.add(chunkKey(chunkX, chunkZ));
        boundsComputed = false;
    }

    /**
     * Removes a chunk from this claim.
     *
     * @return true if the chunk was removed
     */
    public boolean removeChunk(int chunkX, int chunkZ) {
        if (chunks == null) {
            return false;
        }
        boolean removed = chunks.remove(chunkKey(chunkX, chunkZ));
        if (removed) {
            boundsComputed = false;
        }
        return removed;
    }

    // === Block coordinate bounds ===

    /**
     * Get the minimum block X coordinate of this claim's bounding box.
     */
    public int getMinBlockX() {
        computeBounds();
        return startChunkX * CHUNK_SIZE;
    }

    /**
     * Get the minimum block Z coordinate of this claim's bounding box.
     */
    public int getMinBlockZ() {
        computeBounds();
        return startChunkZ * CHUNK_SIZE;
    }

    /**
     * Get the maximum block X coordinate of this claim's bounding box (exclusive).
     */
    public int getMaxBlockX() {
        computeBounds();
        return (endChunkX + 1) * CHUNK_SIZE;
    }

    /**
     * Get the maximum block Z coordinate of this claim's bounding box (exclusive).
     */
    public int getMaxBlockZ() {
        computeBounds();
        return (endChunkZ + 1) * CHUNK_SIZE;
    }

    // === Permission checks ===

    /**
     * Check if a player is the owner of this claim.
     */
    public boolean isOwner(@Nonnull UUID playerId) {
        return owner.equals(playerId);
    }

    /**
     * Check if a player has TRUSTED permission (full access).
     */
    public boolean isTrusted(@Nonnull UUID playerId) {
        if (isOwner(playerId)) return true;
        ClaimPermission perm = permissions.get(playerId);
        return perm == ClaimPermission.TRUSTED;
    }

    /**
     * Check if a player has at least ACCESSOR permission (container access).
     */
    public boolean hasAccessorPermission(@Nonnull UUID playerId) {
        if (isOwner(playerId)) return true;
        ClaimPermission perm = permissions.get(playerId);
        return perm != null;  // Any permission grants at least accessor
    }

    /**
     * Check if a player has a specific permission level.
     */
    public boolean hasPermission(@Nonnull UUID playerId, @Nonnull ClaimPermission required) {
        if (isOwner(playerId)) return true;
        ClaimPermission perm = permissions.get(playerId);
        if (perm == null) return false;

        return switch (required) {
            case ACCESSOR -> true;  // Any permission grants accessor
            case TRUSTED -> perm == ClaimPermission.TRUSTED;
        };
    }

    /**
     * Get the permission level of a player, or null if not trusted.
     */
    @Nullable
    public ClaimPermission getPermission(@Nonnull UUID playerId) {
        return permissions.get(playerId);
    }

    /**
     * Grant a permission to a player.
     */
    public void setPermission(@Nonnull UUID playerId, @Nonnull ClaimPermission permission) {
        permissions.put(playerId, permission);
    }

    /**
     * Remove all permissions for a player.
     */
    public void removePermission(@Nonnull UUID playerId) {
        permissions.remove(playerId);
    }

    // === Overlap detection ===

    /**
     * Check if this claim overlaps with another claim (shares any chunks).
     */
    public boolean overlaps(@Nonnull PlotClaim other) {
        if (!this.worldId.equals(other.worldId)) {
            return false;
        }
        // Check if any chunks are shared
        if (this.chunks == null || other.chunks == null) {
            return false;
        }
        for (long key : this.chunks) {
            if (other.chunks.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if this claim contains any of the specified chunks.
     */
    public boolean containsAnyChunk(@Nonnull Set<Long> chunkKeys) {
        if (this.chunks == null) {
            return false;
        }
        for (long key : chunkKeys) {
            if (this.chunks.contains(key)) {
                return true;
            }
        }
        return false;
    }

    // === Getters ===

    @Nonnull
    public UUID getId() {
        return id;
    }

    @Nonnull
    public UUID getOwner() {
        return owner;
    }

    @Nonnull
    public String getOwnerName() {
        return ownerName;
    }

    @Nonnull
    public String getWorldId() {
        return worldId;
    }

    /**
     * Gets the user-defined plot name, or null if not set.
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Gets a display name for this plot.
     * Returns the user-defined name if set, otherwise generates one from coordinates.
     */
    @Nonnull
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        computeBounds();
        return String.format("Plot [%d, %d]", startChunkX, startChunkZ);
    }

    public int getStartChunkX() {
        computeBounds();
        return startChunkX;
    }

    public int getStartChunkZ() {
        computeBounds();
        return startChunkZ;
    }

    public int getEndChunkX() {
        computeBounds();
        return endChunkX;
    }

    public int getEndChunkZ() {
        computeBounds();
        return endChunkZ;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Nonnull
    public Map<UUID, ClaimPermission> getPermissions() {
        return permissions;
    }

    @Nonnull
    public ClaimFlags getFlags() {
        return flags;
    }

    public boolean isAdminClaim() {
        return adminClaim;
    }

    @Nullable
    public String getMarkerColor() {
        return markerColor;
    }

    // === Setters ===

    public void setId(@Nonnull UUID id) {
        this.id = id;
    }

    public void setOwner(@Nonnull UUID owner) {
        this.owner = owner;
    }

    public void setOwnerName(@Nonnull String ownerName) {
        this.ownerName = ownerName;
    }

    public void setWorldId(@Nonnull String worldId) {
        this.worldId = worldId;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public void setChunks(@Nonnull Set<Long> chunks) {
        this.chunks = new HashSet<>(chunks);
        this.boundsComputed = false;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public void setPermissions(@Nonnull Map<UUID, ClaimPermission> permissions) {
        this.permissions = permissions;
    }

    public void setFlags(@Nonnull ClaimFlags flags) {
        this.flags = flags;
    }

    public void setAdminClaim(boolean adminClaim) {
        this.adminClaim = adminClaim;
    }

    public void setMarkerColor(@Nullable String markerColor) {
        this.markerColor = markerColor;
    }

    @Override
    public String toString() {
        computeBounds();
        return String.format("PlotClaim{id=%s, owner=%s, world=%s, chunks=%d, bounds=[%d,%d]->[%d,%d], admin=%b}",
            id, ownerName, worldId, getTotalChunks(), startChunkX, startChunkZ, endChunkX, endChunkZ, adminClaim);
    }
}
