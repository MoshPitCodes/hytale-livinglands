package com.livinglands.modules.leveling.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.ability.handlers.LoggingAbilityHandler;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;
import com.livinglands.modules.leveling.util.PlayerPlacedBlockChecker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.logging.Level;

/**
 * ECS System for awarding Logging XP when players break wood/log blocks.
 * Extends EntityEventSystem to receive BreakBlockEvent from the ECS.
 *
 * Note: This listens to the same event as MiningXpSystem but awards XP
 * for different block types (logs instead of ores).
 */
public class LoggingXpSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    @Nullable
    private LoggingAbilityHandler abilityHandler;

    // Wood block prefixes that award logging XP
    // Wood names follow pattern: Wood_{TreeType}_{Part}
    private static final String WOOD_PREFIX = "Wood_";

    // Leaf block prefix
    // Leaf names follow pattern: Plant_Leaves_{TreeType}
    private static final String LEAVES_PREFIX = "Plant_Leaves_";

    public LoggingXpSystem(@Nonnull LevelingSystem system,
                           @Nonnull LevelingModuleConfig config,
                           @Nonnull HytaleLogger logger) {
        super(BreakBlockEvent.class);
        this.system = system;
        this.config = config;
        this.logger = logger;
        this.playerRefType = PlayerRef.getComponentType();
    }

    /**
     * Sets the logging ability handler for triggering logging abilities on wood breaks.
     */
    public void setAbilityHandler(@Nullable LoggingAbilityHandler abilityHandler) {
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
                       BreakBlockEvent event) {
        try {
            // Get player reference from the entity
            PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
            if (playerRef == null) {
                return;
            }

            var playerId = playerRef.getUuid();
            String blockId = event.getBlockType().getId();
            var blockPosition = event.getTargetBlock();

            // Skip "Empty" block events - these fire when placing blocks (air being removed)
            if ("Empty".equals(blockId)) {
                return;
            }

            // Only process log/wood blocks
            if (!isLoggingBlock(blockId)) {
                return;
            }

            // Get world ID for tracking
            var world = store.getExternalData().getWorld();
            String worldId = world != null ? world.getName() : "unknown";

            // Skip player-placed blocks - only award XP for naturally generated blocks
            if (PlayerPlacedBlockChecker.isPlayerPlaced(store, blockPosition)) {
                logger.at(Level.FINE).log("Skipping Logging XP for player-placed block %s", blockId);
                // Clean up tracking when block is broken
                PlayerPlacedBlockChecker.recordBlockBroken(worldId, blockPosition);
                return;
            }

            // Calculate XP based on block type
            int xp = calculateXp(blockId);
            if (xp <= 0) {
                return;
            }

            // Award XP
            system.awardXp(playerId, ProfessionType.LOGGING, xp);

            logger.at(Level.FINE).log("Awarded %d Logging XP to player %s for breaking %s",
                xp, playerId, blockId);

            // Trigger logging abilities (Forest's Blessing) for wood blocks (not leaves)
            if (abilityHandler != null && blockId.startsWith(WOOD_PREFIX)) {
                abilityHandler.onLogChopped(playerId);
            }

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error processing logging XP");
        }
    }

    /**
     * Check if a block is a logging-related block.
     */
    private boolean isLoggingBlock(String blockId) {
        return blockId.startsWith(WOOD_PREFIX) || blockId.startsWith(LEAVES_PREFIX);
    }

    /**
     * Calculate XP for breaking a block.
     */
    private int calculateXp(String blockId) {
        // Check configured XP first
        int configuredXp = config.xpSources.getWoodXp(blockId);
        if (configuredXp > 0) {
            return configuredXp;
        }

        // Wood blocks (trunk, roots, branches) give more XP
        if (blockId.startsWith(WOOD_PREFIX)) {
            return 5; // Wood XP
        }

        // Leaves give small XP
        if (blockId.startsWith(LEAVES_PREFIX)) {
            return 1; // Leaves XP
        }

        return 0;
    }
}
