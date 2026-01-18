package com.livinglands.listeners;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.LivingLandsPlugin;
import com.livinglands.core.config.ModConfig;
import com.livinglands.metabolism.MetabolismSystem;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Listener for player death and respawn handling.
 * Resets metabolism stats when player respawns after death.
 *
 * Uses EntityRemoveEvent + AddPlayerToWorldEvent pattern since
 * LivingEntityDeathEvent is not available in the current API.
 */
public class DeathHandlerListener {

    private final LivingLandsPlugin plugin;
    private final MetabolismSystem metabolismSystem;
    private final ModConfig config;

    // Track players who died (removed with low health) and need stats reset on respawn
    private final Set<UUID> deadPlayers = ConcurrentHashMap.newKeySet();

    public DeathHandlerListener(@Nonnull LivingLandsPlugin plugin) {
        this.plugin = plugin;
        this.metabolismSystem = plugin.getMetabolismSystem();
        this.config = plugin.getModConfig();
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Listen for entity removal (includes player death)
        eventRegistry.registerGlobal(EntityRemoveEvent.class, this::onEntityRemove);

        // Listen for player added to world (respawn)
        eventRegistry.registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerAddedToWorld);

        plugin.getLogger().at(Level.INFO).log("Registered death handler listener");
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

            // Check metabolism data - if hunger/thirst is critical, assume death
            var dataOpt = metabolismSystem.getPlayerData(playerId);
            if (dataOpt.isPresent()) {
                var data = dataOpt.get();
                var debuffs = config.debuffs();

                // Check if death was likely from starvation/dehydration
                boolean likelyMetabolismDeath =
                    (debuffs.hunger().enabled() && data.getHunger() <= debuffs.hunger().damageStartThreshold()) ||
                    (debuffs.thirst().enabled() && data.getThirst() <= debuffs.thirst().damageStartThreshold());

                if (likelyMetabolismDeath) {
                    deadPlayers.add(playerId);
                    plugin.getLogger().at(Level.INFO).log(
                        "Player %s removed (possible metabolism death: hunger=%.0f, thirst=%.0f)",
                        playerId, data.getHunger(), data.getThirst()
                    );
                }
            }

        } catch (Exception e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log("Error processing entity remove event");
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

                plugin.getLogger().at(Level.INFO).log(
                    "Player %s respawned - metabolism stats reset",
                    playerId
                );
            }

        } catch (Exception e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log("Error processing respawn event");
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
        var metabolism = config.metabolism();
        var dataOpt = metabolismSystem.getPlayerData(playerId);

        if (dataOpt.isPresent()) {
            var data = dataOpt.get();
            data.reset(
                System.currentTimeMillis(),
                metabolism.initialHunger(),
                metabolism.initialThirst(),
                metabolism.initialEnergy()
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
