package com.livinglands.modules.metabolism.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.event.KillFeedEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.metabolism.MetabolismSystem;
import com.livinglands.modules.metabolism.config.MetabolismModuleConfig;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * ECS System for detecting player death events.
 * Listens to KillFeedEvent.DecedentMessage which fires when an entity dies.
 * When a player dies, tracks their UUID for metabolism reset on respawn.
 */
public class PlayerDeathSystem extends EntityEventSystem<EntityStore, KillFeedEvent.DecedentMessage> {

    private final MetabolismSystem metabolismSystem;
    private final MetabolismModuleConfig config;
    private final HytaleLogger logger;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    // Track players who have died and need stats reset on respawn
    private static final Set<UUID> deadPlayers = ConcurrentHashMap.newKeySet();

    public PlayerDeathSystem(@Nonnull MetabolismSystem metabolismSystem,
                              @Nonnull MetabolismModuleConfig config,
                              @Nonnull HytaleLogger logger) {
        super(KillFeedEvent.DecedentMessage.class);
        this.metabolismSystem = metabolismSystem;
        this.config = config;
        this.logger = logger;
        this.playerRefType = PlayerRef.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int entityIndex,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       KillFeedEvent.DecedentMessage event) {
        try {
            // Get player reference from the entity (the one who died)
            PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
            if (playerRef == null) {
                return; // Not a player entity
            }

            var playerId = playerRef.getUuid();

            // Track this player for metabolism reset on respawn
            deadPlayers.add(playerId);

            var dataOpt = metabolismSystem.getPlayerData(playerId);
            if (dataOpt.isPresent()) {
                var data = dataOpt.get();
                logger.at(Level.INFO).log(
                    "Player %s died - tracking for metabolism reset on respawn (hunger=%.0f, thirst=%.0f, energy=%.0f)",
                    playerId, data.getHunger(), data.getThirst(), data.getEnergy()
                );
            } else {
                logger.at(Level.INFO).log("Player %s died - tracking for metabolism reset on respawn", playerId);
            }

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error processing player death event");
        }
    }

    /**
     * Check if a player has died and needs metabolism reset.
     * Called when player respawns/rejoins the world.
     *
     * @param playerId The player's UUID
     * @return true if the player died and needs reset
     */
    public static boolean checkAndClearDeathFlag(UUID playerId) {
        return deadPlayers.remove(playerId);
    }

    /**
     * Check if a player has died (without clearing the flag).
     *
     * @param playerId The player's UUID
     * @return true if the player died
     */
    public static boolean hasDied(UUID playerId) {
        return deadPlayers.contains(playerId);
    }

    /**
     * Clear death tracking for a player.
     *
     * @param playerId The player's UUID
     */
    public static void clearDeathFlag(UUID playerId) {
        deadPlayers.remove(playerId);
    }

    /**
     * Reset metabolism stats for a player after respawn.
     *
     * @param playerId The player's UUID
     */
    public void resetPlayerMetabolism(UUID playerId) {
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
            logger.at(Level.INFO).log(
                "Reset metabolism for player %s after respawn (hunger=%.0f, thirst=%.0f, energy=%.0f)",
                playerId, metabolism.initialHunger, metabolism.initialThirst, metabolism.initialEnergy
            );
        }
    }
}
