package com.livinglands.modules.claims.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.livinglands.api.ModuleUIProvider;
import com.livinglands.core.notifications.NotificationModule;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import com.livinglands.modules.claims.data.ClaimPermission;
import com.livinglands.modules.claims.data.PlotClaim;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;

/**
 * UI provider for the Claims module.
 *
 * Provides a full claims management interface within the core settings panel:
 * - Claim grid (6x6) for selecting and claiming chunks
 * - View owned plots with rename/delete functionality
 * - View trusted plots with permission info
 */
public class ClaimsUIProvider implements ModuleUIProvider {

    private static final int GRID_SIZE = 7;
    private static final int GRID_CENTER = 3;
    private static final int MAX_LIST_ITEMS = 9;

    // Cell colors (from ColorUtil)
    private static final String COLOR_UNCLAIMED = com.livinglands.util.ColorUtil.getHexColor("claim_unclaimed");
    private static final String COLOR_SELECTED = com.livinglands.util.ColorUtil.getHexColor("claim_selected");
    private static final String COLOR_CLAIMED_OWN = com.livinglands.util.ColorUtil.getHexColor("claim_own");
    private static final String COLOR_CLAIMED_OTHER = com.livinglands.util.ColorUtil.getHexColor("claim_other");

    // Tab colors (from ColorUtil)
    private static final String COLOR_TAB_ACTIVE = com.livinglands.util.ColorUtil.getHexColor("ui_tab_active");
    private static final String COLOR_TAB_INACTIVE = com.livinglands.util.ColorUtil.getHexColor("ui_tab_inactive");

    private final ClaimSystem claimSystem;
    private final ClaimsModuleConfig config;
    private final NotificationModule notificationModule;
    private final HytaleLogger logger;

    // Per-player UI state
    private final Map<UUID, PlayerUIState> playerStates = new HashMap<>();

    private static class PlayerUIState {
        int section = 0; // 0 = Claim, 1 = My Plots, 2 = Trusted
        UUID selectedPlotId = null;
        Set<Long> selectedChunks = new HashSet<>();
        int centerChunkX = 0;
        int centerChunkZ = 0;
        String worldId = "";
    }

    public ClaimsUIProvider(@Nonnull ClaimSystem claimSystem,
                           @Nonnull ClaimsModuleConfig config,
                           @Nonnull NotificationModule notificationModule,
                           @Nonnull HytaleLogger logger) {
        this.claimSystem = claimSystem;
        this.config = config;
        this.notificationModule = notificationModule;
        this.logger = logger;
    }

    @Override
    @Nonnull
    public String getTabName() {
        return "Claims";
    }

    @Override
    public int getTabOrder() {
        return 10; // First tab - UI template has Claims content in #ModuleContent0
    }

    /**
     * Sets the player's center position for the claim grid.
     * Called when opening the settings panel.
     */
    public void setPlayerPosition(UUID playerId, String worldId, int chunkX, int chunkZ) {
        logger.at(Level.FINE).log("[ClaimsUI] setPlayerPosition: playerId=%s, worldId='%s', chunk=[%d,%d]",
            playerId, worldId, chunkX, chunkZ);
        PlayerUIState state = getOrCreateState(playerId);
        state.worldId = worldId;
        state.centerChunkX = chunkX;
        state.centerChunkZ = chunkZ;
    }

