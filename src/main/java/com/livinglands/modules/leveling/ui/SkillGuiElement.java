package com.livinglands.modules.leveling.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.livinglands.core.hud.HudElement;
import com.livinglands.core.hud.HudModule;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD element for skills XP notifications.
 * Registers with the HudModule to display XP gains in the combined HUD.
 */
public class SkillGuiElement implements HudElement {

    private static final String ID = "skillgui";
    private static final String NAME = "Skill GUI XP Notifications";
    private static final long DISPLAY_DURATION_MS = 3000;

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private HudModule hudModule;

    // Pending XP notifications per player
    private final Map<UUID, XpNotification> pendingNotifications = new ConcurrentHashMap<>();

    // Track when notification was shown
    private final Map<UUID, Long> notificationTime = new ConcurrentHashMap<>();

    private record XpNotification(
        ProfessionType profession,
        long xpGained,
        int level,
        long currentXp,
        long xpNeeded
    ) {}

    public SkillGuiElement(@Nonnull LevelingSystem system,
                           @Nonnull LevelingModuleConfig config) {
        this.system = system;
        this.config = config;
    }

    /**
     * Set the HudModule reference for immediate updates.
     */
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
        // Start with empty notification area
        builder.set("#ProfessionName.Text", "");
        builder.set("#LevelText.Text", "");
        builder.set("#XpGainText.Text", "");
        builder.set("#ProgressText.Text", "");
    }

    @Override
    public boolean update(@Nonnull UICommandBuilder builder, @Nonnull UUID playerId) {
        long now = System.currentTimeMillis();

        // Check if there's a pending notification
        var pending = pendingNotifications.remove(playerId);
        if (pending != null) {
            // Show the notification
            builder.set("#ProfessionName.Text", getProfessionIcon(pending.profession) + " " + pending.profession.getDisplayName());
            builder.set("#LevelText.Text", "Lv." + pending.level);
            builder.set("#XpGainText.Text", "+" + pending.xpGained + " XP");
            builder.set("#ProgressText.Text", pending.currentXp + "/" + pending.xpNeeded + " XP");

            notificationTime.put(playerId, now);
            return true;
        }

        // Check if notification should be cleared (timeout)
        Long showTime = notificationTime.get(playerId);
        if (showTime != null && (now - showTime) > DISPLAY_DURATION_MS) {
            // Clear the notification
            builder.set("#ProfessionName.Text", "");
            builder.set("#LevelText.Text", "");
            builder.set("#XpGainText.Text", "");
            builder.set("#ProgressText.Text", "");

            notificationTime.remove(playerId);
            return true;
        }

        return false;
    }

    @Override
    public void removePlayer(@Nonnull UUID playerId) {
        pendingNotifications.remove(playerId);
        notificationTime.remove(playerId);
    }

    @Override
    public boolean isEnabled() {
        return true; // Always enabled when leveling module is active
    }

    /**
     * Record XP gain for HUD display.
     * Called by the leveling system when XP is awarded.
     */
    public void recordXpGain(@Nonnull UUID playerId, @Nonnull ProfessionType profession, long xpGained) {
        // Get player data for current XP/level
        var dataOpt = system.getPlayerData(playerId);
        if (dataOpt.isEmpty()) {
            return;
        }

        var data = dataOpt.get();

        // Check if HUD display is enabled for this player
        if (!data.isHudEnabled()) {
            return;
        }

        var profData = data.getProfession(profession);
        int level = profData != null ? profData.getLevel() : 1;
        long currentXp = profData != null ? profData.getCurrentXp() : 0;
        long xpNeeded = profData != null ? profData.getXpToNextLevel() : 100;

        // Check if HUD is ready
        if (hudModule != null && hudModule.isHudReady(playerId)) {
            // Send immediate update
            var builder = new UICommandBuilder();
            builder.set("#ProfessionName.Text", getProfessionIcon(profession) + " " + profession.getDisplayName());
            builder.set("#LevelText.Text", "Lv." + level);
            builder.set("#XpGainText.Text", "+" + xpGained + " XP");
            builder.set("#ProgressText.Text", currentXp + "/" + xpNeeded + " XP");

            hudModule.sendImmediateUpdate(playerId, builder);
            notificationTime.put(playerId, System.currentTimeMillis());
        } else {
            // Queue for later
            pendingNotifications.put(playerId, new XpNotification(profession, xpGained, level, currentXp, xpNeeded));
        }
    }

    private String getProfessionIcon(ProfessionType prof) {
        return switch (prof) {
            case COMBAT -> "[C]";
            case MINING -> "[M]";
            case BUILDING -> "[B]";
            case LOGGING -> "[L]";
            case GATHERING -> "[G]";
        };
    }
}
