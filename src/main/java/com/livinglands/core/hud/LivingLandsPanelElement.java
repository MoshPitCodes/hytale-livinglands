package com.livinglands.core.hud;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.ability.AbilitySystem;
import com.livinglands.modules.leveling.ability.AbilityType;
import com.livinglands.modules.leveling.profession.ProfessionType;
import com.livinglands.modules.metabolism.MetabolismSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified Living Lands panel showing all player stats:
 * - Metabolism (Hunger, Thirst, Energy)
 * - Professions (Combat, Mining, Building, Logging, Gathering)
 * - Active Effects (Buffs and Debuffs)
 * - Passive Abilities (Unlocked profession bonuses)
 *
 * Toggled via /ll main command.
 */
public class LivingLandsPanelElement implements HudElement {

    private static final String ID = "livinglands_panel";
    private static final String NAME = "Living Lands Panel";
    private static final int MAX_ABILITIES_DISPLAYED = 5;

    @Nullable
    private MetabolismSystem metabolismSystem;
    @Nullable
    private LevelingSystem levelingSystem;
    @Nullable
    private AbilitySystem abilitySystem;
    @Nullable
    private HudModule hudModule;

    private final Set<UUID> visibleForPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> needsRefresh = new ConcurrentHashMap<>();

    public void setMetabolismSystem(@Nullable MetabolismSystem metabolismSystem) {
        this.metabolismSystem = metabolismSystem;
    }

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
        Boolean refresh = needsRefresh.remove(playerId);
        if (refresh == null || !refresh) {
            return false;
        }

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

        // Populate metabolism section
        populateMetabolismSection(builder, playerId);

        // Populate skills section
        populateSkillsSection(builder, playerId);

        // Populate effects section
        populateEffectsSection(builder, playerId);

        // Populate abilities section
        populateAbilitiesSection(builder, playerId);

        // Populate footer
        populateFooter(builder, playerId);

