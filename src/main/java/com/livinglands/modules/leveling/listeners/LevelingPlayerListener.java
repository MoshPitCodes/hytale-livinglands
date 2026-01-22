package com.livinglands.modules.leveling.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.livinglands.modules.leveling.LevelingModule;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.ability.AbilitySystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Listener for player lifecycle events to initialize/save leveling data.
 *
 * Handles:
 * - Player connect: Load leveling data from disk
 * - Player ready: Apply permanent ability buffs (after ECS is ready)
 * - Player disconnect: Save and clean up leveling data
 */
public class LevelingPlayerListener {

    private final LevelingSystem system;
    private final HytaleLogger logger;
    @Nullable
    private AbilitySystem abilitySystem;

    public LevelingPlayerListener(@Nonnull LevelingModule module,
                                  @Nonnull LevelingSystem system,
                                  @Nonnull HytaleLogger logger) {
        this.system = system;
        this.logger = logger;
    }

    public void setAbilitySystem(@Nullable AbilitySystem abilitySystem) {
        this.abilitySystem = abilitySystem;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.register(PlayerConnectEvent.class, this::onPlayerConnect);
        eventRegistry.registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        eventRegistry.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        logger.at(Level.FINE).log("Leveling player lifecycle listener registered");
    }

    private void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        var playerId = event.getPlayerRef().getUuid();
        system.initializePlayer(playerId);
    }

    /**
     * Called when player ECS is ready - apply permanent buffs.
     */
    @SuppressWarnings("removal") // Player.getPlayerRef() is deprecated for removal but no alternative exists yet
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        var player = event.getPlayer();
        var playerId = player.getPlayerRef().getUuid();

        // Apply permanent ability buffs (Tier 3 abilities like Battle Hardened)
        if (abilitySystem != null) {
            abilitySystem.onPlayerReady(playerId);
            logger.at(Level.FINE).log("Applied permanent buffs for player %s", playerId);
        }
    }

    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        var playerId = event.getPlayerRef().getUuid();
        system.removePlayer(playerId);
    }
}
