package com.livinglands.modules.claims.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import com.livinglands.modules.claims.data.ClaimPermission;
import com.livinglands.modules.claims.data.PlotClaim;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;

/**
 * Interactive UI page for claim management with three tabs:
 * 1. Claim - Grid for selecting and claiming nearby chunks
 * 2. My Plots - List of owned plots with rename functionality
 * 3. Trusted - List of plots where player has trusted access
 */
public class ClaimSelectorPage extends InteractiveCustomUIPage<ClaimSelectorPage.EventData> {

    // Grid dimensions (6x6 grid, player at center)
    private static final int GRID_SIZE = 6;
    private static final int GRID_CENTER = 2;  // Player is between cells 2,3 (0-indexed), offset by -2 to -3
    private static final int MAX_LIST_ITEMS = 5;

    // Tab colors (from ColorUtil)
    private static final String TAB_COLOR_ACTIVE = com.livinglands.util.ColorUtil.getHexColor("ui_tab_active");
    private static final String TAB_COLOR_INACTIVE = com.livinglands.util.ColorUtil.getHexColor("ui_tab_inactive");

    // Cell colors (from ColorUtil)
    private static final String COLOR_UNCLAIMED = com.livinglands.util.ColorUtil.getHexColor("claim_unclaimed");
    private static final String COLOR_SELECTED = com.livinglands.util.ColorUtil.getHexColor("claim_selected");
    private static final String COLOR_CLAIMED_OWN = com.livinglands.util.ColorUtil.getHexColor("claim_own");
    private static final String COLOR_CLAIMED_OTHER = com.livinglands.util.ColorUtil.getHexColor("claim_other");

    // Event data codec with optional text field for ValueChanged events
    private static final KeyedCodec<String> ACTION_CODEC = new KeyedCodec<>("Action", new StringCodec());
    private static final KeyedCodec<String> VALUE_CODEC = new KeyedCodec<>("Value", new StringCodec());

    public static final BuilderCodec<EventData> EVENT_DATA_CODEC = BuilderCodec.builder(EventData.class, EventData::new)
        .addField(ACTION_CODEC, EventData::setAction, EventData::action)
        .addField(VALUE_CODEC, EventData::setValue, EventData::value)
        .build();

    private final ClaimSystem claimSystem;
    private final ClaimsModuleConfig config;
    private final HytaleLogger logger;
    private final String worldId;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final UUID playerId;
    private final String playerName;

    // UI State
    private Tab currentTab = Tab.CLAIM;
    private final Set<Long> selectedChunks = new HashSet<>();
    private UUID selectedPlotId = null;
    private String pendingRenameName = "";

    // Cached plot lists
    private List<PlotClaim> ownedPlots = List.of();
    private List<PlotClaim> trustedPlots = List.of();

    private enum Tab {
        CLAIM, MY_PLOTS, TRUSTED
    }

    public static class EventData {
        private String action;
        private String value;

        public EventData() {}

        public String action() { return action; }
        public void setAction(String action) { this.action = action; }

