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
import com.livinglands.modules.leveling.ability.handlers.CombatAbilityHandler;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    @Nullable
    private CombatAbilityHandler abilityHandler;

    // Deduplication tracking to prevent double XP from same kill
    // Key: "playerUUID:victimEntityId:roundedTimestamp", Value: exact timestamp
    private static final Map<String, Long> recentKills = new ConcurrentHashMap<>();
    private static final long DEDUP_WINDOW_MS = 1000; // 1 second dedup window
    private static final long TIMESTAMP_PRECISION_MS = 100; // Round to nearest 100ms for dedup key

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

    /**
     * Sets the combat ability handler for triggering combat abilities on kills.
     */
    public void setAbilityHandler(@Nullable CombatAbilityHandler abilityHandler) {
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
                       KillFeedEvent.KillerMessage event) {
        try {
            // Get player reference from the entity (the killer)
            PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
            if (playerRef == null) {
                return;
            }

            var playerId = playerRef.getUuid();
            long now = System.currentTimeMillis();

            // Create dedup key: playerUUID:victimRef:roundedTimestamp
            // Include victim entity ref to distinguish different kills
            var targetRef = event.getTargetRef();
            int victimEntityId = (targetRef != null) ? targetRef.hashCode() : 0;
            long roundedTime = (now / TIMESTAMP_PRECISION_MS) * TIMESTAMP_PRECISION_MS;
            String dedupKey = playerId.toString() + ":" + victimEntityId + ":" + roundedTime;

            // Check for duplicate - if key exists and timestamp is within dedup window, skip
            Long lastKillTime = recentKills.putIfAbsent(dedupKey, now);
            if (lastKillTime != null) {
                // Duplicate detected within the same time bucket
                logger.at(Level.FINE).log("Duplicate kill event filtered for player %s, victim %d",
                    playerId, victimEntityId);
                return;
            }

            // Proactive cleanup: remove all entries older than dedup window on every event
            // This prevents unbounded growth without waiting for size threshold
            recentKills.entrySet().removeIf(entry -> (now - entry.getValue()) > DEDUP_WINDOW_MS);

            // Get XP for the kill (try to get entity type if available)
            int xp = config.xpSources.defaultMobKillXp; // Default mob kill XP

            // Award XP
            system.awardXp(playerId, ProfessionType.COMBAT, xp);

            logger.at(Level.FINE).log("Awarded %d Combat XP to player %s for kill",
                xp, playerId);

            // Trigger combat abilities (Adrenaline Rush, Warrior's Resilience)
            if (abilityHandler != null) {
                abilityHandler.onKill(playerId);
            }

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error processing combat XP");
        }
    }
}
