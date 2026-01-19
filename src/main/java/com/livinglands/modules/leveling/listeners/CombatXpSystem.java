package com.livinglands.modules.leveling.listeners;

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
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * ECS System for awarding Combat XP when players kill entities.
 * Extends EntityEventSystem to receive KillFeedEvent.KillerMessage from the ECS.
 */
public class CombatXpSystem extends EntityEventSystem<EntityStore, KillFeedEvent.KillerMessage> {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    private final ComponentType<EntityStore, NPCEntity> npcEntityType;

    // Deduplication tracking to prevent double XP from same kill
    private static final Map<String, Long> recentKills = new ConcurrentHashMap<>();
    private static final long DEDUP_WINDOW_MS = 1000; // 1 second dedup window

    public CombatXpSystem(@Nonnull LevelingSystem system,
                          @Nonnull LevelingModuleConfig config,
                          @Nonnull HytaleLogger logger) {
        super(KillFeedEvent.KillerMessage.class);
        this.system = system;
        this.config = config;
        this.logger = logger;
        this.playerRefType = PlayerRef.getComponentType();
        this.npcEntityType = NPCEntity.getComponentType();
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
                       KillFeedEvent.KillerMessage event) {
        try {
            // Get player reference from the entity (the killer)
            PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
            if (playerRef == null) {
                return;
            }

            var playerId = playerRef.getUuid();

            // Create dedup key
            String dedupKey = playerId.toString() + "_" + System.currentTimeMillis() / DEDUP_WINDOW_MS;
            long now = System.currentTimeMillis();

            // Check for duplicate
            Long lastKill = recentKills.get(dedupKey);
            if (lastKill != null && (now - lastKill) < DEDUP_WINDOW_MS) {
                return; // Duplicate kill event
            }
            recentKills.put(dedupKey, now);

            // Clean up old entries periodically
            if (recentKills.size() > 1000) {
                recentKills.entrySet().removeIf(e -> (now - e.getValue()) > DEDUP_WINDOW_MS * 2);
            }

            // Get XP for the kill (try to get entity type if available)
            int xp = config.xpSources.defaultMobKillXp; // Default mob kill XP

            // Award XP
            system.awardXp(playerId, ProfessionType.COMBAT, xp);

            logger.at(Level.FINE).log("Awarded %d Combat XP to player %s for kill",
                xp, playerId);

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error processing combat XP");
        }
    }
}
