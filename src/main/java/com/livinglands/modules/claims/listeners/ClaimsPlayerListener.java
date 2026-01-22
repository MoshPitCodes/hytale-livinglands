package com.livinglands.modules.claims.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.ui.ClaimsUIProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Handles player lifecycle events for the Claims module.
 * Ensures proper cleanup of player-specific data on disconnect.
 */
public class ClaimsPlayerListener {

    private final ClaimSystem claimSystem;
    private final HytaleLogger logger;

    @Nullable
    private ClaimsUIProvider uiProvider;

    public ClaimsPlayerListener(@Nonnull ClaimSystem claimSystem,
                                @Nonnull HytaleLogger logger) {
        this.claimSystem = claimSystem;
        this.logger = logger;
    }

    public void setUIProvider(@Nullable ClaimsUIProvider uiProvider) {
        this.uiProvider = uiProvider;
    }

    /**
     * Registers event handlers with the event registry.
     */
    public void register(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        logger.at(Level.FINE).log("[Claims] Player lifecycle listener registered");
    }

    /**
     * Handles player disconnect - cleans up player-specific data.
     */
    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        var playerId = event.getPlayerRef().getUuid();

        // Clean up claim system data (admin bypass, pending previews)
        claimSystem.onPlayerDisconnect(playerId);

        // Clean up UI provider state
        if (uiProvider != null) {
            uiProvider.cleanupPlayerState(playerId);
        }

        logger.at(Level.FINE).log("[Claims] Cleaned up data for disconnected player: %s", playerId);
    }
}