        public String value() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public ClaimSelectorPage(@Nonnull PlayerRef playerRef,
                             @Nonnull ClaimSystem claimSystem,
                             @Nonnull ClaimsModuleConfig config,
                             @Nonnull HytaleLogger logger,
                             @Nonnull String worldId,
                             int centerChunkX,
                             int centerChunkZ) {
        super(playerRef, CustomPageLifetime.CanDismiss, EVENT_DATA_CODEC);
        this.claimSystem = claimSystem;
        this.config = config;
        this.logger = logger;
        this.worldId = worldId;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.playerId = playerRef.getUuid();
        this.playerName = playerRef.getUsername();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        // Load the UI template
        cmd.append("Pages/ClaimSelectorPage.ui");

        // Refresh cached data
        refreshPlotLists();

        // Build tab buttons
        bindTabEvents(events);

        // Update tab visibility and styles
        updateTabVisibility(cmd);

        // Build content for current tab
        switch (currentTab) {
            case CLAIM -> buildClaimTab(cmd, events);
            case MY_PLOTS -> buildMyPlotsTab(cmd, events);
            case TRUSTED -> buildTrustedTab(cmd, events);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull EventData data) {
        String action = data.action();
        if (action == null) return;

        String[] parts = action.split(":");

        switch (parts[0]) {
            // Tab navigation
            case "tab_claim" -> {
                currentTab = Tab.CLAIM;
                selectedPlotId = null;
            }
            case "tab_myplots" -> {
                currentTab = Tab.MY_PLOTS;
                selectedPlotId = null;
            }
            case "tab_trusted" -> {
                currentTab = Tab.TRUSTED;
                selectedPlotId = null;
            }

            // Claim tab actions
            case "cell_left" -> {
                if (parts.length >= 3) {
                    int chunkX = Integer.parseInt(parts[1]);
                    int chunkZ = Integer.parseInt(parts[2]);
                    handleCellLeftClick(chunkX, chunkZ);
                }
            }
            case "cell_right" -> {
                if (parts.length >= 3) {
                    int chunkX = Integer.parseInt(parts[1]);
                    int chunkZ = Integer.parseInt(parts[2]);
                    handleCellRightClick(chunkX, chunkZ);
                }
            }
            case "claim" -> handleClaimButton(ref, store);
            case "clear" -> handleClearButton();
            case "cancel" -> handleCancelButton();

            // My Plots tab actions
            case "select_plot" -> {
                if (parts.length >= 2) {
                    selectedPlotId = UUID.fromString(parts[1]);
                    // Set pending rename to current name
                    PlotClaim plot = claimSystem.getClaimById(selectedPlotId);
                    if (plot != null) {
                        pendingRenameName = plot.getName() != null ? plot.getName() : "";
                    }
                }
            }
            case "rename" -> handleRename(pendingRenameName);
            case "rename_input" -> {
                // ValueChanged event from TextField - capture the text
                String inputText = data.value();
                if (inputText != null) {
                    pendingRenameName = inputText;
                }
            }
            case "delete_plot" -> handleDeletePlot();

            // Trusted tab actions
            case "select_trusted" -> {
                if (parts.length >= 2) {
                    selectedPlotId = UUID.fromString(parts[1]);
                }
            }
        }

        rebuild();
    }

    private void refreshPlotLists() {
        ownedPlots = claimSystem.getClaimsByOwner(playerId);
        trustedPlots = claimSystem.getTrustedClaims(playerId);
    }

    private void bindTabEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabClaim",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "tab_claim"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabMyPlots",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "tab_myplots"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabTrusted",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "tab_trusted"));
    }

    private void updateTabVisibility(UICommandBuilder cmd) {
        // Update tab background colors to indicate active state
        cmd.set("#TabClaim.Style.Default.Background", currentTab == Tab.CLAIM ? TAB_COLOR_ACTIVE : TAB_COLOR_INACTIVE);
        cmd.set("#TabMyPlots.Style.Default.Background", currentTab == Tab.MY_PLOTS ? TAB_COLOR_ACTIVE : TAB_COLOR_INACTIVE);
        cmd.set("#TabTrusted.Style.Default.Background", currentTab == Tab.TRUSTED ? TAB_COLOR_ACTIVE : TAB_COLOR_INACTIVE);

        // Update tab content visibility
        cmd.set("#ClaimTab.Visible", currentTab == Tab.CLAIM);
        cmd.set("#MyPlotsTab.Visible", currentTab == Tab.MY_PLOTS);
        cmd.set("#TrustedTab.Visible", currentTab == Tab.TRUSTED);
    }

    // ==================== CLAIM TAB ====================

    private void buildClaimTab(UICommandBuilder cmd, UIEventBuilder events) {
        // Build grid
        for (int gridX = 0; gridX < GRID_SIZE; gridX++) {
            for (int gridZ = 0; gridZ < GRID_SIZE; gridZ++) {
                int chunkX = centerChunkX + (gridX - GRID_CENTER);
                int chunkZ = centerChunkZ + (gridZ - GRID_CENTER);

                CellState state = getCellState(chunkX, chunkZ);
                String color = getCellColor(state);

                String cellId = String.format("#Cell%dx%d", gridX, gridZ);
                cmd.set(cellId + ".Style.Default.Background", color);

                String leftAction = String.format("cell_left:%d:%d", chunkX, chunkZ);
                String rightAction = String.format("cell_right:%d:%d", chunkX, chunkZ);

                events.addEventBinding(CustomUIEventBindingType.Activating, cellId,
                    com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", leftAction));
                events.addEventBinding(CustomUIEventBindingType.RightClicking, cellId,
                    com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", rightAction));
            }
        }

        // Update counter
        int claimed = claimSystem.getPlayerChunkCount(playerId);
        int max = claimSystem.getMaxChunks(playerId);
        int selected = selectedChunks.size();

        String counterText = selected > 0
            ? String.format("%d (+%d) / %d", claimed, selected, max)
            : String.format("%d / %d", claimed, max);
        cmd.set("#CounterValue.Text", counterText);

        // Bind button events
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "claim"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "clear"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "cancel"));
    }

    private enum CellState {
        UNCLAIMED, SELECTED, CLAIMED_OWN, CLAIMED_OTHER
    }

    private CellState getCellState(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);

        if (selectedChunks.contains(key)) {
            return CellState.SELECTED;
        }

