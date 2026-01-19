package com.livinglands.modules.leveling.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.livinglands.modules.leveling.LevelingModule;
import com.livinglands.modules.leveling.LevelingSystem;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Listener for player lifecycle events to initialize/save leveling data.
 *
 * Note: HUD initialization is handled by HudModule (initialized via MetabolismPlayerListener).
 * This listener only handles leveling data lifecycle.
 */
public class LevelingPlayerListener {

    private final LevelingSystem system;
    private final HytaleLogger logger;

    public LevelingPlayerListener(@Nonnull LevelingModule module,
                                  @Nonnull LevelingSystem system,
                                  @Nonnull HytaleLogger logger) {
        this.system = system;
        this.logger = logger;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.register(PlayerConnectEvent.class, this::onPlayerConnect);
        eventRegistry.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        logger.at(Level.INFO).log("Leveling player lifecycle listener registered");
    }

    private void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        var playerId = event.getPlayerRef().getUuid();
        system.initializePlayer(playerId);
    }

    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        var playerId = event.getPlayerRef().getUuid();
        system.removePlayer(playerId);
    }
}
