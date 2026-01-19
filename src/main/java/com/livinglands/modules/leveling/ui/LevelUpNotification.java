package com.livinglands.modules.leveling.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerSession;
import com.livinglands.modules.leveling.ability.AbilityType;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Utility class for displaying level-up notifications to players.
 * Logs level-up events for now - visual notifications can be added
 * when the appropriate UI API is available.
 */
public final class LevelUpNotification {

    private LevelUpNotification() {
        // Utility class, no instantiation
    }

    /**
     * Show a level-up notification to a player.
     * Currently logs the event - UI integration pending.
     *
     * @param session The player's session
     * @param profession The profession that leveled up
     * @param newLevel The new level achieved
     * @param logger Logger for debugging
     */
    public static void show(@Nonnull PlayerSession session, @Nonnull ProfessionType profession,
                            int newLevel, @Nonnull HytaleLogger logger) {
        try {
            var playerId = session.getPlayerId();

            // Log the level-up event
            logger.at(Level.INFO).log(
                "[Level Up] Player %s reached %s Level %d!",
                playerId, profession.getDisplayName(), newLevel
            );

            // TODO: Implement visual notification when Hytale UI API supports it
            // Options:
            // - Title/Subtitle using Hytale's title API
            // - Action bar message
            // - Custom HUD popup
            // - Chat message via player's message queue

            // For now, the level-up is communicated via the Skills HUD
            // which shows XP gain notifications

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to show level-up notification");
        }
    }

    /**
     * Show ability unlock notification.
     *
     * @param session The player's session
     * @param ability The ability that was unlocked
     * @param logger Logger for debugging
     */
    public static void showAbilityUnlock(@Nonnull PlayerSession session,
                                          @Nonnull AbilityType ability,
                                          @Nonnull HytaleLogger logger) {
        try {
            var playerId = session.getPlayerId();

            // Log the ability unlock
            logger.at(Level.INFO).log(
                "[Ability Unlock] Player %s unlocked %s (%s)!",
                playerId, ability.getDisplayName(), ability.getProfession().getDisplayName()
            );

            // TODO: Implement visual notification when UI API supports it

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to show ability unlock notification");
        }
    }

    /**
     * Get icon for a profession.
     */
    private static String getProfessionIcon(ProfessionType prof) {
        return switch (prof) {
            case COMBAT -> "[C]";
            case MINING -> "[M]";
            case BUILDING -> "[B]";
            case LOGGING -> "[L]";
            case GATHERING -> "[G]";
        };
    }

    /**
     * Get an encouragement message based on level.
     */
    private static String getEncouragementMessage(int level) {
        if (level == 10) return "Great start! Keep it up!";
        if (level == 25) return "You're getting skilled!";
        if (level == 50) return "Halfway to mastery!";
        if (level == 75) return "Almost there!";
        if (level == 99) return "MAXIMUM POWER ACHIEVED!";

        String[] messages = {
            "Nice work!",
            "Keep going!",
            "Great progress!",
            "Well done!",
            "Excellent!",
            "You're improving!",
            "Skill increased!"
        };
        return messages[(int) (Math.random() * messages.length)];
    }
}
