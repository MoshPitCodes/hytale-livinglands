package com.livinglands.modules.claims;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import com.livinglands.modules.claims.data.ClaimFlags;
import com.livinglands.modules.claims.data.ClaimPermission;
import com.livinglands.modules.claims.data.PlotClaim;
import com.livinglands.modules.claims.map.ClaimMarkerProvider;
import com.livinglands.modules.claims.map.ClaimsMapUpdateSystem;
import com.livinglands.modules.leveling.LevelingSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Core system for managing plot claims.
 *
 * <p>Thread Safety: This class is designed to be accessed from multiple threads
 * (game thread, UI thread, network thread). All data structures use
 * {@link ConcurrentHashMap} for thread-safe access. Read operations are
 * lock-free and safe to call from any thread. Write operations (claim creation,
 * deletion, trust changes) are atomic at the collection level.</p>
 *
 * <p>Persistence operations are performed asynchronously to avoid blocking
 * the calling thread during file I/O.</p>
 *
 * Handles:
 * - Claim creation and deletion
 * - Chunk-to-claim lookups (O(1) via index)
 * - Permission management
 * - Leveling integration for bonus claim slots
 * - Preview management for claim creation
 */
public class ClaimSystem {

    private final ClaimsModuleConfig config;
    private final HytaleLogger logger;
    private final PlayerRegistry playerRegistry;
    private final ClaimDataPersistence persistence;

    // All claims by UUID
    private final Map<UUID, PlotClaim> claimsById = new ConcurrentHashMap<>();

    // Chunk index for O(1) lookups: worldId -> (chunkKey -> claim)
    private final Map<String, Map<Long, PlotClaim>> chunkIndex = new ConcurrentHashMap<>();

    // Claims by owner for quick listing
    private final Map<UUID, Set<UUID>> claimsByOwner = new ConcurrentHashMap<>();

    // Pending claim previews: playerId -> preview data
    private final Map<UUID, PendingClaimPreview> pendingPreviews = new ConcurrentHashMap<>();

    // Admin bypass mode: players who can bypass claim protection
    private final Set<UUID> adminBypass = ConcurrentHashMap.newKeySet();

    // Leveling system for bonus slots (optional)
    private LevelingSystem levelingSystem;

    // Map marker provider for visualization (optional)
    private ClaimMarkerProvider markerProvider;