    @Override
    public void buildTabContent(@Nonnull UICommandBuilder cmd,
                               @Nonnull UIEventBuilder events,
                               @Nonnull PlayerRef player) {
        PlayerUIState state = getOrCreateState(player.getUuid());

        // Get data
        List<PlotClaim> ownedPlots = claimSystem.getClaimsByOwner(player.getUuid());
        List<PlotClaim> trustedPlots = claimSystem.getTrustedClaims(player.getUuid());
        int claimedChunks = claimSystem.getPlayerChunkCount(player.getUuid());
        int maxChunks = claimSystem.getMaxChunks(player.getUuid());

        // Update stats label
        String statsText = String.format("Your Claims: %d plots, %d/%d chunks",
            ownedPlots.size(), claimedChunks, maxChunks);
        cmd.set("#ClaimsStatsLabel.Text", statsText);

        // Update section buttons style
        cmd.set("#ClaimsSectionClaim.Style.Default.Background", state.section == 0 ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        cmd.set("#ClaimsSectionMyPlots.Style.Default.Background", state.section == 1 ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);
        cmd.set("#ClaimsSectionTrusted.Style.Default.Background", state.section == 2 ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);

        // Bind section buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimsSectionClaim",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "claims_section:0"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimsSectionMyPlots",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "claims_section:1"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimsSectionTrusted",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "claims_section:2"));

        // Update section visibility
        cmd.set("#ClaimsClaimSection.Visible", state.section == 0);
        cmd.set("#ClaimsMyPlotsSection.Visible", state.section == 1);
        cmd.set("#ClaimsTrustedSection.Visible", state.section == 2);

        // Build the appropriate section
        switch (state.section) {
            case 0 -> buildClaimSection(cmd, events, player, state, claimedChunks, maxChunks);
            case 1 -> buildMyPlotsSection(cmd, events, player, state, ownedPlots);
            case 2 -> buildTrustedSection(cmd, events, player, state, trustedPlots);
        }
    }

    private void buildClaimSection(UICommandBuilder cmd, UIEventBuilder events,
                                   PlayerRef player, PlayerUIState state,
                                   int claimedChunks, int maxChunks) {
        // Build grid
        for (int gridX = 0; gridX < GRID_SIZE; gridX++) {
            for (int gridZ = 0; gridZ < GRID_SIZE; gridZ++) {
                int chunkX = state.centerChunkX + (gridX - GRID_CENTER);
                int chunkZ = state.centerChunkZ + (gridZ - GRID_CENTER);

                String cellState = getCellState(state.worldId, chunkX, chunkZ, player.getUuid(), state.selectedChunks);
                String color = getCellColor(cellState);

                String cellId = String.format("#ClaimsCell%dx%d", gridX, gridZ);
                cmd.set(cellId + ".Style.Default.Background", color);

                String leftAction = String.format("claims_cell_left:%d:%d", chunkX, chunkZ);
                String rightAction = String.format("claims_cell_right:%d:%d", chunkX, chunkZ);

                events.addEventBinding(CustomUIEventBindingType.Activating, cellId,
                    com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", leftAction));
                events.addEventBinding(CustomUIEventBindingType.RightClicking, cellId,
                    com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", rightAction));
            }
        }

        // Update selection label
        int selected = state.selectedChunks.size();
        String selectionText = selected > 0
            ? String.format("Selected: %d chunks (%d + %d = %d/%d)", selected, claimedChunks, selected, claimedChunks + selected, maxChunks)
            : String.format("Selected: 0 chunks (%d/%d)", claimedChunks, maxChunks);
        cmd.set("#ClaimsSelectionLabel.Text", selectionText);

        // Bind claim and clear buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimsClaimBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "claims_claim"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimsClearBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "claims_clear"));
    }

    private String getCellState(String worldId, int chunkX, int chunkZ, UUID playerId, Set<Long> selectedChunks) {
        long key = chunkKey(chunkX, chunkZ);

        if (selectedChunks.contains(key)) {
            return "SELECTED";
        }

        PlotClaim claim = claimSystem.getClaimAt(worldId, chunkX, chunkZ);
        if (claim != null) {
            if (claim.isOwner(playerId) || claimSystem.hasAdminBypass(playerId)) {
                return "CLAIMED_OWN";
            } else {
                return "CLAIMED_OTHER";
            }
        }

        return "UNCLAIMED";
    }

    private String getCellColor(String state) {
        return switch (state) {
            case "SELECTED" -> COLOR_SELECTED;
            case "CLAIMED_OWN" -> COLOR_CLAIMED_OWN;
            case "CLAIMED_OTHER" -> COLOR_CLAIMED_OTHER;
            default -> COLOR_UNCLAIMED;
        };
    }

    private void buildMyPlotsSection(UICommandBuilder cmd, UIEventBuilder events,
                                     PlayerRef player, PlayerUIState state,
                                     List<PlotClaim> ownedPlots) {
        // Hide all list items first
        for (int i = 0; i < MAX_LIST_ITEMS; i++) {
            cmd.set("#ClaimsPlotItem" + i + ".Visible", false);
        }

        if (ownedPlots.isEmpty()) {
            cmd.set("#ClaimsEmptyMessage.Visible", true);
            cmd.set("#ClaimsEmptyMessage.Text", "You don't own any plots yet.");
            cmd.set("#ClaimsRenameSection.Visible", false);
        } else {
            cmd.set("#ClaimsEmptyMessage.Visible", false);

            // Populate list
            int count = Math.min(ownedPlots.size(), MAX_LIST_ITEMS);
            for (int i = 0; i < count; i++) {
                PlotClaim plot = ownedPlots.get(i);
                String itemId = "#ClaimsPlotItem" + i;

                cmd.set(itemId + ".Visible", true);
                cmd.set(itemId + ".Text", String.format("  %s (%d chunks)",
                    plot.getDisplayName(), plot.getTotalChunks()));

                // Highlight selected
                if (plot.getId().equals(state.selectedPlotId)) {
                    cmd.set(itemId + ".Style.Default.Background", COLOR_TAB_ACTIVE);
                } else {
                    cmd.set(itemId + ".Style.Default.Background", COLOR_TAB_INACTIVE);
                }

                // Bind click
                events.addEventBinding(CustomUIEventBindingType.Activating, itemId,
                    com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "claims_select:" + plot.getId()));
            }

            // Show rename section if a plot is selected
            if (state.selectedPlotId != null) {
                PlotClaim selected = claimSystem.getClaimById(state.selectedPlotId);
                if (selected != null && selected.isOwner(player.getUuid())) {
                    cmd.set("#ClaimsRenameSection.Visible", true);

                    // When Rename button is clicked, include the TextField's current value
                    // using "@ClaimsRenameInput" key that captures "#ClaimsRenameInput.Value"
                    // This follows Hytale's native pattern (e.g., SetNameRespawnPointPage)
                    events.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimsRenameBtn",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "claims_rename")
                            .append("@ClaimsRenameInput", "#ClaimsRenameInput.Value"));
                    events.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimsDeleteBtn",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "claims_delete"));
                } else {
                    cmd.set("#ClaimsRenameSection.Visible", false);
                }
            } else {
                cmd.set("#ClaimsRenameSection.Visible", false);
            }
        }
    }

    private void buildTrustedSection(UICommandBuilder cmd, UIEventBuilder events,
                                     PlayerRef player, PlayerUIState state,
                                     List<PlotClaim> trustedPlots) {
        // Hide all trusted list items first
        for (int i = 0; i < MAX_LIST_ITEMS; i++) {
            cmd.set("#ClaimsTrustedItem" + i + ".Visible", false);
        }

        if (trustedPlots.isEmpty()) {
            cmd.set("#ClaimsTrustedEmptyMessage.Visible", true);
            cmd.set("#ClaimsTrustedInfo.Visible", false);
        } else {
            cmd.set("#ClaimsTrustedEmptyMessage.Visible", false);

            // Populate list
            int count = Math.min(trustedPlots.size(), MAX_LIST_ITEMS);
            for (int i = 0; i < count; i++) {
                PlotClaim plot = trustedPlots.get(i);
                String itemId = "#ClaimsTrustedItem" + i;

                cmd.set(itemId + ".Visible", true);
                cmd.set(itemId + ".Text", String.format("  %s - %s",
                    plot.getDisplayName(), plot.getOwnerName()));

                // Highlight selected
                if (plot.getId().equals(state.selectedPlotId)) {
                    cmd.set(itemId + ".Style.Default.Background", COLOR_TAB_ACTIVE);
                } else {
                    cmd.set(itemId + ".Style.Default.Background", COLOR_TAB_INACTIVE);
                }

                // Bind click
                events.addEventBinding(CustomUIEventBindingType.Activating, itemId,
                    com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "claims_select_trusted:" + plot.getId()));
            }

            // Show trusted info if a plot is selected
            if (state.selectedPlotId != null) {
                PlotClaim selected = claimSystem.getClaimById(state.selectedPlotId);
                if (selected != null) {
                    cmd.set("#ClaimsTrustedInfo.Visible", true);
                    cmd.set("#ClaimsTrustedOwner.Text", "Owner: " + selected.getOwnerName());

                    ClaimPermission perm = selected.getPermission(player.getUuid());
                    String permText = perm == ClaimPermission.TRUSTED
                        ? "Trusted (Full Access)"
                        : "Accessor (Container Access)";
                    cmd.set("#ClaimsTrustedPermission.Text", "Permission: " + permText);
                } else {
                    cmd.set("#ClaimsTrustedInfo.Visible", false);
                }
            } else {
                cmd.set("#ClaimsTrustedInfo.Visible", false);
            }
        }
    }

