package com.livinglands.core.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * Combined HUD for Living Lands that contains all HUD elements.
 * This is the single CustomUIHud registered with the player's HudManager.
 *
 * UI file: Common/UI/Custom/Hud/LivingLandsHud.ui
 */
public class LivingLandsHud extends CustomUIHud {

    private static final String UI_FILE = "Hud/LivingLandsHud.ui";

    private final UUID playerId;
    private final List<HudElement> elements;

    public LivingLandsHud(@Nonnull PlayerRef playerRef, @Nonnull UUID playerId,
                          @Nonnull List<HudElement> elements) {
        super(playerRef);
        this.playerId = playerId;
        this.elements = elements;
    }

    @Override
    protected void build(UICommandBuilder builder) {
        builder.append(UI_FILE);

        // Build initial values for all enabled elements
        for (var element : elements) {
            if (element.isEnabled()) {
                element.buildInitialValues(builder, playerId);
            }
        }
    }

    /**
     * Update all HUD elements.
     * Called periodically by the HudModule.
     *
     * @return true if any updates were made
     */
    public boolean updateElements() {
        var builder = new UICommandBuilder();
        boolean hasUpdates = false;

        for (var element : elements) {
            if (element.isEnabled()) {
                if (element.update(builder, playerId)) {
                    hasUpdates = true;
                }
            }
        }

        if (hasUpdates) {
            update(false, builder);
        }

        return hasUpdates;
    }

    /**
     * Force update specific element selectors.
     * Used for immediate updates like XP notifications.
     */
    public void updateImmediate(@Nonnull UICommandBuilder builder) {
        update(false, builder);
    }

    public UUID getPlayerId() {
        return playerId;
    }
}