        PlotClaim claim = claimSystem.getClaimAt(worldId, chunkX, chunkZ);
        if (claim != null) {
            if (claim.isOwner(playerId) || claimSystem.hasAdminBypass(playerId)) {
                return CellState.CLAIMED_OWN;
            } else {
                return CellState.CLAIMED_OTHER;
            }
        }

        return CellState.UNCLAIMED;
    }

    private String getCellColor(CellState state) {
        return switch (state) {
            case UNCLAIMED -> COLOR_UNCLAIMED;
            case SELECTED -> COLOR_SELECTED;
            case CLAIMED_OWN -> COLOR_CLAIMED_OWN;
            case CLAIMED_OTHER -> COLOR_CLAIMED_OTHER;
        };
    }

    private void handleCellLeftClick(int chunkX, int chunkZ) {
        CellState state = getCellState(chunkX, chunkZ);
        long key = chunkKey(chunkX, chunkZ);

        if (state == CellState.UNCLAIMED) {
            int currentClaimed = claimSystem.getPlayerChunkCount(playerId);
            int maxChunks = claimSystem.getMaxChunks(playerId);

            if (currentClaimed + selectedChunks.size() < maxChunks) {
                selectedChunks.add(key);
                logger.at(Level.FINE).log("[Claims] Selected chunk [%d, %d]", chunkX, chunkZ);
            }
        }
    }

    private void handleCellRightClick(int chunkX, int chunkZ) {
        CellState state = getCellState(chunkX, chunkZ);
        long key = chunkKey(chunkX, chunkZ);

        switch (state) {
            case SELECTED -> {
                selectedChunks.remove(key);
                logger.at(Level.FINE).log("[Claims] Deselected chunk [%d, %d]", chunkX, chunkZ);
            }
            case CLAIMED_OWN -> {
                // Unclaim only this specific chunk, not the entire claim
                if (claimSystem.unclaimChunk(worldId, chunkX, chunkZ, playerId)) {
                    logger.at(Level.FINE).log("[Claims] Unclaimed chunk [%d, %d]", chunkX, chunkZ);
                }
            }
        }
    }

    private void handleClaimButton(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (selectedChunks.isEmpty()) {
            return;
        }

        ClaimSystem.ClaimCreationResult result = claimSystem.createClaimFromChunks(
            playerId, playerName, worldId, selectedChunks, false);

        if (result.success()) {
            selectedChunks.clear();
            logger.at(Level.FINE).log("[Claims] Created claim with %d chunks from UI selection",
                result.claim().getTotalChunks());
        } else {
            logger.at(Level.FINE).log("[Claims] Failed to create claim: %s", result.message());
        }
    }

    private void handleClearButton() {
        selectedChunks.clear();
        logger.at(Level.FINE).log("[Claims] Cleared selection");
    }

    private void handleCancelButton() {
        selectedChunks.clear();
        close();
    }

    // ==================== MY PLOTS TAB ====================

