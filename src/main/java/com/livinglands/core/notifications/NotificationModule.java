package com.livinglands.core.notifications;

import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.TempAssetIdUtil;
import com.livinglands.api.AbstractModule;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.PlayerSession;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Central notification module for Living Lands.
 * Provides a unified API for displaying notifications to players using various styles:
 * - Title/Subtitle (fullscreen text)
 * - Chat messages
 * - Future: Toast notifications, action bar, custom HUD popups
 *
 * This module should be loaded early as other modules depend on it.
 */
public final class NotificationModule extends AbstractModule {

    public static final String ID = "notifications";
    public static final String NAME = "Notification System";
    public static final String VERSION = "1.0.0";

    // Default title display settings
    private static final float DEFAULT_DURATION = 3.0f;      // seconds
    private static final float DEFAULT_FADE_IN = 0.5f;       // seconds
    private static final float DEFAULT_FADE_OUT = 0.5f;      // seconds
    private static final String DEFAULT_ZONE = "";           // empty = default

    private PlayerRegistry playerRegistry;

    public NotificationModule() {
        super(ID, NAME, VERSION, Set.of());
    }

    @Override
    protected void onSetup() {
        this.playerRegistry = context.playerRegistry();
        logger.at(Level.FINE).log("[%s] Notification module initialized", name);
    }

    @Override
    protected void onStart() {
        logger.at(Level.FINE).log("[%s] Notification module started", name);
    }

    @Override
    protected void onShutdown() {
        logger.at(Level.FINE).log("[%s] Notification module shutdown", name);
    }

    // ========== Title Notifications ==========

    /**
     * Show a title notification to a player.
     *
     * @param playerId Player's UUID
     * @param title Main title text
     * @param subtitle Subtitle text (can be null)
     */
    public void showTitle(@Nonnull UUID playerId, @Nonnull String title, @Nullable String subtitle) {
        showTitle(playerId, title, subtitle, DEFAULT_DURATION, DEFAULT_FADE_IN, DEFAULT_FADE_OUT);
    }