        return true;
    }

    private void populateMetabolismSection(UICommandBuilder builder, UUID playerId) {
        if (metabolismSystem == null) {
            builder.set("#PanelHunger.Text", "Hunger:    --");
            builder.set("#PanelThirst.Text", "Thirst:    --");
            builder.set("#PanelEnergy.Text", "Energy:    --");
            return;
        }

        var dataOpt = metabolismSystem.getPlayerData(playerId);
        if (dataOpt.isEmpty()) {
            builder.set("#PanelHunger.Text", "Hunger:    --");
            builder.set("#PanelThirst.Text", "Thirst:    --");
            builder.set("#PanelEnergy.Text", "Energy:    --");
            return;
        }

        var data = dataOpt.get();
        builder.set("#PanelHunger.Text", String.format("Hunger:    %.0f / 100", data.getHunger()));
        builder.set("#PanelThirst.Text", String.format("Thirst:    %.0f / 100", data.getThirst()));
        builder.set("#PanelEnergy.Text", String.format("Energy:    %.0f / 100", data.getEnergy()));
    }

    private void populateSkillsSection(UICommandBuilder builder, UUID playerId) {
        if (levelingSystem == null) {
            builder.set("#SkillCombat.Text", "Combat       Lv.1    0 XP");
            builder.set("#SkillMining.Text", "Mining       Lv.1    0 XP");
            builder.set("#SkillBuilding.Text", "Building     Lv.1    0 XP");
            builder.set("#SkillLogging.Text", "Logging      Lv.1    0 XP");
            builder.set("#SkillGathering.Text", "Gathering    Lv.1    0 XP");
            return;
        }

        var dataOpt = levelingSystem.getPlayerData(playerId);
        if (dataOpt.isEmpty()) {
            builder.set("#SkillCombat.Text", "Combat       Lv.1    0 XP");
            builder.set("#SkillMining.Text", "Mining       Lv.1    0 XP");
            builder.set("#SkillBuilding.Text", "Building     Lv.1    0 XP");
            builder.set("#SkillLogging.Text", "Logging      Lv.1    0 XP");
            builder.set("#SkillGathering.Text", "Gathering    Lv.1    0 XP");
            return;
        }

        var data = dataOpt.get();

        // Combat
        var combat = data.getProfession(ProfessionType.COMBAT);
        builder.set("#SkillCombat.Text", formatProfessionLine("Combat", combat));

        // Mining
        var mining = data.getProfession(ProfessionType.MINING);
        builder.set("#SkillMining.Text", formatProfessionLine("Mining", mining));

        // Building
        var building = data.getProfession(ProfessionType.BUILDING);
        builder.set("#SkillBuilding.Text", formatProfessionLine("Building", building));

        // Logging
        var logging = data.getProfession(ProfessionType.LOGGING);
        builder.set("#SkillLogging.Text", formatProfessionLine("Logging", logging));

        // Gathering
        var gathering = data.getProfession(ProfessionType.GATHERING);
        builder.set("#SkillGathering.Text", formatProfessionLine("Gathering", gathering));
    }

    private String formatProfessionLine(String name, @Nullable com.livinglands.modules.leveling.profession.ProfessionData prof) {
        int level = prof != null ? prof.getLevel() : 1;
        long currentXp = prof != null ? prof.getCurrentXp() : 0;
        long xpNeeded = prof != null ? prof.getXpToNextLevel() : 100;

        return String.format("%-10s Lv.%-2d  %d / %d XP", name, level, currentXp, xpNeeded);
    }

    private void populateEffectsSection(UICommandBuilder builder, UUID playerId) {
        List<String> effects = new ArrayList<>();

        // Check for metabolism-based effects (matching MetabolismHudElement thresholds)
        if (metabolismSystem != null) {
            var dataOpt = metabolismSystem.getPlayerData(playerId);
            if (dataOpt.isPresent()) {
                var data = dataOpt.get();

                // Hunger effects - buffs at >= 80, debuffs at <= 20
                if (data.getHunger() >= 80) {
                    effects.add("[+] Well Fed");
                } else if (data.getHunger() <= 20) {
                    effects.add("[-] Starving");
                }

                // Thirst effects - buffs at >= 80, debuffs at <= 20
                if (data.getThirst() >= 80) {
                    effects.add("[+] Hydrated");
                } else if (data.getThirst() <= 20) {
                    effects.add("[-] Dehydrated");
                }

                // Energy effects - buffs at >= 80, debuffs at <= 20
                if (data.getEnergy() >= 80) {
                    effects.add("[+] Energized");
                } else if (data.getEnergy() <= 20) {
                    effects.add("[-] Exhausted");
                }
            }
        }

        // Fill effect slots (up to 3) - uses #PanelEffect for the panel
        for (int i = 0; i < 3; i++) {
            String effectText = (i < effects.size()) ? effects.get(i) : "";
            builder.set("#PanelEffect" + (i + 1) + ".Text", effectText);
        }
    }

    private void populateAbilitiesSection(UICommandBuilder builder, UUID playerId) {
        List<String> abilities = new ArrayList<>();

        // Get unlocked abilities from the ability system
        if (abilitySystem != null) {
            for (AbilityType ability : AbilityType.values()) {
                if (abilitySystem.isUnlocked(playerId, ability)) {
                    float chance = abilitySystem.getTriggerChance(playerId, ability);
                    // Format: "[✓] Ability Name (X%)"
                    abilities.add(String.format("[✓] %s (%.0f%%)",
                        ability.getDisplayName(), chance * 100));
                }
            }
        }

        // Fill ability slots (up to MAX_ABILITIES_DISPLAYED) - uses #PanelAbility for the panel
        for (int i = 0; i < MAX_ABILITIES_DISPLAYED; i++) {
            String abilityText = (i < abilities.size()) ? abilities.get(i) : "";
            builder.set("#PanelAbility" + (i + 1) + ".Text", abilityText);
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