    @Override
    public void handleTabEvent(@Nonnull String action,
                              @Nullable String value,
                              @Nonnull PlayerRef player,
                              @Nonnull Runnable rebuild) {
        logger.at(Level.FINE).log("[ClaimsUI] handleTabEvent: action='%s', value='%s', player='%s'",
            action, value, player.getUsername());

        PlayerUIState state = getOrCreateState(player.getUuid());
        logger.at(Level.FINE).log("[ClaimsUI] State: worldId='%s', centerChunk=[%d,%d], section=%d, selectedChunks=%d",
            state.worldId, state.centerChunkX, state.centerChunkZ, state.section, state.selectedChunks.size());

        String[] parts = action.split(":");

        switch (parts[0]) {
            case "claims_section" -> {
                if (parts.length >= 2) {
                    state.section = Integer.parseInt(parts[1]);
                    state.selectedPlotId = null;
                    rebuild.run();
                }
            }
            case "claims_cell_left" -> {
                if (parts.length >= 3) {
                    int chunkX = Integer.parseInt(parts[1]);
                    int chunkZ = Integer.parseInt(parts[2]);
                    handleCellLeftClick(player, state, chunkX, chunkZ);
                    rebuild.run();
                }
            }
            case "claims_cell_right" -> {
                if (parts.length >= 3) {
                    int chunkX = Integer.parseInt(parts[1]);
                    int chunkZ = Integer.parseInt(parts[2]);
                    handleCellRightClick(player, state, chunkX, chunkZ);
                    rebuild.run();
                }
            }
            case "claims_claim" -> {
                handleClaimButton(player, state);
                rebuild.run();
            }
            case "claims_clear" -> {
                state.selectedChunks.clear();
                rebuild.run();
            }
            case "claims_select" -> {
                if (parts.length >= 2) {
                    state.selectedPlotId = UUID.fromString(parts[1]);
                    rebuild.run();
                }
            }
            case "claims_select_trusted" -> {
                if (parts.length >= 2) {
                    state.selectedPlotId = UUID.fromString(parts[1]);
                    rebuild.run();
                }
            }
            case "claims_rename" -> {
                // Value contains the TextField input captured via "@ClaimsRenameInput" codec key
                logger.at(Level.FINE).log("[ClaimsUI] claims_rename: received value='%s'", value);
                handleRename(player, state, value);
                rebuild.run();
            }
            case "claims_delete" -> {
                handleDelete(player, state);
                rebuild.run();
            }
        }
    }

