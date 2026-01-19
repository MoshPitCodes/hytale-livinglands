package com.livinglands.modules.metabolism.listeners;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.metabolism.MetabolismModule;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener for player respawn handling.
 * Resets metabolism stats when player respawns after death.
 *
 * Death detection is handled by PlayerDeathSystem (ECS event).
 * This listener handles the respawn side via AddPlayerToWorldEvent.
 */
public class DeathHandlerListener {

    private final MetabolismModule module;
    private final PlayerDeathSystem deathSystem;
    private final HytaleLogger logger;

    public DeathHandlerListener(@Nonnull MetabolismModule module,
                                 @Nonnull PlayerDeathSystem deathSystem) {
        this.module = module;
        this.deathSystem = deathSystem;
        this.logger = module.getContext().logger();
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Listen for player added to world (respawn)
        eventRegistry.registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerAddedToWorld);

        logger.at(Level.INFO).log("Registered death/respawn handler listener");
    }

    @SuppressWarnings("deprecation")
    private void onPlayerAddedToWorld(@Nonnull AddPlayerToWorldEvent event) {
        try {
            var holder = event.getHolder();
            if (holder == null) {
                return;
            }

            // Get player UUID from the holder
            var playerId = getPlayerIdFromHolder(holder);
            if (playerId == null) {
                return;
            }

            // Check if this player died and needs stats reset
            if (PlayerDeathSystem.checkAndClearDeathFlag(playerId)) {
                deathSystem.resetPlayerMetabolism(playerId);

                logger.at(Level.INFO).log(
                    "Player %s respawned - metabolism stats reset to initial values",
                    playerId
                );
            }

        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("Error processing respawn event");
        }
    }

    /**
     * Extracts player UUID from entity holder.
     */
    @SuppressWarnings("deprecation")
    private UUID getPlayerIdFromHolder(Holder<EntityStore> holder) {
        try {
            // Get the Player component from the holder
            var player = holder.getComponent(Player.getComponentType());
            if (player != null) {
                var playerRef = player.getPlayerRef();
                if (playerRef != null) {
                    return playerRef.getUuid();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Cleans up tracking for a player.
     */
    public void removePlayer(UUID playerId) {
        PlayerDeathSystem.clearDeathFlag(playerId);
    }
}