    /**
     * Show a title notification to a player with custom timing.
     *
     * @param playerId Player's UUID
     * @param title Main title text
     * @param subtitle Subtitle text (can be null)
     * @param duration How long to display (seconds)
     * @param fadeIn Fade in duration (seconds)
     * @param fadeOut Fade out duration (seconds)
     */
    public void showTitle(@Nonnull UUID playerId, @Nonnull String title, @Nullable String subtitle,
                          float duration, float fadeIn, float fadeOut) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) {
            return;
        }

        var session = sessionOpt.get();
        showTitleToSession(session, title, subtitle, duration, fadeIn, fadeOut);
    }

    /**
     * Show a title notification to a player session.
     *
     * @param session Player's session
     * @param title Main title text
     * @param subtitle Subtitle text (can be null)
     */
    public void showTitle(@Nonnull PlayerSession session, @Nonnull String title, @Nullable String subtitle) {
        showTitleToSession(session, title, subtitle, DEFAULT_DURATION, DEFAULT_FADE_IN, DEFAULT_FADE_OUT);
    }

    /**
     * Show a title notification with colored messages.
     *
     * @param playerId Player's UUID
     * @param title Title message (with color)
     * @param subtitle Subtitle message (with color, can be null)
     */
    public void showTitle(@Nonnull UUID playerId, @Nonnull Message title, @Nullable Message subtitle) {
        showTitle(playerId, title, subtitle, DEFAULT_DURATION, DEFAULT_FADE_IN, DEFAULT_FADE_OUT);
    }

    /**
     * Show a title notification with colored messages and custom timing.
     *
     * @param playerId Player's UUID
     * @param title Title message (with color)
     * @param subtitle Subtitle message (with color, can be null)
     * @param duration Display duration (seconds)
     * @param fadeIn Fade in duration (seconds)
     * @param fadeOut Fade out duration (seconds)
     */
    public void showTitle(@Nonnull UUID playerId, @Nonnull Message title, @Nullable Message subtitle,
                          float duration, float fadeIn, float fadeOut) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) {
            return;
        }

        var session = sessionOpt.get();
        showTitleToSession(session, title, subtitle, duration, fadeIn, fadeOut);
    }

    /**
     * Internal method to show title to a session.
     */
    private void showTitleToSession(PlayerSession session, String title, @Nullable String subtitle,
                                     float duration, float fadeIn, float fadeOut) {
        Message titleMsg = Message.raw(title);
        Message subtitleMsg = subtitle != null ? Message.raw(subtitle) : Message.raw("");

        showTitleToSession(session, titleMsg, subtitleMsg, duration, fadeIn, fadeOut);
    }

    /**
     * Internal method to show title Message to a session.
     */
    private void showTitleToSession(PlayerSession session, Message title, @Nullable Message subtitle,
                                     float duration, float fadeIn, float fadeOut) {
        try {
            PlayerRef playerRef = session.getPlayerRef();
            if (playerRef == null) {
                return;
            }

            Message subtitleMsg = subtitle != null ? subtitle : Message.raw("");

            // Execute on world thread for thread safety
            session.executeOnWorld(() -> {
                try {
                    EventTitleUtil.showEventTitleToPlayer(
                        playerRef,
                        title,
                        subtitleMsg,
                        false,              // animated (false = instant appear)
                        DEFAULT_ZONE,       // zone
                        duration,           // display duration
                        fadeIn,             // fade in
                        fadeOut             // fade out
                    );
                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log(
                        "[%s] Failed to show title on world thread", name);
                }
            });

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "[%s] Failed to show title notification", name);
        }
    }

    /**
     * Hide any active title from a player.
     *
     * @param playerId Player's UUID
     */
    public void hideTitle(@Nonnull UUID playerId) {
        hideTitle(playerId, DEFAULT_FADE_OUT);
    }

    /**
     * Hide any active title from a player with custom fade out.
     *
     * @param playerId Player's UUID
     * @param fadeOut Fade out duration (seconds)
     */
    public void hideTitle(@Nonnull UUID playerId, float fadeOut) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) {
            return;
        }

        var session = sessionOpt.get();
        try {
            PlayerRef playerRef = session.getPlayerRef();
            if (playerRef == null) {
                return;
            }

            session.executeOnWorld(() -> {
                try {
                    EventTitleUtil.hideEventTitleFromPlayer(playerRef, fadeOut);
                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log(
                        "[%s] Failed to hide title on world thread", name);
                }
            });

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "[%s] Failed to hide title notification", name);
        }
    }

    // ========== Sound Notifications ==========

    /**
     * Play a 2D sound effect to a player (not positional, plays directly in their ears).
     *
     * @param playerId Player's UUID
     * @param soundEventName The sound event name (e.g., "SFX_Discovery_Z1_Short")
     */
    public void playSound(@Nonnull UUID playerId, @Nonnull String soundEventName) {
        playSound(playerId, soundEventName, SoundCategory.UI, DEFAULT_SOUND_VOLUME, DEFAULT_SOUND_PITCH);
    }

    /**
     * Play a 2D sound effect to a player with custom volume and pitch.
     *
     * @param playerId Player's UUID
     * @param soundEventName The sound event name
     * @param category Sound category (UI, SFX, Ambient, Music)
     * @param volume Volume multiplier (0.0 - 1.0)
     * @param pitch Pitch multiplier (0.5 - 2.0 typical range)
     */
    @SuppressWarnings("removal") // TempAssetIdUtil is deprecated but no alternative available yet
    public void playSound(@Nonnull UUID playerId, @Nonnull String soundEventName,
                          @Nonnull SoundCategory category, float volume, float pitch) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) {
            return;
        }

        var session = sessionOpt.get();
        try {
            PlayerRef playerRef = session.getPlayerRef();
            if (playerRef == null) {
                return;
            }

            // Resolve sound event name to ID
            int soundEventId = TempAssetIdUtil.getSoundEventIndex(soundEventName);
            if (soundEventId < 0) {
                logger.at(Level.WARNING).log(
                    "[%s] Unknown sound event: %s", name, soundEventName);
                return;
            }

            // Execute on world thread for thread safety
            session.executeOnWorld(() -> {
                try {
                    SoundUtil.playSoundEvent2dToPlayer(playerRef, soundEventId, category, volume, pitch);
                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log(
                        "[%s] Failed to play sound on world thread", name);
                }
            });

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                "[%s] Failed to play sound notification", name);
        }
    }

    // ========== Convenience Methods for Common Notifications ==========

    // Color constants for notification types (from ColorUtil)
    private static final String COLOR_SUCCESS = com.livinglands.util.ColorUtil.getHexColor("notif_success");
    private static final String COLOR_WARNING = com.livinglands.util.ColorUtil.getHexColor("notif_warning");
    private static final String COLOR_ERROR = com.livinglands.util.ColorUtil.getHexColor("notif_error");
    private static final String COLOR_INFO = com.livinglands.util.ColorUtil.getHexColor("notif_info");
    private static final String COLOR_UNLOCK = com.livinglands.util.ColorUtil.getHexColor("notif_unlock");
    private static final String COLOR_SUBTITLE = com.livinglands.util.ColorUtil.getHexColor("notif_subtitle");

    // Sound event names for notifications (resolved to IDs at runtime)
    private static final String SOUND_UNLOCK = "SFX_Discovery_Z1_Short";  // Light discovery chime
    private static final String SOUND_SUCCESS = "SFX_Discovery_Z1_Short";
    private static final String SOUND_WARNING = "SFX_Incorrect_Tool";
    private static final String SOUND_ERROR = "SFX_Incorrect_Tool";

    // Default sound settings
    private static final float DEFAULT_SOUND_VOLUME = 1.0f;
    private static final float DEFAULT_SOUND_PITCH = 1.0f;

    /**
     * Show a success notification (green title).
     */
    public void showSuccess(@Nonnull UUID playerId, @Nonnull String title, @Nullable String subtitle) {
        Message titleMsg = Message.raw(title).color(COLOR_SUCCESS);
        Message subtitleMsg = subtitle != null ? Message.raw(subtitle).color(COLOR_SUBTITLE) : null;
        showTitle(playerId, titleMsg, subtitleMsg);
    }

    /**
     * Show a warning notification (yellow title).
     */
    public void showWarning(@Nonnull UUID playerId, @Nonnull String title, @Nullable String subtitle) {
        Message titleMsg = Message.raw(title).color(COLOR_WARNING);
        Message subtitleMsg = subtitle != null ? Message.raw(subtitle).color(COLOR_SUBTITLE) : null;
        showTitle(playerId, titleMsg, subtitleMsg);
    }

    /**
     * Show an error notification (red title).
     */
    public void showError(@Nonnull UUID playerId, @Nonnull String title, @Nullable String subtitle) {
        Message titleMsg = Message.raw(title).color(COLOR_ERROR);
        Message subtitleMsg = subtitle != null ? Message.raw(subtitle).color(COLOR_SUBTITLE) : null;
        showTitle(playerId, titleMsg, subtitleMsg);
    }

    /**
     * Show an info notification (blue title).
     */
    public void showInfo(@Nonnull UUID playerId, @Nonnull String title, @Nullable String subtitle) {
        Message titleMsg = Message.raw(title).color(COLOR_INFO);
        Message subtitleMsg = subtitle != null ? Message.raw(subtitle).color(COLOR_SUBTITLE) : null;
        showTitle(playerId, titleMsg, subtitleMsg);
    }

    /**
     * Show an achievement/unlock notification (purple title) with sound.
     */
    public void showUnlock(@Nonnull UUID playerId, @Nonnull String title, @Nullable String subtitle) {
        showUnlock(playerId, title, subtitle, true);
    }

    /**
     * Show an achievement/unlock notification (purple title) with optional sound.
     *
     * @param playerId Player's UUID
     * @param title Title text
     * @param subtitle Subtitle text (can be null)
     * @param withSound Whether to play the unlock sound effect
     */
    public void showUnlock(@Nonnull UUID playerId, @Nonnull String title, @Nullable String subtitle,
                           boolean withSound) {
        Message titleMsg = Message.raw(title).color(COLOR_UNLOCK);
        Message subtitleMsg = subtitle != null ? Message.raw(subtitle).color(COLOR_SUBTITLE) : null;
        showTitle(playerId, titleMsg, subtitleMsg, 4.0f, 0.5f, 1.0f);  // Longer duration for unlocks

        if (withSound) {
            playSound(playerId, SOUND_UNLOCK);
        }
    }

    // ========== Chat Message Notifications ==========

    /**
     * Send a chat message to a player with a specific color.
     *
     * @param playerId Player's UUID
     * @param message The message text
     * @param colorName Color name from ColorUtil (e.g., "red", "green", "purple")
     */
    public void sendChat(@Nonnull UUID playerId, @Nonnull String message, @Nonnull String colorName) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) {
            return;
        }

        var player = sessionOpt.get().getPlayer();
        if (player == null) {
            return;
        }

        try {
            player.sendMessage(Message.raw(message).color(com.livinglands.util.ColorUtil.getHexColor(colorName)));
        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("[%s] Failed to send chat message", name);
        }
    }

    /**
     * Send a chat message to a player with a hex color.
     *
     * @param playerId Player's UUID
     * @param message The message text
     * @param hexColor Hex color code (e.g., "#FF0000")
     */
    public void sendChatHex(@Nonnull UUID playerId, @Nonnull String message, @Nonnull String hexColor) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) {
            return;
        }

        var player = sessionOpt.get().getPlayer();
        if (player == null) {
            return;
        }

        try {
            player.sendMessage(Message.raw(message).color(hexColor));
        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("[%s] Failed to send chat message", name);
        }
    }

    /**
     * Send an error chat message (red).
     *
     * @param playerId Player's UUID
     * @param message The error message
     */
    public void sendChatError(@Nonnull UUID playerId, @Nonnull String message) {
        sendChat(playerId, message, "red");
    }

    /**
     * Send a success chat message (green).
     *
     * @param playerId Player's UUID
     * @param message The success message
     */
    public void sendChatSuccess(@Nonnull UUID playerId, @Nonnull String message) {
        sendChat(playerId, message, "green");
    }

    /**
     * Send a warning chat message (yellow/gold).
     *
     * @param playerId Player's UUID
     * @param message The warning message
     */
    public void sendChatWarning(@Nonnull UUID playerId, @Nonnull String message) {
        sendChat(playerId, message, "gold");
    }

    /**
     * Send an info chat message (gray).
     *
     * @param playerId Player's UUID
     * @param message The info message
     */
    public void sendChatInfo(@Nonnull UUID playerId, @Nonnull String message) {
        sendChat(playerId, message, "gray");
    }

    /**
     * Send an ability-related chat message (purple).
     *
     * @param playerId Player's UUID
     * @param message The ability message
     */
    public void sendChatAbility(@Nonnull UUID playerId, @Nonnull String message) {
        sendChat(playerId, message, "purple");
    }

    /**
     * Send a buff/positive effect chat message (green).
     *
     * @param playerId Player's UUID
     * @param message The buff message
     */
    public void sendChatBuff(@Nonnull UUID playerId, @Nonnull String message) {
        sendChat(playerId, message, "green");
    }

    /**
     * Send a debuff/negative effect chat message (red).
     *
     * @param playerId Player's UUID
     * @param message The debuff message
     */
    public void sendChatDebuff(@Nonnull UUID playerId, @Nonnull String message) {
        sendChat(playerId, message, "red");
    }
}
