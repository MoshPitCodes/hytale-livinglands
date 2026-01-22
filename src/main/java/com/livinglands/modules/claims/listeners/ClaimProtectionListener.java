package com.livinglands.modules.claims.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.livinglands.core.notifications.NotificationModule;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import com.livinglands.modules.claims.data.PlotClaim;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Event listener for claim protection.
 *
 * Handles:
 * - Block breaking protection (BreakBlockEvent)
 * - Block placing protection (PlaceBlockEvent)
 * - Container/interaction protection (UseBlockEvent.Pre)
 * - PvP protection (Damage event) - based on claim's pvp flag
 *
 * All events are cancelled if the player doesn't have permission in the claim.
 */
public class ClaimProtectionListener {

    private final ClaimSystem claimSystem;
    private final ClaimsModuleConfig config;
    private final NotificationModule notificationModule;
    private final HytaleLogger logger;

    public ClaimProtectionListener(@Nonnull ClaimSystem claimSystem,
                                   @Nonnull ClaimsModuleConfig config,
                                   @Nonnull NotificationModule notificationModule,
                                   @Nonnull HytaleLogger logger) {
        this.claimSystem = claimSystem;
        this.config = config;
        this.notificationModule = notificationModule;
        this.logger = logger;
    }

    /**
     * Registers all protection systems with the entity store registry.
     */
    public void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new BreakBlockProtectionSystem());
        registry.registerSystem(new PlaceBlockProtectionSystem());
        registry.registerSystem(new UseBlockProtectionSystem());
        registry.registerSystem(new PvPProtectionSystem());
        // Note: Item cleanup system removed - can't distinguish between player drops and NPC death loot.
        // NPCs are killed by burn damage; players can collect any dropped loot themselves.
        logger.at(Level.FINE).log("[Claims] Registered protection listeners");
    }

    /**
     * Checks if a player can build (place/break) at a location.
     * Does NOT check buildRestrictedToClaimsOnly - that's handled separately for placing.
     */
    private boolean canBuild(@Nonnull UUID playerId, @Nonnull String worldId, int blockX, int blockZ) {
        PlotClaim claim = claimSystem.getClaimAtBlock(worldId, blockX, blockZ);
        if (claim == null) {
            return true; // Unclaimed - anyone can build (unless buildRestrictedToClaimsOnly)
        }

        // Check admin bypass
        if (claimSystem.hasAdminBypass(playerId)) {
            return true;
        }

        // Check if owner or trusted
        return claim.isTrusted(playerId);
    }

    /**
     * Checks if a player can place a specific block at a location.
     * Takes into account buildRestrictedToClaimsOnly setting and allowed blocks.
     */
    private boolean canPlaceBlock(@Nonnull UUID playerId, @Nonnull String worldId,
                                  int blockX, int blockZ, @Nonnull String blockTypeId) {
        PlotClaim claim = claimSystem.getClaimAtBlock(worldId, blockX, blockZ);

        // Check admin bypass first
        if (claimSystem.hasAdminBypass(playerId)) {
            return true;
        }

        if (claim == null) {
            // Unclaimed area
            if (config.buildRestrictedToClaimsOnly) {
                // Building is restricted - check if this block type is allowed
                return isBlockAllowedOutsideClaims(blockTypeId);
            }
            return true; // No restriction - anyone can build
        }

        // Inside a claim - check if owner or trusted
        return claim.isTrusted(playerId);
    }

    /**
     * Checks if a block type is in the allowed list for placing outside claims.
     */
    private boolean isBlockAllowedOutsideClaims(@Nonnull String blockTypeId) {
        if (config.allowedBlocksOutsideClaims == null || config.allowedBlocksOutsideClaims.isEmpty()) {
            return false;
        }
        return config.allowedBlocksOutsideClaims.contains(blockTypeId);
    }

    /**
     * Checks if a player can access containers at a location.
     */
    private boolean canAccess(@Nonnull UUID playerId, @Nonnull String worldId, int blockX, int blockZ) {
        PlotClaim claim = claimSystem.getClaimAtBlock(worldId, blockX, blockZ);
        if (claim == null) {
            return true; // Unclaimed - anyone can access
        }

        // Check admin bypass
        if (claimSystem.hasAdminBypass(playerId)) {
            return true;
        }

        // Check if owner or has at least accessor permission
        return claim.hasAccessorPermission(playerId);
    }

    /**
     * Gets the claim owner name for a location (for messages).
     */
    private String getClaimOwnerName(@Nonnull String worldId, int blockX, int blockZ) {
        PlotClaim claim = claimSystem.getClaimAtBlock(worldId, blockX, blockZ);
        return claim != null ? claim.getOwnerName() : "Unknown";
    }

    /**
     * Sends a denial message to a player.
     */
    private void sendDeniedMessage(@Nonnull UUID playerId, @Nonnull String message) {
        if (notificationModule != null) {
            notificationModule.sendChatError(playerId, message);
        }
    }

    /**
     * ECS System for protecting against block breaking.
     */
    private class BreakBlockProtectionSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

        private final ComponentType<EntityStore, PlayerRef> playerRefType;

        public BreakBlockProtectionSystem() {
            super(BreakBlockEvent.class);
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
                           BreakBlockEvent event) {
            try {
                var blockPos = event.getTargetBlock();
                int blockX = blockPos.getX();
                int blockZ = blockPos.getZ();

                // Get world ID
                var world = store.getExternalData().getWorld();
                String worldId = world != null ? world.getName() : "unknown";

                PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);

                if (playerRef != null) {
                    // Player breaking block - check build permission
                    var playerId = playerRef.getUuid();

                    if (!canBuild(playerId, worldId, blockX, blockZ)) {
                        event.setCancelled(true);
                        String ownerName = getClaimOwnerName(worldId, blockX, blockZ);
                        sendDeniedMessage(playerId, String.format(config.deniedBreakMessage, ownerName));

                        logger.at(Level.FINE).log("[Claims] Denied break for %s at [%d, %d] in %s's claim",
                            playerId, blockX, blockZ, ownerName);
                    }
                } else {
                    // Non-player entity (explosion, mob, etc.) breaking block
                    // Check if claim has mob griefing disabled (covers explosions too)
                    PlotClaim claim = claimSystem.getClaimAtBlock(worldId, blockX, blockZ);
                    if (claim != null && !claim.getFlags().mobGriefing) {
                        event.setCancelled(true);
                        logger.at(Level.FINE).log("[Claims] Blocked non-player block break at [%d, %d] in protected claim",
                            blockX, blockZ);
                    }
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[Claims] Error in break protection");
            }
        }
    }

    /**
     * ECS System for protecting against block placing.
     * Also enforces buildRestrictedToClaimsOnly when enabled.
     */
    private class PlaceBlockProtectionSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

        private final ComponentType<EntityStore, PlayerRef> playerRefType;

        public PlaceBlockProtectionSystem() {
            super(PlaceBlockEvent.class);
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
                PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
                if (playerRef == null) {
                    return;
                }

                var playerId = playerRef.getUuid();
                var blockPos = event.getTargetBlock();
                int blockX = blockPos.getX();
                int blockZ = blockPos.getZ();

                // Get world ID
                var world = store.getExternalData().getWorld();
                String worldId = world != null ? world.getName() : "unknown";

                // Get block type ID from the item being placed
                var itemInHand = event.getItemInHand();
                String blockTypeId = itemInHand != null ? itemInHand.getItemId() : "";

                // Use canPlaceBlock which handles both claim protection AND buildRestrictedToClaimsOnly
                if (!canPlaceBlock(playerId, worldId, blockX, blockZ, blockTypeId)) {
                    event.setCancelled(true);

                    // Determine the right message
                    PlotClaim claim = claimSystem.getClaimAtBlock(worldId, blockX, blockZ);
                    if (claim != null) {
                        // Inside someone else's claim
                        String ownerName = claim.getOwnerName();
                        sendDeniedMessage(playerId, String.format(config.deniedPlaceMessage, ownerName));
                        logger.at(Level.FINE).log("[Claims] Denied place for %s at [%d, %d] in %s's claim",
                            playerId, blockX, blockZ, ownerName);
                    } else {
                        // Outside all claims but buildRestrictedToClaimsOnly is enabled
                        sendDeniedMessage(playerId, config.deniedPlaceOutsideClaimMessage);
                        logger.at(Level.FINE).log("[Claims] Denied place for %s at [%d, %d] - building restricted to claims",
                            playerId, blockX, blockZ);
                    }
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[Claims] Error in place protection");
            }
        }
    }

    /**
     * ECS System for protecting against container/block interaction.
     */
    private class UseBlockProtectionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

        private final ComponentType<EntityStore, PlayerRef> playerRefType;

        public UseBlockProtectionSystem() {
            super(UseBlockEvent.Pre.class);
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
                           UseBlockEvent.Pre event) {
            try {
                PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
                if (playerRef == null) {
                    return;
                }

                var playerId = playerRef.getUuid();
                var blockPos = event.getTargetBlock();
                int blockX = blockPos.getX();
                int blockZ = blockPos.getZ();

                // Get world ID
                var world = store.getExternalData().getWorld();
                String worldId = world != null ? world.getName() : "unknown";

                // Check if this is a container or interactive block
                String blockId = event.getBlockType() != null ? event.getBlockType().getId() : "";
                boolean isContainer = isContainerBlock(blockId);

                if (isContainer) {
                    // Containers require at least ACCESSOR permission
                    if (!canAccess(playerId, worldId, blockX, blockZ)) {
                        event.setCancelled(true);
                        String ownerName = getClaimOwnerName(worldId, blockX, blockZ);
                        sendDeniedMessage(playerId, String.format(config.deniedContainerMessage, ownerName));

                        logger.at(Level.FINE).log("[Claims] Denied container access for %s at [%d, %d] in %s's claim",
                            playerId, blockX, blockZ, ownerName);
                    }
                } else {
                    // Other interactions (buttons, levers, doors) require TRUSTED permission
                    if (!canBuild(playerId, worldId, blockX, blockZ)) {
                        event.setCancelled(true);
                        String ownerName = getClaimOwnerName(worldId, blockX, blockZ);
                        sendDeniedMessage(playerId, String.format(config.deniedInteractMessage, ownerName));

                        logger.at(Level.FINE).log("[Claims] Denied interact for %s at [%d, %d] in %s's claim",
                            playerId, blockX, blockZ, ownerName);
                    }
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[Claims] Error in use block protection");
            }
        }

        /**
         * Check if a block is a container (chest, barrel, etc.)
         */
        private boolean isContainerBlock(String blockId) {
            if (blockId == null || blockId.isEmpty()) {
                return false;
            }
            String lower = blockId.toLowerCase();
            return lower.contains("chest") ||
                   lower.contains("barrel") ||
                   lower.contains("hopper") ||
                   lower.contains("crate") ||
                   lower.contains("storage") ||
                   lower.contains("furnace") ||
                   lower.contains("crafting");
        }
    }

    /**
     * ECS System for protecting against PvP damage in claims where PvP is disabled.
     *
     * Checks if damage is player-to-player and cancels it if:
     * - The victim is in a claim with pvp=false
     * - OR the attacker is in a claim with pvp=false
     */
    private class PvPProtectionSystem extends DamageEventSystem {

        private final ComponentType<EntityStore, PlayerRef> playerRefType;
        private final ComponentType<EntityStore, TransformComponent> transformType;

        public PvPProtectionSystem() {
            this.playerRefType = PlayerRef.getComponentType();
            this.transformType = TransformComponent.getComponentType();
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
                           Damage damage) {
            try {
                // Get the victim's PlayerRef (the entity receiving damage)
                PlayerRef victimRef = chunk.getComponent(entityIndex, playerRefType);
                if (victimRef == null) {
                    return; // Not a player being damaged
                }

                // Check if damage source is from another player (EntitySource)
                Damage.Source source = damage.getSource();
                if (!(source instanceof Damage.EntitySource entitySource)) {
                    return; // Not entity damage (could be fall damage, etc.)
                }

                // Get the attacker entity reference
                Ref<EntityStore> attackerRef = entitySource.getRef();
                if (attackerRef == null) {
                    return;
                }

                // Try to get attacker's PlayerRef to confirm it's a player
                PlayerRef attackerPlayerRef = store.getComponent(attackerRef, playerRefType);
                if (attackerPlayerRef == null) {
                    return; // Attacker is not a player (mob damage is allowed)
                }

                // This is player-to-player damage - check PvP protection
                UUID victimId = victimRef.getUuid();
                UUID attackerId = attackerPlayerRef.getUuid();

                // Get victim's position via TransformComponent
                TransformComponent victimTransform = chunk.getComponent(entityIndex, transformType);
                if (victimTransform == null) {
                    return;
                }

                // Get world ID
                var world = store.getExternalData().getWorld();
                String worldId = world != null ? world.getName() : "unknown";

                // Check victim's location for PvP flag
                var victimPos = victimTransform.getPosition();
                int blockX = (int) Math.floor(victimPos.getX());
                int blockZ = (int) Math.floor(victimPos.getZ());

                PlotClaim victimClaim = claimSystem.getClaimAtBlock(worldId, blockX, blockZ);
                if (victimClaim != null && !victimClaim.getFlags().pvp) {
                    damage.setCancelled(true);
                    sendDeniedMessage(attackerId, "PvP is disabled in this claim");
                    logger.at(Level.FINE).log("[Claims] Blocked PvP damage from %s to %s in protected claim",
                        attackerId, victimId);
                    return;
                }

                // Also check attacker's location (prevents attacking from safe zones)
                TransformComponent attackerTransform = store.getComponent(attackerRef, transformType);
                if (attackerTransform != null) {
                    var attackerPos = attackerTransform.getPosition();
                    int attackerBlockX = (int) Math.floor(attackerPos.getX());
                    int attackerBlockZ = (int) Math.floor(attackerPos.getZ());

                    PlotClaim attackerClaim = claimSystem.getClaimAtBlock(worldId, attackerBlockX, attackerBlockZ);
                    if (attackerClaim != null && !attackerClaim.getFlags().pvp) {
                        damage.setCancelled(true);
                        sendDeniedMessage(attackerId, "You cannot attack from a PvP-protected claim");
                        logger.at(Level.FINE).log("[Claims] Blocked PvP damage from %s (in protected claim) to %s",
                            attackerId, victimId);
                    }
                }

            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("[Claims] Error in PvP protection");
            }
        }
    }
}