    private void buildMyPlotsTab(UICommandBuilder cmd, UIEventBuilder events) {
        // Hide all items first
        for (int i = 0; i < MAX_LIST_ITEMS; i++) {
            cmd.set("#PlotItem" + i + ".Visible", false);
        }

        // Show "no plots" message or populate list
        if (ownedPlots.isEmpty()) {
            cmd.set("#NoOwnedPlots.Visible", true);
            cmd.set("#RenameSection.Visible", false);
            cmd.set("#DeleteButtonContainer.Visible", false);
        } else {
            cmd.set("#NoOwnedPlots.Visible", false);

            // Populate plot items
            int count = Math.min(ownedPlots.size(), MAX_LIST_ITEMS);
            for (int i = 0; i < count; i++) {
                PlotClaim plot = ownedPlots.get(i);
                String itemId = "#PlotItem" + i;

                cmd.set(itemId + ".Visible", true);
                cmd.set(itemId + ".Text", String.format("  %s (%d chunks)", plot.getDisplayName(), plot.getTotalChunks()));

                // Highlight selected
                if (plot.getId().equals(selectedPlotId)) {
                    cmd.set(itemId + ".Style.Default.Background", TAB_COLOR_ACTIVE);
                }

                events.addEventBinding(CustomUIEventBindingType.Activating, itemId,
                    com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "select_plot:" + plot.getId()));
            }

            // Show rename/delete controls if a plot is selected
            if (selectedPlotId != null) {
                PlotClaim selectedPlot = claimSystem.getClaimById(selectedPlotId);
                if (selectedPlot != null && selectedPlot.isOwner(playerId)) {
                    cmd.set("#RenameSection.Visible", true);
                    cmd.set("#DeleteButtonContainer.Visible", true);

                    // Bind ValueChanged event to capture text input from TextField
                    events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RenameInput",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "rename_input"));

                    events.addEventBinding(CustomUIEventBindingType.Activating, "#RenameBtn",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "rename"));
                    events.addEventBinding(CustomUIEventBindingType.Activating, "#DeletePlotBtn",
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "delete_plot"));
                } else {
                    cmd.set("#RenameSection.Visible", false);
                    cmd.set("#DeleteButtonContainer.Visible", false);
                }
            } else {
                cmd.set("#RenameSection.Visible", false);
                cmd.set("#DeleteButtonContainer.Visible", false);
            }
        }
    }

    private void handleRename(String newName) {
        if (selectedPlotId == null) return;

        PlotClaim plot = claimSystem.getClaimById(selectedPlotId);
        if (plot == null || !plot.isOwner(playerId)) return;

        // Use the text from the TextField, or fall back to a default pattern
        if (newName == null || newName.trim().isEmpty()) {
            newName = String.format("%s's Plot #%d", playerName, getNextPlotNumber());
        } else {
            newName = newName.trim();
        }

        if (claimSystem.renameClaim(selectedPlotId, newName, playerId)) {
            logger.at(Level.FINE).log("[Claims] Renamed plot to '%s'", newName);
            pendingRenameName = "";
            refreshPlotLists();
        }
    }

    /**
     * Finds the next available plot number by checking existing plot names.
     * Returns the lowest unused number (e.g., if plots #1 and #3 exist, returns 2).
     */
    private int getNextPlotNumber() {
        String prefix = playerName + "'s Plot #";
        Set<Integer> usedNumbers = new HashSet<>();

        for (PlotClaim claim : ownedPlots) {
            String name = claim.getName();
            if (name != null && name.startsWith(prefix)) {
                try {
                    int num = Integer.parseInt(name.substring(prefix.length()));
                    usedNumbers.add(num);
                } catch (NumberFormatException ignored) {
                    // Not a numbered plot name, skip
                }
            }
        }

        // Find the lowest unused number starting from 1
        int nextNum = 1;
        while (usedNumbers.contains(nextNum)) {
            nextNum++;
        }
        return nextNum;
    }

    private void handleDeletePlot() {
        if (selectedPlotId == null) return;

        PlotClaim plot = claimSystem.getClaimById(selectedPlotId);
        if (plot == null || !claimSystem.canModifyClaim(playerId, plot)) return;

        if (claimSystem.deleteClaim(selectedPlotId)) {
            logger.at(Level.FINE).log("[Claims] Deleted plot %s", selectedPlotId);
            selectedPlotId = null;
            refreshPlotLists();
        }
    }

    // ==================== TRUSTED TAB ====================

    private void buildTrustedTab(UICommandBuilder cmd, UIEventBuilder events) {
        // Hide all items first
        for (int i = 0; i < MAX_LIST_ITEMS; i++) {
            cmd.set("#TrustedItem" + i + ".Visible", false);
        }

        // Show "no plots" message or populate list
        if (trustedPlots.isEmpty()) {
            cmd.set("#NoTrustedPlots.Visible", true);
            cmd.set("#TrustedPlotInfo.Visible", false);
        } else {
            cmd.set("#NoTrustedPlots.Visible", false);

            // Populate trusted plot items
            int count = Math.min(trustedPlots.size(), MAX_LIST_ITEMS);
            for (int i = 0; i < count; i++) {
                PlotClaim plot = trustedPlots.get(i);
                String itemId = "#TrustedItem" + i;

                cmd.set(itemId + ".Visible", true);
                cmd.set(itemId + ".Text", String.format("  %s - %s", plot.getDisplayName(), plot.getOwnerName()));

                // Highlight selected
                if (plot.getId().equals(selectedPlotId)) {
                    cmd.set(itemId + ".Style.Default.Background", TAB_COLOR_ACTIVE);
                }

                events.addEventBinding(CustomUIEventBindingType.Activating, itemId,
                    com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "select_trusted:" + plot.getId()));
            }

            // Show info if a plot is selected
            if (selectedPlotId != null) {
                PlotClaim selectedPlot = claimSystem.getClaimById(selectedPlotId);
                if (selectedPlot != null) {
                    cmd.set("#TrustedPlotInfo.Visible", true);
                    cmd.set("#TrustedPlotOwner.Text", "Owner: " + selectedPlot.getOwnerName());

                    ClaimPermission perm = selectedPlot.getPermission(playerId);
                    String permText = perm == ClaimPermission.TRUSTED ? "Trusted (Full Access)" : "Accessor (Container Access)";
                    cmd.set("#TrustedPlotPermission.Text", "Permission: " + permText);
                } else {
                    cmd.set("#TrustedPlotInfo.Visible", false);
                }
            } else {
                cmd.set("#TrustedPlotInfo.Visible", false);
            }
        }
    }

    // ==================== UTILITY ====================

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
