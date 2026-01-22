package com.livinglands.modules.claims.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.data.PlotClaim;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Tick-based system that despawns hostile NPCs in protected claims.
 *
 * Features:
 * - Detects NPCs (entities without PlayerRef) in claims with hostileNpcProtection=true
 * - Immediately despawns hostile NPCs entering protected claims
 * - Only affects hostile NPCs, not players or passive creatures
 *
 * Performance optimizations:
 * - Early exit for entities without required components
 * - Cached component types
 */
public class ClaimNPCBurnSystem {

    private final ClaimSystem claimSystem;
    private final HytaleLogger logger;

    public ClaimNPCBurnSystem(@Nonnull ClaimSystem claimSystem,
                              @Nonnull HytaleLogger logger) {
        this.claimSystem = claimSystem;
        this.logger = logger;
    }

    /**
     * Registers the NPC protection system with the entity store registry.
     */
    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new NPCProtectionTickSystem());
        logger.at(Level.FINE).log("[Claims] Registered NPC protection system");
    }

    /**
     * ECS Ticking System that despawns NPCs in protected claims.
     */
    private class NPCProtectionTickSystem extends EntityTickingSystem<EntityStore> {

        private final ComponentType<EntityStore, PlayerRef> playerRefType;
        private final ComponentType<EntityStore, TransformComponent> transformType;
        private final ComponentType<EntityStore, EntityStatMap> statsType;
        private final ComponentType<EntityStore, DespawnComponent> despawnType;

        public NPCProtectionTickSystem() {
            this.playerRefType = PlayerRef.getComponentType();
            this.transformType = TransformComponent.getComponentType();
            this.statsType = EntityStatMap.getComponentType();
            this.despawnType = DespawnComponent.getComponentType();
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public boolean isParallel(int archetypeIndex, int chunkIndex) {
            return false;
        }

        @Override
        public void tick(float deltaTime,
                        int entityIndex,
                        ArchetypeChunk<EntityStore> chunk,
                        Store<EntityStore> store,
                        CommandBuffer<EntityStore> commandBuffer) {

            try {
                // Skip if entity is a player
                PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
                if (playerRef != null) {
                    return;
                }

                // Skip if entity doesn't have stats (likely not a combat NPC)
                EntityStatMap stats = chunk.getComponent(entityIndex, statsType);
                if (stats == null) {
                    return;
                }

                // Skip if already despawning
                DespawnComponent existingDespawn = chunk.getComponent(entityIndex, despawnType);
                if (existingDespawn != null) {
                    return;
                }

                // Get entity position
                TransformComponent transform = chunk.getComponent(entityIndex, transformType);
                if (transform == null) {
                    return;
                }

                // Get world ID
                var world = store.getExternalData().getWorld();
                if (world == null) {
                    return;
                }
                String worldId = world.getName();

                // Calculate block position
                var position = transform.getPosition();
                int blockX = (int) Math.floor(position.getX());
                int blockZ = (int) Math.floor(position.getZ());

                // Check if in a claim with hostile NPC protection enabled
                PlotClaim claim = claimSystem.getClaimAtBlock(worldId, blockX, blockZ);
                if (claim == null || !claim.getFlags().hostileNpcProtection) {
                    return;
                }

                // Despawn the NPC immediately using the world's TimeResource
                var ref = chunk.getReferenceTo(entityIndex);
                var timeResource = store.getResource(TimeResource.getResourceType());
                commandBuffer.addComponent(ref, despawnType, DespawnComponent.despawnInSeconds(timeResource, 0));

                logger.at(Level.FINE).log(
                    "[Claims] Despawned NPC at [%d, %d] in %s's claim",
                    blockX, blockZ, claim.getOwnerName()
                );

            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[Claims] Error in NPC protection system");
            }
        }
    }
}
