package com.livinglands.modules.metabolism.listeners;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.metabolism.MetabolismModule;
import com.livinglands.modules.metabolism.MetabolismSystem;
import com.livinglands.modules.metabolism.config.MetabolismModuleConfig;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Listener for player death and respawn handling.
 * Resets metabolism stats when player respawns after death.
 *
 * Uses EntityRemoveEvent to detect death (by checking for DeathComponent)
 * and AddPlayerToWorldEvent to detect respawn.
 */
public class DeathHandlerListener {

    private final MetabolismModule module;
    private final MetabolismSystem metabolismSystem;
    private final MetabolismModuleConfig config;
    private final HytaleLogger logger;

    // Track players who have died and need stats reset on respawn
    private final Set<UUID> deadPlayers = ConcurrentHashMap.newKeySet();

    public DeathHandlerListener(@Nonnull MetabolismModule module) {
        this.module = module;
        this.metabolismSystem = module.getSystem();
        this.config = module.getConfig();
        this.logger = module.getContext().logger();
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Listen for entity removal (includes player death)
        eventRegistry.registerGlobal(EntityRemoveEvent.class, this::onEntityRemove);

        // Listen for player added to world (respawn)
        eventRegistry.registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerAddedToWorld);

        logger.at(Level.INFO).log("Registered death handler listener");
    }

    @SuppressWarnings("deprecation") // getPlayerRef() is deprecated but required for UUID access
    private void onEntityRemove(@Nonnull EntityRemoveEvent event) {
        try {
            var entity = event.getEntity();
            if (entity == null) {
                return;
            }

            // Check if this is a player
            if (!(entity instanceof Player player)) {
                return;
            }

            var playerRef = player.getPlayerRef();
            if (playerRef == null) {
                return;
            }
            var playerId = playerRef.getUuid();

            // Check if the player has a DeathComponent (indicates actual death, not disconnect)
            var holder = entity.toHolder();
            if (holder != null) {
                var deathComponent = holder.getComponent(DeathComponent.getComponentType());
                if (deathComponent != null) {
                    // Player actually died - track for respawn reset
                    deadPlayers.add(playerId);

                    var dataOpt = metabolismSystem.getPlayerData(playerId);
                    if (dataOpt.isPresent()) {
                        var data = dataOpt.get();
                        logger.at(Level.INFO).log(
                            "Player %s died - tracking for respawn reset (hunger=%.0f, thirst=%.0f, energy=%.0f)",
                            playerId, data.getHunger(), data.getThirst(), data.getEnergy()
                        );
                    }
                }
            }

        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("Error processing entity remove event");
        }
    }

    @SuppressWarnings("deprecation")
    private void onPlayerAddedToWorld(@Nonnull AddPlayerToWorldEvent event) {
        try {
            var holder = event.getHolder();
            if (holder == null) {
                return;
            }

            // Get player UUID from the holder using Player component
            var playerId = getPlayerIdFromHolder(holder);
            if (playerId == null) {
                return;
            }

            // Check if this player died and needs stats reset
            if (deadPlayers.remove(playerId)) {
                resetPlayerStats(playerId);

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
    @SuppressWarnings("deprecation") // getPlayerRef() is deprecated but required for UUID access
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
     * Resets player metabolism stats to initial values.
     */
    private void resetPlayerStats(UUID playerId) {
        var metabolism = config.metabolism;
        var dataOpt = metabolismSystem.getPlayerData(playerId);

        if (dataOpt.isPresent()) {
            var data = dataOpt.get();
            data.reset(
                System.currentTimeMillis(),
                metabolism.initialHunger,
                metabolism.initialThirst,
                metabolism.initialEnergy
            );
        }
    }

    /**
     * Cleans up tracking for a player.
     */
    public void removePlayer(UUID playerId) {
        deadPlayers.remove(playerId);
    }
}