    private void handleCellLeftClick(PlayerRef player, PlayerUIState state, int chunkX, int chunkZ) {
        String cellState = getCellState(state.worldId, chunkX, chunkZ, player.getUuid(), state.selectedChunks);
        long key = chunkKey(chunkX, chunkZ);

        logger.at(Level.FINE).log("[ClaimsUI] handleCellLeftClick: chunk=[%d,%d], cellState='%s', worldId='%s'",
            chunkX, chunkZ, cellState, state.worldId);

        if ("UNCLAIMED".equals(cellState)) {
            int currentClaimed = claimSystem.getPlayerChunkCount(player.getUuid());
            int maxChunks = claimSystem.getMaxChunks(player.getUuid());

            if (currentClaimed + state.selectedChunks.size() < maxChunks) {
                state.selectedChunks.add(key);
                logger.at(Level.FINE).log("[ClaimsUI] Added chunk to selection, total selected: %d",
                    state.selectedChunks.size());
            } else {
                logger.at(Level.FINE).log("[ClaimsUI] Cannot add chunk - at limit (%d + %d >= %d)",
                    currentClaimed, state.selectedChunks.size(), maxChunks);
            }
        }
    }

    private void handleCellRightClick(PlayerRef player, PlayerUIState state, int chunkX, int chunkZ) {
        String cellState = getCellState(state.worldId, chunkX, chunkZ, player.getUuid(), state.selectedChunks);
        long key = chunkKey(chunkX, chunkZ);

        switch (cellState) {
            case "SELECTED" -> state.selectedChunks.remove(key);
            case "CLAIMED_OWN" -> {
                if (claimSystem.unclaimChunk(state.worldId, chunkX, chunkZ, player.getUuid())) {
                    logger.at(Level.FINE).log("[Claims UI] Unclaimed chunk [%d, %d]", chunkX, chunkZ);
                }
            }
        }
    }

