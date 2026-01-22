package com.livinglands.modules.claims.config;

import com.livinglands.modules.claims.data.ClaimFlags;

import java.util.List;

/**
 * Main configuration for the Claims module.
 * Saved to LivingLands/claims/config.json
 *
 * Note: Module enable/disable is controlled via modules.json, not here.
 */
public class ClaimsModuleConfig {

    // Plot limits
    public int maxPlotsPerPlayer = 5;           // Max number of plots (not chunks)
    public int maxChunksPerPlot = 25;           // Max chunks in a single plot (5x5)
    public int maxTotalChunksPerPlayer = 50;    // Total chunks across all plots
    public int maxPlotDimension = 10;           // Max width/length (10x10 max)

    // Leveling integration (uses average of all profession levels)
    public boolean levelingIntegration = true;
    public int basePlotSlots = 2;               // Starting plot slots
    public int bonusSlotsPerLevels = 1;         // Extra slots per X avg levels
    public int levelsPerBonusSlot = 15;         // Every 15 avg levels = +1 slot
    public int baseChunkLimit = 25;             // Starting chunk limit
    public int bonusChunksPerLevels = 5;        // Extra chunks per X avg levels
    public int levelsPerBonusChunks = 10;       // Every 10 avg levels = +5 chunks

    // Trust limits
    public int maxTrustedPerPlot = 10;

    // World restrictions
    public List<String> claimableWorlds = List.of("*");  // "*" = all worlds

    // Map visualization
    // Full terrain rendering with claim color overlays
    public boolean showOnMap = true;
    public String playerClaimColor = "#4CAF50";   // Green for player claims
    public String adminClaimColor = "#F44336";    // Red for admin claims
    public String trustedClaimColor = "#2196F3";  // Blue for claims you're trusted in
    public String previewClaimColor = "#FFEB3B";  // Yellow for preview

    // Preview settings
    public int previewTimeoutSeconds = 30;        // Auto-cancel preview after this time

    // Admin settings
    public boolean adminClaimsProtected = true;  // Admin claims can't be modified by non-admins

    // Build restriction settings
    // When enabled, players can only place blocks inside claimed plots they own or are trusted in
    public boolean buildRestrictedToClaimsOnly = false;

    // Block types that can be placed outside claims even when buildRestrictedToClaimsOnly is true
    // Use block type IDs (e.g., "Bench_Workbench", "Bench_Builders")
    public List<String> allowedBlocksOutsideClaims = List.of(
        "Bench_Workbench",
        "Bench_Builders",
        "Bench_Campfire",
        "Furniture_Crude_Brazier",
        "Crude_Torch"
    );

    // Default flags for new claims
    public ClaimFlags defaultFlags = new ClaimFlags();

    // Messages
    public String deniedBreakMessage = "You cannot break blocks in %s's claim";
    public String deniedPlaceMessage = "You cannot place blocks in %s's claim";
    public String deniedContainerMessage = "You cannot access containers in %s's claim";
    public String deniedInteractMessage = "You cannot interact with objects in %s's claim";
    public String deniedPlaceOutsideClaimMessage = "You can only place blocks inside your claimed plots";

    public ClaimsModuleConfig() {}

    /**
     * Create default configuration with all default values.
     */
    public static ClaimsModuleConfig defaults() {
        return new ClaimsModuleConfig();
    }

    /**
     * Check if a world is claimable.
     */
    public boolean isWorldClaimable(String worldId) {
        if (claimableWorlds.contains("*")) {
            return true;
        }
        return claimableWorlds.contains(worldId);
    }
}
