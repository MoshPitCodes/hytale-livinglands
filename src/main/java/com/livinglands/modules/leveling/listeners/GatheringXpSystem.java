package com.livinglands.modules.leveling.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.ability.handlers.GatheringAbilityHandler;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * ECS System for awarding Gathering XP when players pick up items.
 * Extends EntityEventSystem to receive InteractivelyPickupItemEvent from the ECS.
 */
public class GatheringXpSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    @Nullable
    private GatheringAbilityHandler abilityHandler;

    // Cooldown tracking to prevent XP spam
    private final Map<UUID, Long> lastXpAwardTime = new ConcurrentHashMap<>();
    private static final long XP_COOLDOWN_MS = 500; // 500ms cooldown between XP awards

    public GatheringXpSystem(@Nonnull LevelingSystem system,
                             @Nonnull LevelingModuleConfig config,
                             @Nonnull HytaleLogger logger) {
        super(InteractivelyPickupItemEvent.class);
        this.system = system;
        this.config = config;
        this.logger = logger;
        this.playerRefType = PlayerRef.getComponentType();
    }

    /**
     * Sets the gathering ability handler for triggering gathering abilities on item pickup.
     */
    public void setAbilityHandler(@Nullable GatheringAbilityHandler abilityHandler) {
        this.abilityHandler = abilityHandler;
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
                       InteractivelyPickupItemEvent event) {
        try {
            // Get player reference from the entity
            PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
            if (playerRef == null) {
                return;
            }

            var playerId = playerRef.getUuid();

            // Check cooldown
            long now = System.currentTimeMillis();
            Long lastTime = lastXpAwardTime.get(playerId);
            if (lastTime != null && (now - lastTime) < XP_COOLDOWN_MS) {
                return; // Still on cooldown
            }

            // Get item info and calculate XP
            String itemId = event.getItemStack().getItemId();
            int xp = config.xpSources.getGatherXp(itemId);

            if (xp <= 0) {
                // Default XP for any item pickup
                xp = 1;
            }

            // Award XP
            system.awardXp(playerId, ProfessionType.GATHERING, xp);
            lastXpAwardTime.put(playerId, now);

            logger.at(Level.FINE).log("Awarded %d Gathering XP to player %s for picking up %s",
                xp, playerId, itemId);

            // Trigger gathering abilities (Nature's Gift)
            if (abilityHandler != null) {
                abilityHandler.onItemGathered(playerId);
            }

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error processing gathering XP");
        }
    }
}
