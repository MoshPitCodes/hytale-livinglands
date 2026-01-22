package com.livinglands.core.hud;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.ability.AbilitySystem;
import com.livinglands.modules.leveling.ability.AbilityType;
import com.livinglands.modules.leveling.profession.ProfessionData;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified Living Lands panel showing professions and abilities organized by profession.
 * Each profession section shows:
 * - Level and XP progress
 * - 3 abilities (Tier 1, 2, 3) with descriptions
 *
 * Toggled via /ll main command.
 */
public class LivingLandsPanelElement implements HudElement {

    private static final String ID = "livinglands_panel";
    private static final String NAME = "Living Lands Panel";

    // Colors for ability text (from ColorUtil)
    private static final String COLOR_UNLOCKED = com.livinglands.util.ColorUtil.getHexColor("ability_unlocked");
    private static final String COLOR_LOCKED = com.livinglands.util.ColorUtil.getHexColor("ability_locked");

    @Nullable
    private LevelingSystem levelingSystem;
    @Nullable
    private AbilitySystem abilitySystem;
    @Nullable
    private HudModule hudModule;

    private final Set<UUID> visibleForPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> needsRefresh = new ConcurrentHashMap<>();

    public void setLevelingSystem(@Nullable LevelingSystem levelingSystem) {
        this.levelingSystem = levelingSystem;
    }

    public void setAbilitySystem(@Nullable AbilitySystem abilitySystem) {
        this.abilitySystem = abilitySystem;
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
        // Panel starts hidden (all empty)
        hidePanel(builder);
    }

    @Override
    public boolean update(@Nonnull UICommandBuilder builder, @Nonnull UUID playerId) {
        // Check if visibility toggle was requested
        Boolean refresh = needsRefresh.remove(playerId);

        // Always update content when panel is visible (realtime updates)
        if (visibleForPlayers.contains(playerId)) {
            return showPanel(builder, playerId);
        } else if (refresh != null && refresh) {
            // Only hide when explicitly toggled off
            return hidePanel(builder);
        }

        return false;
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
     * Toggle the panel visibility for a player.
     * @return true if now visible, false if hidden
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

        needsRefresh.put(playerId, true);

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
     * Request a refresh of the panel content for a player.
     */
    public void refreshPanel(@Nonnull UUID playerId) {
        if (visibleForPlayers.contains(playerId)) {
            needsRefresh.put(playerId, true);
        }
    }

    private boolean showPanel(UICommandBuilder builder, UUID playerId) {
        // Show the panel using Visible property
        builder.set("#SkillsPanel.Visible", true);

        // Get unlocked abilities once
        Set<AbilityType> unlockedSet = abilitySystem != null
            ? abilitySystem.getUnlockedAbilities(playerId)
            : java.util.Collections.emptySet();

        // Populate each profession section
        populateProfessionSection(builder, playerId, ProfessionType.COMBAT, "Combat", unlockedSet);
        populateProfessionSection(builder, playerId, ProfessionType.MINING, "Mining", unlockedSet);
        populateProfessionSection(builder, playerId, ProfessionType.BUILDING, "Building", unlockedSet);
        populateProfessionSection(builder, playerId, ProfessionType.LOGGING, "Logging", unlockedSet);
        populateProfessionSection(builder, playerId, ProfessionType.GATHERING, "Gathering", unlockedSet);

        // Populate footer
        populateFooter(builder, playerId);

        return true;
    }

    private void populateProfessionSection(UICommandBuilder builder, UUID playerId,
                                           ProfessionType profType, String sectionName,
                                           Set<AbilityType> unlockedSet) {
        // Get profession data
        ProfessionData profData = null;
        if (levelingSystem != null) {
            var dataOpt = levelingSystem.getPlayerData(playerId);
            if (dataOpt.isPresent()) {
                profData = dataOpt.get().getProfession(profType);
            }
        }

        // Set level text (inline with profession name)
        int level = profData != null ? profData.getLevel() : 1;
        long currentXp = profData != null ? profData.getCurrentXp() : 0;
        long xpNeeded = profData != null ? profData.getXpToNextLevel() : 100;

        String levelText = String.format("Lv.%d  •  %d / %d XP", level, currentXp, xpNeeded);
        builder.set("#" + sectionName + "Level.Text", levelText);

        // Get abilities for this profession (ordered by tier)
        AbilityType[] abilities = AbilityType.getAbilitiesForProfession(profType);

        // Populate 3 ability slots (name + description on separate lines)
        for (int i = 0; i < 3 && i < abilities.length; i++) {
            AbilityType ability = abilities[i];
            String selector = "#" + sectionName + "Ability" + (i + 1);
            String descSelector = selector + "Desc";

            boolean isUnlocked = unlockedSet.contains(ability);
            int unlockLevel = ability.getTier().getDefaultUnlockLevel();

            String nameText;
            String textColor;

            if (isUnlocked) {
                // Unlocked: just name
                nameText = ability.getDisplayName();
                textColor = COLOR_UNLOCKED;
            } else {
                // Locked: name + unlock level
                nameText = String.format("%s (Lv.%d)", ability.getDisplayName(), unlockLevel);
                textColor = COLOR_LOCKED;
            }

            builder.set(selector + ".Text", nameText);
            builder.set(selector + ".Style.TextColor", textColor);

            // Description on second line (always dimmed gray)
            builder.set(descSelector + ".Text", ability.getDescription());
        }
    }

    private void populateFooter(UICommandBuilder builder, UUID playerId) {
        if (levelingSystem != null) {
            var dataOpt = levelingSystem.getPlayerData(playerId);
            if (dataOpt.isPresent()) {
                builder.set("#SkillsTotalXp.Text", "Total XP Earned: " + dataOpt.get().getTotalXpEarned());
                return;
            }
        }
        builder.set("#SkillsTotalXp.Text", "");
    }

    private boolean hidePanel(UICommandBuilder builder) {
        // Hide the panel using Visible property
        builder.set("#SkillsPanel.Visible", false);
        return true;
    }

}
