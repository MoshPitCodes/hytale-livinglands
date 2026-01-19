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
import java.util.logging.Level;

/**
 * ECS System for detecting player death events.
 * Listens to KillFeedEvent.DecedentMessage which fires when an entity dies.
 * When a player dies, immediately resets their metabolism to initial values.
 */
public class PlayerDeathSystem extends EntityEventSystem<EntityStore, KillFeedEvent.DecedentMessage> {

    private final MetabolismSystem metabolismSystem;
    private final MetabolismModuleConfig config;
    private final HytaleLogger logger;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

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

            // Reset metabolism immediately on death
            // This ensures stats are fresh when the player respawns
            var dataOpt = metabolismSystem.getPlayerData(playerId);
            if (dataOpt.isPresent()) {
                var data = dataOpt.get();
                var metabolism = config.metabolism;

                logger.at(Level.INFO).log(
                    "Player %s died (hunger=%.0f, thirst=%.0f, energy=%.0f) - resetting metabolism",
                    playerId, data.getHunger(), data.getThirst(), data.getEnergy()
                );

                // Reset to initial values
                data.reset(
                    System.currentTimeMillis(),
                    metabolism.initialHunger,
                    metabolism.initialThirst,
                    metabolism.initialEnergy
                );

                logger.at(Level.INFO).log(
                    "Reset metabolism for player %s on death (hunger=%.0f, thirst=%.0f, energy=%.0f)",
                    playerId, metabolism.initialHunger, metabolism.initialThirst, metabolism.initialEnergy
                );
            } else {
                logger.at(Level.INFO).log("Player %s died - no metabolism data found", playerId);
            }

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error processing player death event");
        }
    }
}
