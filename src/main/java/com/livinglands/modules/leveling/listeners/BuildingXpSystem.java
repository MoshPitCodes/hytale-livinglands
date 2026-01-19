package com.livinglands.modules.leveling.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * ECS System for awarding Building XP when players place blocks.
 * Extends EntityEventSystem to receive PlaceBlockEvent from the ECS.
 */
public class BuildingXpSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    public BuildingXpSystem(@Nonnull LevelingSystem system,
                            @Nonnull LevelingModuleConfig config,
                            @Nonnull HytaleLogger logger) {
        super(PlaceBlockEvent.class);
        this.system = system;
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
                       PlaceBlockEvent event) {
        try {
            // Get player reference from the entity
            PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
            if (playerRef == null) {
                return;
            }

            var playerId = playerRef.getUuid();

            // Award XP for placing a block
            int xp = config.xpSources.blockPlaceXp;
            if (xp <= 0) {
                return;
            }

            system.awardXp(playerId, ProfessionType.BUILDING, xp);

            logger.at(Level.FINE).log("Awarded %d Building XP to player %s for placing block",
                xp, playerId);

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error processing building XP");
        }
    }
}