    private void handleClaimButton(PlayerRef player, PlayerUIState state) {
        logger.at(Level.FINE).log("[ClaimsUI] handleClaimButton: selectedChunks=%d, worldId='%s'",
            state.selectedChunks.size(), state.worldId);

        if (state.selectedChunks.isEmpty()) {
            logger.at(Level.FINE).log("[ClaimsUI] No chunks selected, aborting claim");
            return;
        }

        logger.at(Level.FINE).log("[ClaimsUI] Calling createClaimFromChunks for player %s", player.getUsername());
        ClaimSystem.ClaimCreationResult result = claimSystem.createClaimFromChunks(
            player.getUuid(), player.getUsername(), state.worldId, state.selectedChunks, false);

        logger.at(Level.FINE).log("[ClaimsUI] createClaimFromChunks result: success=%b, message='%s'",
            result.success(), result.message());

        if (result.success()) {
            state.selectedChunks.clear();
            logger.at(Level.FINE).log("[ClaimsUI] Created claim with %d chunks",
                result.claim().getTotalChunks());
        } else {
            // Send error message to player chat
            sendErrorMessage(player.getUuid(), result.message());
            logger.at(Level.FINE).log("[ClaimsUI] Sent error message to player: %s", result.message());
        }
    }

    private void handleRename(PlayerRef player, PlayerUIState state, String inputValue) {
        logger.at(Level.FINE).log("[ClaimsUI] handleRename called with inputValue='%s'", inputValue);
        if (state.selectedPlotId == null) return;

        PlotClaim plot = claimSystem.getClaimById(state.selectedPlotId);
        if (plot == null || !plot.isOwner(player.getUuid())) return;

        String newName = inputValue;
        if (newName == null || newName.trim().isEmpty()) {
            logger.at(Level.FINE).log("[ClaimsUI] inputValue was null/empty, using default name");
            newName = String.format("%s's Plot #%d", player.getUsername(), getNextPlotNumber(player.getUuid()));
        } else {
            newName = newName.trim();
        }

        logger.at(Level.FINE).log("[ClaimsUI] Attempting to rename plot to '%s'", newName);
        if (claimSystem.renameClaim(state.selectedPlotId, newName, player.getUuid())) {
            logger.at(Level.FINE).log("[ClaimsUI] Successfully renamed plot to '%s'", newName);
        }
    }

    private void handleDelete(PlayerRef player, PlayerUIState state) {
        if (state.selectedPlotId == null) return;

        PlotClaim plot = claimSystem.getClaimById(state.selectedPlotId);
        if (plot == null || !claimSystem.canModifyClaim(player.getUuid(), plot)) return;

        if (claimSystem.deleteClaim(state.selectedPlotId)) {
            logger.at(Level.FINE).log("[Claims UI] Deleted plot %s", state.selectedPlotId);
            state.selectedPlotId = null;
        }
    }

    private int getNextPlotNumber(UUID playerId) {
        List<PlotClaim> ownedPlots = claimSystem.getClaimsByOwner(playerId);
        String prefix = "'s Plot #";
        Set<Integer> usedNumbers = new HashSet<>();

        for (PlotClaim claim : ownedPlots) {
            String name = claim.getName();
            if (name != null && name.contains(prefix)) {
                try {
                    int startIdx = name.indexOf(prefix) + prefix.length();
                    String numStr = name.substring(startIdx).split("[^0-9]")[0];
                    int num = Integer.parseInt(numStr);
                    usedNumbers.add(num);
                } catch (Exception ignored) {}
            }
        }

        int nextNum = 1;
        while (usedNumbers.contains(nextNum)) {
            nextNum++;
        }
        return nextNum;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private PlayerUIState getOrCreateState(UUID playerId) {
        return playerStates.computeIfAbsent(playerId, k -> new PlayerUIState());
    }

    public void cleanupPlayerState(UUID playerId) {
        playerStates.remove(playerId);
    }

    /**
     * Sends an error message to the player chat.
     */
    private void sendErrorMessage(@Nonnull UUID playerId, @Nonnull String message) {
        if (notificationModule != null) {
            notificationModule.sendChatError(playerId, "[Claims] " + message);
        }
    }
}
