package com.livinglands.modules.leveling.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.livinglands.core.hud.HudElement;
import com.livinglands.core.hud.HudModule;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD element for the skills panel that displays all professions.
 * Toggled by the /skills command.
 */
public class SkillsPanelElement implements HudElement {

    private static final String ID = "skills_panel";
    private static final String NAME = "Skills Panel";

    private final LevelingSystem system;
    private HudModule hudModule;

    // Track which players have the panel visible
    private final Set<UUID> visibleForPlayers = ConcurrentHashMap.newKeySet();

    // Track if panel needs refresh
    private final Map<UUID, Boolean> needsRefresh = new ConcurrentHashMap<>();

    public SkillsPanelElement(@Nonnull LevelingSystem system) {
        this.system = system;
    }

    public void setHudModule(@Nonnull HudModule hudModule) {
        this.hudModule = hudModule;
    }

    @Override
    @Nonnull
    public String getId() {
        return ID;
    }

    @Override
    @Nonnull
    public String getName() {
        return NAME;
    }

    @Override
    public void buildInitialValues(@Nonnull UICommandBuilder builder, @Nonnull UUID playerId) {
        // Panel starts hidden (empty text)
        builder.set("#SkillsPanelTitle.Text", "");
        builder.set("#SkillCombat.Text", "");
        builder.set("#SkillMining.Text", "");
        builder.set("#SkillBuilding.Text", "");
        builder.set("#SkillLogging.Text", "");
        builder.set("#SkillGathering.Text", "");
        builder.set("#SkillsTotalXp.Text", "");
    }

    @Override
    public boolean update(@Nonnull UICommandBuilder builder, @Nonnull UUID playerId) {
        // Check if panel needs refresh for this player
        Boolean refresh = needsRefresh.remove(playerId);
        if (refresh == null || !refresh) {
            return false;
        }

        // Update panel content
        if (visibleForPlayers.contains(playerId)) {
            return showPanel(builder, playerId);
        } else {
            return hidePanel(builder);
        }
    }

    @Override
    public void removePlayer(@Nonnull UUID playerId) {
        visibleForPlayers.remove(playerId);
        needsRefresh.remove(playerId);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Toggle the skills panel visibility for a player.
     * Returns true if now visible, false if hidden.
     */
    public boolean togglePanel(@Nonnull UUID playerId) {
        boolean nowVisible;
        if (visibleForPlayers.contains(playerId)) {
            visibleForPlayers.remove(playerId);
            nowVisible = false;
        } else {
            visibleForPlayers.add(playerId);
            nowVisible = true;
        }

        // Mark for refresh and send immediate update
        needsRefresh.put(playerId, true);

        // Send immediate update if HUD is ready
        if (hudModule != null && hudModule.isHudReady(playerId)) {
            var builder = new UICommandBuilder();
            if (nowVisible) {
                showPanel(builder, playerId);
            } else {
                hidePanel(builder);
            }
            hudModule.sendImmediateUpdate(playerId, builder);
        }

        return nowVisible;
    }

    /**
     * Check if the panel is visible for a player.
     */
    public boolean isPanelVisible(@Nonnull UUID playerId) {
        return visibleForPlayers.contains(playerId);
    }

    /**
     * Refresh the panel content for a player (e.g., after XP gain).
     */
    public void refreshPanel(@Nonnull UUID playerId) {
        if (visibleForPlayers.contains(playerId)) {
            needsRefresh.put(playerId, true);
        }
    }

    /**
     * Show the panel with current skill data.
     */
    private boolean showPanel(UICommandBuilder builder, UUID playerId) {
        var dataOpt = system.getPlayerData(playerId);
        if (dataOpt.isEmpty()) {
            return false;
        }

        var data = dataOpt.get();

        // Title
        builder.set("#SkillsPanelTitle.Text", "=== Your Skills ===");

        // Combat
        var combat = data.getProfession(ProfessionType.COMBAT);
        builder.set("#SkillCombat.Text", formatSkillLine(
            "[C] Combat",
            combat != null ? combat.getLevel() : 1,
            combat != null ? combat.getCurrentXp() : 0,
            combat != null ? combat.getXpToNextLevel() : 100
        ));

        // Mining
        var mining = data.getProfession(ProfessionType.MINING);
        builder.set("#SkillMining.Text", formatSkillLine(
            "[M] Mining",
            mining != null ? mining.getLevel() : 1,
            mining != null ? mining.getCurrentXp() : 0,
            mining != null ? mining.getXpToNextLevel() : 100
        ));

        // Building
        var building = data.getProfession(ProfessionType.BUILDING);
        builder.set("#SkillBuilding.Text", formatSkillLine(
            "[B] Building",
            building != null ? building.getLevel() : 1,
            building != null ? building.getCurrentXp() : 0,
            building != null ? building.getXpToNextLevel() : 100
        ));

        // Logging
        var logging = data.getProfession(ProfessionType.LOGGING);
        builder.set("#SkillLogging.Text", formatSkillLine(
            "[L] Logging",
            logging != null ? logging.getLevel() : 1,
            logging != null ? logging.getCurrentXp() : 0,
            logging != null ? logging.getXpToNextLevel() : 100
        ));

        // Gathering
        var gathering = data.getProfession(ProfessionType.GATHERING);
        builder.set("#SkillGathering.Text", formatSkillLine(
            "[G] Gathering",
            gathering != null ? gathering.getLevel() : 1,
            gathering != null ? gathering.getCurrentXp() : 0,
            gathering != null ? gathering.getXpToNextLevel() : 100
        ));

        // Total XP
        builder.set("#SkillsTotalXp.Text", "Total XP: " + data.getTotalXpEarned());

        return true;
    }

    /**
     * Hide the panel by clearing all text.
     */
    private boolean hidePanel(UICommandBuilder builder) {
        builder.set("#SkillsPanelTitle.Text", "");
        builder.set("#SkillCombat.Text", "");
        builder.set("#SkillMining.Text", "");
        builder.set("#SkillBuilding.Text", "");
        builder.set("#SkillLogging.Text", "");
        builder.set("#SkillGathering.Text", "");
        builder.set("#SkillsTotalXp.Text", "");
        return true;
    }

    /**
     * Format a skill line with name, level, and progress.
     */
    private String formatSkillLine(String name, int level, long currentXp, long xpNeeded) {
        String progressBar = createProgressBar(currentXp, xpNeeded, 8);
        return String.format("%s Lv.%d %s %d/%d", name, level, progressBar, currentXp, xpNeeded);
    }

    /**
     * Create a simple progress bar.
     */
    private String createProgressBar(long current, long max, int width) {
        if (max <= 0) max = 1;
        float progress = (float) current / max;
        int filled = Math.round(progress * width);

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "|" : ".");
        }
        bar.append("]");
        return bar.toString();
    }
}