    // Single-threaded executor for async persistence operations
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ClaimSystem-Persistence");
        t.setDaemon(true);
        return t;
    });

    /**
     * Creates a new claim system.
     */
    public ClaimSystem(@Nonnull ClaimsModuleConfig config,
                       @Nonnull HytaleLogger logger,
                       @Nonnull PlayerRegistry playerRegistry,
                       @Nonnull ClaimDataPersistence persistence) {
        this.config = config;
        this.logger = logger;
        this.playerRegistry = playerRegistry;
        this.persistence = persistence;
    }

    /**
     * Sets the leveling system for bonus claim slot calculation.
     */
    public void setLevelingSystem(@Nullable LevelingSystem levelingSystem) {
        this.levelingSystem = levelingSystem;
        if (levelingSystem != null) {
            logger.at(Level.FINE).log("[Claims] Leveling integration enabled");
        }
    }

    /**
     * Sets the map marker provider for claim visualization.
     */
    public void setMarkerProvider(@Nullable ClaimMarkerProvider markerProvider) {
        this.markerProvider = markerProvider;
    }

    // === Initialization ===

    /**
     * Loads all claims from persistence.
     */
    public void loadAllClaims() {
        var worldIds = persistence.getKnownWorldIds();
        int totalLoaded = 0;

        for (String worldId : worldIds) {
            var claims = persistence.loadWorld(worldId);
            for (PlotClaim claim : claims) {
                indexClaim(claim);
            }
            totalLoaded += claims.size();
        }

        logger.at(Level.FINE).log("[Claims] Loaded %d claims from %d worlds", totalLoaded, worldIds.size());
    }

    /**
     * Saves all claims to persistence.
     */
    public void saveAllClaims() {
        // Group claims by world
        Map<String, List<PlotClaim>> claimsByWorld = new HashMap<>();
        for (PlotClaim claim : claimsById.values()) {
            claimsByWorld.computeIfAbsent(claim.getWorldId(), k -> new ArrayList<>()).add(claim);
        }

        // Save each world
        for (var entry : claimsByWorld.entrySet()) {
            persistence.saveWorld(entry.getKey(), entry.getValue());
        }

        logger.at(Level.FINE).log("[Claims] Saved all claims to persistence");
    }

    // === Claim Creation ===

    /**
     * Result of a claim creation attempt.
     */
    public record ClaimCreationResult(boolean success, String message, @Nullable PlotClaim claim) {}

    /**
     * Validates if a player can create a claim with the given parameters.
     *
     * @return Error message if invalid, null if valid
     */
    @Nullable
    public String validateClaimCreation(@Nonnull UUID ownerId, @Nonnull String worldId,
                                        int startChunkX, int startChunkZ,
                                        int endChunkX, int endChunkZ,
                                        boolean isAdmin) {
        // Normalize bounds
        int minX = Math.min(startChunkX, endChunkX);
        int minZ = Math.min(startChunkZ, endChunkZ);
        int maxX = Math.max(startChunkX, endChunkX);
        int maxZ = Math.max(startChunkZ, endChunkZ);

        int width = maxX - minX + 1;
        int length = maxZ - minZ + 1;
        int totalChunks = width * length;

        // Check world is claimable
        if (!config.isWorldClaimable(worldId)) {
            return "Claims are not allowed in this world";
        }

        // Skip limits for admin claims
        if (!isAdmin) {
            // Check dimension limits
            if (width > config.maxPlotDimension || length > config.maxPlotDimension) {
                return String.format("Plot dimension exceeds maximum (%d). Max: %dx%d",
                    config.maxPlotDimension, config.maxPlotDimension, config.maxPlotDimension);
            }

            // Check chunks per plot limit
            if (totalChunks > config.maxChunksPerPlot) {
                return String.format("Plot has too many chunks (%d). Max: %d per plot",
                    totalChunks, config.maxChunksPerPlot);
            }

            // Check player's plot count
            int currentPlots = getPlayerClaimCount(ownerId);
            int maxPlots = getMaxPlots(ownerId);
            if (currentPlots >= maxPlots) {
                return String.format("You have reached your claim limit (%d/%d plots)", currentPlots, maxPlots);
            }

            // Check total chunks limit
            int currentTotalChunks = getPlayerTotalChunks(ownerId);
            if (currentTotalChunks + totalChunks > config.maxTotalChunksPerPlayer) {
                return String.format("Total chunks would exceed limit. Current: %d, Adding: %d, Max: %d",
                    currentTotalChunks, totalChunks, config.maxTotalChunksPerPlayer);
            }
        }

        // Check for overlaps with existing claims
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                PlotClaim existing = getClaimAt(worldId, x, z);
                if (existing != null) {
                    // Allow overlap only if admin overriding non-admin claim
                    if (!isAdmin || existing.isAdminClaim()) {
                        return String.format("Overlaps with existing claim owned by %s at chunk [%d, %d]",
                            existing.getOwnerName(), x, z);
                    }
                }
            }
        }

        return null; // Valid
    }

    /**
     * Creates a new plot claim.
     */
    public ClaimCreationResult createClaim(@Nonnull UUID ownerId, @Nonnull String ownerName,
                                           @Nonnull String worldId,
                                           int startChunkX, int startChunkZ,
                                           int endChunkX, int endChunkZ,
                                           boolean isAdmin) {
        // Validate first
        String error = validateClaimCreation(ownerId, worldId, startChunkX, startChunkZ, endChunkX, endChunkZ, isAdmin);
        if (error != null) {
            return new ClaimCreationResult(false, error, null);
        }

        // Create the claim
        PlotClaim claim = new PlotClaim(ownerId, ownerName, worldId, startChunkX, startChunkZ, endChunkX, endChunkZ);
        claim.setAdminClaim(isAdmin);
        claim.setFlags(config.defaultFlags.copy());

        // If admin claim is overriding existing claims, remove them first
        if (isAdmin) {
            removeOverlappingClaims(claim);
        }

        // Index and save (persistence is async)
        indexClaim(claim);
        saveClaimAsync(claim);

        // Notify marker provider
        if (markerProvider != null) {
            markerProvider.onClaimCreated(claim);
        }

        // Queue map update for all chunks in the claim
        queueMapUpdateForClaim(claim);

        logger.at(Level.FINE).log("[Claims] Created claim: %s", claim);
        return new ClaimCreationResult(true, "Claim created successfully", claim);
    }

    /**
     * Removes any non-admin claims that overlap with the given claim.
     * Used when an admin creates a claim that overrides existing claims.
     */
    private void removeOverlappingClaims(PlotClaim newClaim) {
        Set<UUID> toRemove = new HashSet<>();

        for (long key : newClaim.getChunks()) {
            int chunkX = PlotClaim.chunkX(key);
            int chunkZ = PlotClaim.chunkZ(key);
            PlotClaim existing = getClaimAt(newClaim.getWorldId(), chunkX, chunkZ);
            if (existing != null && !existing.isAdminClaim()) {
                toRemove.add(existing.getId());
            }
        }

        for (UUID claimId : toRemove) {
            deleteClaim(claimId);
            logger.at(Level.FINE).log("[Claims] Removed overlapping claim %s for admin override", claimId);
        }
    }

    /**
     * Deletes a claim by ID.
     *
     * @return true if the claim was deleted
     */
    public boolean deleteClaim(@Nonnull UUID claimId) {
        PlotClaim claim = claimsById.remove(claimId);
        if (claim == null) {
            return false;
        }

        // Remove from chunk index
        unindexClaim(claim);

        // Remove from owner index
        var ownerClaims = claimsByOwner.get(claim.getOwner());
        if (ownerClaims != null) {
            ownerClaims.remove(claimId);
        }

        // Delete from persistence (async)
        deleteClaimAsync(claim);

        // Notify marker provider
        if (markerProvider != null) {
            markerProvider.onClaimDeleted(claim);
        }

        // Queue map update for all chunks that were in the claim
        queueMapUpdateForClaim(claim);

        logger.at(Level.FINE).log("[Claims] Deleted claim: %s", claim);
        return true;
    }

    /**
     * Removes a single chunk from a claim.
     * If this was the last chunk, the entire claim is deleted.
     *
     * @param worldId World ID
     * @param chunkX  Chunk X coordinate
     * @param chunkZ  Chunk Z coordinate
     * @param playerId Player attempting the unclaim (must be owner or have admin bypass)
     * @return true if the chunk was unclaimed
     */
    public boolean unclaimChunk(@Nonnull String worldId, int chunkX, int chunkZ, @Nonnull UUID playerId) {
        PlotClaim claim = getClaimAt(worldId, chunkX, chunkZ);
        if (claim == null) {
            return false;
        }

        // Check permission
        if (!canModifyClaim(playerId, claim)) {
            return false;
        }

        // If this is the only chunk, delete the entire claim
        if (claim.getTotalChunks() <= 1) {
            return deleteClaim(claim.getId());
        }

        // Remove chunk from claim
        long key = chunkKey(chunkX, chunkZ);
        if (!claim.removeChunk(chunkX, chunkZ)) {
            return false;
        }

        // Remove from chunk index
        var worldIndex = chunkIndex.get(worldId);
        if (worldIndex != null) {
            worldIndex.remove(key);
        }

        // Save updated claim
        saveClaimAsync(claim);

        // Notify marker provider - clear the unclaimed chunk and update the claim
        if (markerProvider != null) {
            markerProvider.onChunkUnclaimed(chunkX, chunkZ);
            markerProvider.onClaimUpdated(claim);
        }

        // Queue map update for this specific chunk
        long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
        ClaimsMapUpdateSystem.queueChunkUpdate(worldId, chunkIndex);

        logger.at(Level.FINE).log("[Claims] Unclaimed chunk [%d, %d] from claim %s", chunkX, chunkZ, claim.getId());
        return true;
    }

    /**
     * Queues map updates for all chunks in a claim.
     */
    private void queueMapUpdateForClaim(@Nonnull PlotClaim claim) {
        if (!config.showOnMap) {
            return;
        }

        for (long key : claim.getChunks()) {
            int chunkX = PlotClaim.chunkX(key);
            int chunkZ = PlotClaim.chunkZ(key);
            long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
            ClaimsMapUpdateSystem.queueChunkUpdate(claim.getWorldId(), chunkIndex);
        }
    }

    // === Claim Lookup ===

    /**
     * Gets a claim by its UUID.
     */
    @Nullable
    public PlotClaim getClaimById(@Nonnull UUID claimId) {
        return claimsById.get(claimId);
    }

    /**
     * Gets the claim at a specific chunk coordinate.
     *
     * @param worldId World ID
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return The claim at this chunk, or null if unclaimed
     */
    @Nullable
    public PlotClaim getClaimAt(@Nonnull String worldId, int chunkX, int chunkZ) {
        var worldIndex = chunkIndex.get(worldId);
        if (worldIndex == null) {
            return null;
        }
        return worldIndex.get(chunkKey(chunkX, chunkZ));
    }

    /**
     * Gets the claim at a specific block position.
     *
     * @param worldId World ID
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return The claim at this position, or null if unclaimed
     */
    @Nullable
    public PlotClaim getClaimAtBlock(@Nonnull String worldId, int blockX, int blockZ) {
        return getClaimAt(worldId, blockX >> PlotClaim.CHUNK_SHIFT, blockZ >> PlotClaim.CHUNK_SHIFT);
    }

    /**
     * Gets all claims owned by a player.
     */
    @Nonnull
    public List<PlotClaim> getClaimsByOwner(@Nonnull UUID ownerId) {
        var claimIds = claimsByOwner.get(ownerId);
        if (claimIds == null || claimIds.isEmpty()) {
            return List.of();
        }
        return claimIds.stream()
            .map(claimsById::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Gets the number of plots owned by a player.
     */
    public int getPlayerClaimCount(@Nonnull UUID ownerId) {
        var claimIds = claimsByOwner.get(ownerId);
        return claimIds != null ? claimIds.size() : 0;
    }

    /**
     * Gets the total number of chunks claimed by a player.
     */
    public int getPlayerTotalChunks(@Nonnull UUID ownerId) {
        return getClaimsByOwner(ownerId).stream()
            .mapToInt(PlotClaim::getTotalChunks)
            .sum();
    }

    /**
     * Alias for getPlayerTotalChunks - used by UI.
     */
    public int getPlayerChunkCount(@Nonnull UUID ownerId) {
        return getPlayerTotalChunks(ownerId);
    }

    /**
     * Gets the maximum number of chunks a player can claim in total.
     * Includes leveling bonuses if enabled (based on average profession level).
     */
    public int getMaxChunks(@Nonnull UUID playerId) {
        int base = config.baseChunkLimit;

        if (config.levelingIntegration && levelingSystem != null) {
            int avgLevel = levelingSystem.getAverageLevel(playerId);
            int bonusChunks = (avgLevel / config.levelsPerBonusChunks) * config.bonusChunksPerLevels;
            base += bonusChunks;
        }

        return Math.min(base, config.maxTotalChunksPerPlayer);
    }

    /**
     * Gets all claims where the player has trusted access (but doesn't own).
     */
    @Nonnull
    public List<PlotClaim> getTrustedClaims(@Nonnull UUID playerId) {
        return claimsById.values().stream()
            .filter(claim -> !claim.isOwner(playerId) && claim.hasAccessorPermission(playerId))
            .collect(Collectors.toList());
    }

    /**
     * Renames a claim.
     *
     * @param claimId The claim ID
     * @param newName The new name (null or empty to reset to auto-generated)
     * @param playerId The player attempting to rename (must be owner or admin)
     * @return true if renamed successfully
     */
    public boolean renameClaim(@Nonnull UUID claimId, @Nullable String newName, @Nonnull UUID playerId) {
        PlotClaim claim = claimsById.get(claimId);
        if (claim == null) {
            return false;
        }

        // Check permission
        if (!claim.isOwner(playerId) && !hasAdminBypass(playerId)) {
            return false;
        }

        // Set the name (empty string becomes null)
        String trimmedName = (newName != null && !newName.trim().isEmpty()) ? newName.trim() : null;
        claim.setName(trimmedName);

        // Save the change
        saveClaimAsync(claim);

        logger.at(Level.FINE).log("[Claims] Renamed claim %s to '%s'", claimId, trimmedName);
        return true;
    }

    /**
     * Creates a claim directly (bypassing preview system).
     * Used by the UI-based chunk selector.
     */
    public ClaimCreationResult createClaimDirect(@Nonnull UUID ownerId, @Nonnull String ownerName,
                                                  @Nonnull String worldId,
                                                  int startChunkX, int startChunkZ,
                                                  int endChunkX, int endChunkZ,
                                                  boolean isAdmin) {
        return createClaim(ownerId, ownerName, worldId, startChunkX, startChunkZ, endChunkX, endChunkZ, isAdmin);
    }

    /**
     * Creates a claim from a set of specific chunks.
     * Used by the UI-based chunk selector for non-rectangular claims.
     */
    public ClaimCreationResult createClaimFromChunks(@Nonnull UUID ownerId, @Nonnull String ownerName,
                                                      @Nonnull String worldId,
                                                      @Nonnull Set<Long> chunkKeys,
                                                      boolean isAdmin) {
        if (chunkKeys.isEmpty()) {
            return new ClaimCreationResult(false, "No chunks selected", null);
        }

        // Validate chunk count
        int totalChunks = chunkKeys.size();
        if (totalChunks > config.maxChunksPerPlot) {
            return new ClaimCreationResult(false,
                String.format("Selection exceeds maximum chunks per claim (%d > %d)",
                    totalChunks, config.maxChunksPerPlot), null);
        }

        // Check player limits (if not admin)
        if (!isAdmin) {
            // Check player's plot count
            int currentPlots = getPlayerClaimCount(ownerId);
            int maxPlots = getMaxPlots(ownerId);
            if (currentPlots >= maxPlots) {
                return new ClaimCreationResult(false,
                    String.format("You have reached your claim limit (%d/%d plots)", currentPlots, maxPlots), null);
            }

            // Check chunk limit
            int currentChunks = getPlayerChunkCount(ownerId);
            int maxChunks = getMaxChunks(ownerId);
            if (currentChunks + totalChunks > maxChunks) {
                return new ClaimCreationResult(false,
                    String.format("Would exceed your chunk limit (%d + %d > %d)",
                        currentChunks, totalChunks, maxChunks), null);
            }
        }

        // Check for overlaps with existing claims
        for (long key : chunkKeys) {
            int chunkX = PlotClaim.chunkX(key);
            int chunkZ = PlotClaim.chunkZ(key);
            PlotClaim existing = getClaimAt(worldId, chunkX, chunkZ);
            if (existing != null) {
                if (!isAdmin || existing.isAdminClaim()) {
                    return new ClaimCreationResult(false,
                        String.format("Chunk [%d, %d] is already claimed by %s",
                            chunkX, chunkZ, existing.getOwnerName()), null);
                }
            }
        }

        // Create the claim
        PlotClaim claim = new PlotClaim(ownerId, ownerName, worldId, chunkKeys);
        claim.setAdminClaim(isAdmin);
        claim.setFlags(config.defaultFlags.copy());

        // If admin claim is overriding existing claims, remove them first
        if (isAdmin) {
            removeOverlappingClaimsFromChunks(claim, chunkKeys);
        }

        // Index and save
        indexClaim(claim);
        saveClaimAsync(claim);

        // Notify marker provider
        if (markerProvider != null) {
            markerProvider.onClaimCreated(claim);
        }

        // Queue map update for all chunks
        queueMapUpdateForClaim(claim);

        logger.at(Level.FINE).log("[Claims] Created claim with %d chunks: %s", totalChunks, claim);
        return new ClaimCreationResult(true, "Claim created successfully", claim);
    }

    /**
     * Removes non-admin claims that overlap with the specified chunks.
     */
    private void removeOverlappingClaimsFromChunks(PlotClaim newClaim, Set<Long> chunkKeys) {
        Set<UUID> toRemove = new HashSet<>();

        for (long key : chunkKeys) {
            int chunkX = PlotClaim.chunkX(key);
            int chunkZ = PlotClaim.chunkZ(key);
            PlotClaim existing = getClaimAt(newClaim.getWorldId(), chunkX, chunkZ);
            if (existing != null && !existing.isAdminClaim()) {
                toRemove.add(existing.getId());
            }
        }

        for (UUID claimId : toRemove) {
            deleteClaim(claimId);
            logger.at(Level.FINE).log("[Claims] Removed overlapping claim %s for admin override", claimId);
        }
    }

    /**
     * Gets the maximum number of plots a player can have.
     * Includes leveling bonuses if enabled.
     */
    public int getMaxPlots(@Nonnull UUID playerId) {
        int base = config.basePlotSlots;

        if (config.levelingIntegration && levelingSystem != null) {
            int avgLevel = levelingSystem.getAverageLevel(playerId);
            int bonusSlots = (avgLevel / config.levelsPerBonusSlot) * config.bonusSlotsPerLevels;
            base += bonusSlots;
        }

        return Math.min(base, config.maxPlotsPerPlayer);
    }

    /**
     * Gets all claims in a world.
     */
    @Nonnull
    public List<PlotClaim> getClaimsInWorld(@Nonnull String worldId) {
        return claimsById.values().stream()
            .filter(c -> c.getWorldId().equals(worldId))
            .collect(Collectors.toList());
    }

    // === Trust Management ===

    /**
     * Adds a trusted player to a claim.
     *
     * @return true if trust was added
     */
    public boolean trustPlayer(@Nonnull UUID claimId, @Nonnull UUID playerId, @Nonnull ClaimPermission permission) {
        PlotClaim claim = claimsById.get(claimId);
        if (claim == null) {
            return false;
        }

        // Check trust limit
        if (claim.getPermissions().size() >= config.maxTrustedPerPlot) {
            return false;
        }

        claim.setPermission(playerId, permission);
        saveClaimAsync(claim);
        return true;
    }

    /**
     * Removes a trusted player from a claim.
     *
     * @return true if trust was removed
     */
    public boolean untrustPlayer(@Nonnull UUID claimId, @Nonnull UUID playerId) {
        PlotClaim claim = claimsById.get(claimId);
        if (claim == null) {
            return false;
        }

        claim.removePermission(playerId);
        saveClaimAsync(claim);
        return true;
    }

    // === Permission Checking ===

    /**
     * Checks if a player can modify a claim (trust, flags, delete).
     */
    public boolean canModifyClaim(@Nonnull UUID playerId, @Nonnull PlotClaim claim) {
        if (claim.isAdminClaim() && !isAdmin(playerId)) {
            return false;  // Non-admin can't modify admin claims
        }
        return claim.isOwner(playerId) || isAdmin(playerId);
    }

    /**
     * Checks if a player is in admin mode (either game mode or bypass enabled).
     */
    public boolean isAdmin(@Nonnull UUID playerId) {
        // Check admin bypass first
        if (adminBypass.contains(playerId)) {
            return true;
        }

        // Check player's game mode (if available)
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isPresent()) {
            var session = sessionOpt.get();
            var player = session.getPlayer();
            if (player != null) {
                var gameMode = player.getGameMode();
                return gameMode == GameMode.Creative;
            }
        }

        return false;
    }

    /**
     * Toggles admin bypass mode for a player.
     *
     * @return true if bypass is now enabled
     */
    public boolean toggleAdminBypass(@Nonnull UUID playerId) {
        if (adminBypass.contains(playerId)) {
            adminBypass.remove(playerId);
            return false;
        } else {
            adminBypass.add(playerId);
            return true;
        }
    }

    /**
     * Checks if a player has admin bypass enabled.
     */
    public boolean hasAdminBypass(@Nonnull UUID playerId) {
        return adminBypass.contains(playerId);
    }

    // === Preview System ===

    /**
     * Data for a pending claim preview.
     */
    public record PendingClaimPreview(
        UUID playerId,
        String worldId,
        int startChunkX,
        int startChunkZ,
        int endChunkX,
        int endChunkZ,
        boolean isAdmin,
        long createdAt
    ) {
        public int getWidth() {
            return Math.abs(endChunkX - startChunkX) + 1;
        }

        public int getLength() {
            return Math.abs(endChunkZ - startChunkZ) + 1;
        }

        public int getTotalChunks() {
            return getWidth() * getLength();
        }

        public boolean isExpired(int timeoutSeconds) {
            return System.currentTimeMillis() - createdAt > (timeoutSeconds * 1000L);
        }
    }

    /**
     * Creates a pending claim preview.
     *
     * @return Error message if invalid, null if preview was created
     */
    @Nullable
    public String createPreview(@Nonnull UUID playerId, @Nonnull String ownerName,
                                @Nonnull String worldId,
                                int centerChunkX, int centerChunkZ,
                                int width, int length,
                                boolean isAdmin) {
        // Cancel any existing preview
        cancelPreview(playerId);

        // Calculate bounds centered on player's chunk
        int halfWidth = (width - 1) / 2;
        int halfLength = (length - 1) / 2;
        int startX = centerChunkX - halfWidth;
        int startZ = centerChunkZ - halfLength;
        int endX = startX + width - 1;
        int endZ = startZ + length - 1;

        // Validate
        String error = validateClaimCreation(playerId, worldId, startX, startZ, endX, endZ, isAdmin);
        if (error != null) {
            return error;
        }

        // Create preview
        PendingClaimPreview preview = new PendingClaimPreview(
            playerId, worldId, startX, startZ, endX, endZ, isAdmin, System.currentTimeMillis()
        );
        pendingPreviews.put(playerId, preview);

        // Notify marker provider to show preview markers
        if (markerProvider != null) {
            markerProvider.onPreviewCreated(playerId, preview);
        }

        logger.at(Level.FINE).log("[Claims] Created preview for %s: %dx%d at [%d,%d]->[%d,%d]",
            ownerName, width, length, startX, startZ, endX, endZ);
        return null;
    }

    /**
     * Confirms a pending preview and creates the claim.
     */
    public ClaimCreationResult confirmPreview(@Nonnull UUID playerId, @Nonnull String ownerName) {
        PendingClaimPreview preview = pendingPreviews.remove(playerId);
        if (preview == null) {
            return new ClaimCreationResult(false, "No pending claim preview", null);
        }

        // Remove preview overlay (pass preview so we know which chunks to clear)
        if (markerProvider != null) {
            markerProvider.onPreviewRemoved(playerId, preview);
        }

        if (preview.isExpired(config.previewTimeoutSeconds)) {
            return new ClaimCreationResult(false, "Preview has expired", null);
        }

        // Re-validate in case something changed
        String error = validateClaimCreation(playerId, preview.worldId,
            preview.startChunkX, preview.startChunkZ, preview.endChunkX, preview.endChunkZ, preview.isAdmin);
        if (error != null) {
            return new ClaimCreationResult(false, error, null);
        }

        // Create the claim
        return createClaim(playerId, ownerName, preview.worldId,
            preview.startChunkX, preview.startChunkZ, preview.endChunkX, preview.endChunkZ, preview.isAdmin);
    }

    /**
     * Cancels a pending preview.
     *
     * @return true if there was a preview to cancel
     */
    public boolean cancelPreview(@Nonnull UUID playerId) {
        PendingClaimPreview preview = pendingPreviews.remove(playerId);
        if (preview != null && markerProvider != null) {
            markerProvider.onPreviewRemoved(playerId, preview);
        }
        return preview != null;
    }

    /**
     * Gets a player's pending preview.
     */
    @Nullable
    public PendingClaimPreview getPreview(@Nonnull UUID playerId) {
        PendingClaimPreview preview = pendingPreviews.get(playerId);
        if (preview != null && preview.isExpired(config.previewTimeoutSeconds)) {
            pendingPreviews.remove(playerId);
            return null;
        }
        return preview;
    }

    /**
     * Checks if a player has a pending preview.
     */
    public boolean hasPendingPreview(@Nonnull UUID playerId) {
        return getPreview(playerId) != null;
    }

    // === Internal Indexing ===

    /**
     * Generates a unique key for a chunk coordinate.
     */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Adds a claim to all indexes.
     */
    private void indexClaim(PlotClaim claim) {
        claimsById.put(claim.getId(), claim);

        // Add to chunk index
        var worldIndex = chunkIndex.computeIfAbsent(claim.getWorldId(), k -> new ConcurrentHashMap<>());
        for (long key : claim.getChunks()) {
            worldIndex.put(key, claim);
        }

        // Add to owner index
        claimsByOwner.computeIfAbsent(claim.getOwner(), k -> ConcurrentHashMap.newKeySet()).add(claim.getId());
    }

    /**
     * Removes a claim from chunk index.
     */
    private void unindexClaim(PlotClaim claim) {
        var worldIndex = chunkIndex.get(claim.getWorldId());
        if (worldIndex != null) {
            for (long key : claim.getChunks()) {
                worldIndex.remove(key);
            }
        }
    }

    // === Async Persistence ===

    /**
     * Saves a claim asynchronously to avoid blocking the calling thread.
     *
     * @param claim The claim to save
     */
    private void saveClaimAsync(@Nonnull PlotClaim claim) {
        persistenceExecutor.execute(() -> {
            try {
                persistence.saveClaim(claim);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to save claim async: %s", claim.getId());
            }
        });
    }

    /**
     * Deletes a claim asynchronously to avoid blocking the calling thread.
     *
     * @param claim The claim to delete
     */
    private void deleteClaimAsync(@Nonnull PlotClaim claim) {
        persistenceExecutor.execute(() -> {
            try {
                persistence.deleteClaim(claim);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to delete claim async: %s", claim.getId());
            }
        });
    }

    // === Cleanup ===

    /**
     * Cleans up expired previews.
     */
    public void cleanupExpiredPreviews() {
        pendingPreviews.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(config.previewTimeoutSeconds);
            if (expired && markerProvider != null) {
                markerProvider.onPreviewRemoved(entry.getKey(), entry.getValue());
            }
            return expired;
        });
    }

    /**
     * Removes a player's data on disconnect (clears bypass, previews).
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        adminBypass.remove(playerId);
        cancelPreview(playerId);
    }

    /**
     * Shuts down the claim system, waiting for pending persistence operations.
     * Should be called during server shutdown.
     */
    public void shutdown() {
        logger.at(Level.FINE).log("[Claims] Shutting down claim system...");

        // Save all claims synchronously before shutdown
        saveAllClaims();

        // Shutdown the persistence executor
        persistenceExecutor.shutdown();
        try {
            if (!persistenceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.at(Level.WARNING).log("[Claims] Persistence executor did not terminate in time");
                persistenceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            persistenceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.at(Level.FINE).log("[Claims] Claim system shut down");
    }

    /**
     * Opens the claim selector UI page for a player.
     *
     * @param playerRef The player reference
     * @param ref The entity store ref from command context
     * @param store The entity store from command context
     * @param worldId The world ID
     * @param centerChunkX The center chunk X coordinate
     * @param centerChunkZ The center chunk Z coordinate
     */
    public void openClaimSelector(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
                                   @Nonnull com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,
                                   @Nonnull com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                                   @Nonnull String worldId,
                                   int centerChunkX,
                                   int centerChunkZ) {
        // Get the Player object from the PlayerRegistry
        var sessionOpt = playerRegistry.getSession(playerRef.getUuid());
        if (sessionOpt.isEmpty()) {
            logger.at(Level.WARNING).log("[Claims] Cannot open claim selector: player session not found for %s",
                playerRef.getUsername());
            return;
        }

        var session = sessionOpt.get();
        var player = session.getPlayer();
        if (player == null) {
            logger.at(Level.WARNING).log("[Claims] Cannot open claim selector: player entity not available for %s",
                playerRef.getUsername());
            return;
        }

        // Create the claim selector page
        var page = new com.livinglands.modules.claims.ui.ClaimSelectorPage(
            playerRef,
            this,
            config,
            logger,
            worldId,
            centerChunkX,
            centerChunkZ
        );

        // Open the page using the player's page manager
        player.getPageManager().openCustomPage(ref, store, page);

        logger.at(Level.FINE).log("[Claims] Opened claim selector UI for %s at chunk [%d, %d]",
            playerRef.getUsername(), centerChunkX, centerChunkZ);
    }
}
