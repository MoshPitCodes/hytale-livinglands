package com.livinglands.modules.claims;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.modules.claims.data.PlotClaim;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * Handles persistence of claim data to/from JSON files.
 * Data is stored in LivingLands/claims/worlds/{worldId}/claims.json
 *
 * <p>Thread Safety: This class uses per-world locks to ensure thread-safe
 * load-modify-save operations. Multiple worlds can be saved concurrently,
 * but operations on the same world are serialized.</p>
 */
public class ClaimDataPersistence {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path worldsDirectory;
    private final HytaleLogger logger;

    // Per-world locks for safe load-modify-save operations
    private final Map<String, ReentrantLock> worldLocks = new ConcurrentHashMap<>();

    /**
     * Creates a new claim data persistence handler.
     *
     * @param moduleDirectory The claims module's data directory
     * @param logger Logger for status messages
     */
    public ClaimDataPersistence(@Nonnull Path moduleDirectory, @Nonnull HytaleLogger logger) {
        this.worldsDirectory = moduleDirectory.resolve("worlds");
        this.logger = logger;

        // Ensure worlds directory exists
        try {
            Files.createDirectories(worldsDirectory);
            logger.at(Level.FINE).log("[Claims] Claims worlds directory: %s", worldsDirectory);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to create claims worlds directory");
        }
    }

    /**
     * Saves all claims for a specific world.
     *
     * @param worldId The world ID
     * @param claims The list of claims to save
     * @return true if save was successful
     */
    public boolean saveWorld(@Nonnull String worldId, @Nonnull List<PlotClaim> claims) {
        var worldDir = worldsDirectory.resolve(sanitizeWorldId(worldId));
        var filePath = worldDir.resolve("claims.json");

        try {
            Files.createDirectories(worldDir);
            var wrapper = new ClaimDataWrapper(claims);
            var json = GSON.toJson(wrapper);
            Files.writeString(filePath, json);
            logger.at(Level.FINE).log("[Claims] Saved %d claims for world: %s", claims.size(), worldId);
            return true;
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to save claims for world: %s", worldId);
            return false;
        }
    }

    /**
     * Loads all claims for a specific world.
     *
     * @param worldId The world ID
     * @return List of claims (empty if none exist or on error)
     */
    public List<PlotClaim> loadWorld(@Nonnull String worldId) {
        var worldDir = worldsDirectory.resolve(sanitizeWorldId(worldId));
        var filePath = worldDir.resolve("claims.json");

        if (!Files.exists(filePath)) {
            logger.at(Level.FINE).log("[Claims] No claims data for world: %s", worldId);
            return new ArrayList<>();
        }

        try {
            var json = Files.readString(filePath);
            var wrapper = GSON.fromJson(json, ClaimDataWrapper.class);
            if (wrapper == null || wrapper.claims == null) {
                return new ArrayList<>();
            }

            // Validate loaded claims
            var validClaims = new ArrayList<PlotClaim>();
            for (var claim : wrapper.claims) {
                if (validateClaim(claim)) {
                    validClaims.add(claim);
                } else {
                    logger.at(Level.WARNING).log("[Claims] Skipping invalid claim during load: %s", claim);
                }
            }

            logger.at(Level.FINE).log("[Claims] Loaded %d claims for world: %s", validClaims.size(), worldId);
            return validClaims;
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to load claims for world: %s", worldId);
            return new ArrayList<>();
        }
    }

    /**
     * Saves a single claim (loads world, adds/updates claim, saves world).
     * This method is thread-safe using per-world locks.
     *
     * @param claim The claim to save
     * @return true if save was successful
     */
    public boolean saveClaim(@Nonnull PlotClaim claim) {
        var worldId = claim.getWorldId();
        var lock = getLockForWorld(worldId);

        lock.lock();
        try {
            var claims = loadWorld(worldId);

            // Remove existing claim with same ID (if updating)
            claims.removeIf(c -> c.getId().equals(claim.getId()));

            // Add the new/updated claim
            claims.add(claim);

            return saveWorld(worldId, claims);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Deletes a single claim.
     *
     * @param claim The claim to delete
     * @return true if deletion was successful
     */
    public boolean deleteClaim(@Nonnull PlotClaim claim) {
        return deleteClaim(claim.getWorldId(), claim.getId());
    }

    /**
     * Deletes a claim by world and claim ID.
     * This method is thread-safe using per-world locks.
     *
     * @param worldId The world ID
     * @param claimId The claim UUID
     * @return true if deletion was successful
     */
    public boolean deleteClaim(@Nonnull String worldId, @Nonnull UUID claimId) {
        var lock = getLockForWorld(worldId);

        lock.lock();
        try {
            var claims = loadWorld(worldId);
            boolean removed = claims.removeIf(c -> c.getId().equals(claimId));

            if (removed) {
                return saveWorld(worldId, claims);
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets all known world IDs that have claim data.
     *
     * @return List of world IDs
     */
    public List<String> getKnownWorldIds() {
        List<String> worldIds = new ArrayList<>();
        try {
            if (!Files.exists(worldsDirectory)) {
                return worldIds;
            }
            try (var stream = Files.list(worldsDirectory)) {
                stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .forEach(worldIds::add);
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("[Claims] Failed to list world directories");
        }
        return worldIds;
    }

    /**
     * Validates a claim has required data.
     */
    private boolean validateClaim(PlotClaim claim) {
        if (claim == null) return false;
        if (claim.getId() == null) return false;
        if (claim.getOwner() == null) return false;
        if (claim.getWorldId() == null || claim.getWorldId().isEmpty()) return false;
        if (claim.getOwnerName() == null) {
            claim.setOwnerName("Unknown");
        }
        if (claim.getPermissions() == null) {
            claim.setPermissions(new java.util.HashMap<>());
        }
        if (claim.getFlags() == null) {
            claim.setFlags(new com.livinglands.modules.claims.data.ClaimFlags());
        }
        return true;
    }

    /**
     * Sanitize world ID for use as a directory name.
     */
    private String sanitizeWorldId(String worldId) {
        // Replace any characters that might be problematic in filenames
        return worldId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Gets the lock for a specific world, creating one if needed.
     */
    private ReentrantLock getLockForWorld(String worldId) {
        return worldLocks.computeIfAbsent(worldId, k -> new ReentrantLock());
    }

    /**
     * Get the worlds directory path.
     */
    public Path getWorldsDirectory() {
        return worldsDirectory;
    }

    /**
     * Wrapper class for JSON serialization.
     */
    private static class ClaimDataWrapper {
        public List<PlotClaim> claims;

        public ClaimDataWrapper() {
            this.claims = new ArrayList<>();
        }

        public ClaimDataWrapper(List<PlotClaim> claims) {
            this.claims = claims;
        }
    }
}
