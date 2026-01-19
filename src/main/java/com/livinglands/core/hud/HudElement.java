package com.livinglands.core.hud;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Interface for HUD elements that can be registered with the HudModule.
 * Each module can implement this to add their own section to the combined HUD.
 */
public interface HudElement {

    /**
     * Get the unique identifier for this HUD element.
     * Used to track and manage the element.
     */
    @Nonnull
    String getId();

    /**
     * Get the display name of this HUD element (for logging/debugging).
     */
    @Nonnull
    String getName();

    /**
     * Build the initial values for this element's UI components.
     * Called when the HUD is first created for a player.
     *
     * @param builder The UI command builder to add initial values to
     * @param playerId The player's UUID
     */
    void buildInitialValues(@Nonnull UICommandBuilder builder, @Nonnull UUID playerId);

    /**
     * Update this element's UI components for a player.
     * Called periodically by the HUD manager.
     *
     * @param builder The UI command builder to add updates to
     * @param playerId The player's UUID
     * @return true if any values were updated, false if no changes
     */
    boolean update(@Nonnull UICommandBuilder builder, @Nonnull UUID playerId);

    /**
     * Called when a player is removed (disconnects).
     * Clean up any player-specific state.
     *
     * @param playerId The player's UUID
     */
    void removePlayer(@Nonnull UUID playerId);

    /**
     * Check if this element is enabled and should be displayed.
     */
    boolean isEnabled();
}
