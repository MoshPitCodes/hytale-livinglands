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
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;
import com.livinglands.modules.leveling.util.PlayerPlacedBlockChecker;

import javax.annotation.Nonnull;
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

    // Block types that award logging XP
    private static final Set<String> LOG_BLOCKS = Set.of(
        "hytale:oak_log", "hytale:birch_log", "hytale:spruce_log",
        "hytale:palm_log", "hytale:cactus_log", "hytale:dead_log",
        "hytale:cherry_log", "hytale:jungle_log", "hytale:acacia_log",
        "hytale:log", "hytale:wood"
    );

    private static final Set<String> LEAF_BLOCKS = Set.of(
        "hytale:oak_leaves", "hytale:birch_leaves", "hytale:spruce_leaves",
        "hytale:palm_leaves", "hytale:cherry_leaves", "hytale:jungle_leaves",
        "hytale:acacia_leaves", "hytale:leaves"
    );

    public LoggingXpSystem(@Nonnull LevelingSystem system,
                           @Nonnull LevelingModuleConfig config,
                           @Nonnull HytaleLogger logger) {
        super(BreakBlockEvent.class);
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
                       BreakBlockEvent event) {
        try {
            // Get player reference from the entity
            PlayerRef playerRef = chunk.getComponent(entityIndex, playerRefType);
            if (playerRef == null) {
                return;
            }

            var playerId = playerRef.getUuid();
            String blockId = event.getBlockType().getId();

            // Only process log/wood blocks
            if (!isLoggingBlock(blockId)) {
                return;
            }

            // Skip player-placed blocks - only award XP for naturally generated blocks
            if (PlayerPlacedBlockChecker.isPlayerPlaced(store, event.getTargetBlock())) {
                logger.at(Level.FINE).log("Skipping Logging XP for player-placed block %s", blockId);
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

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error processing logging XP");
        }
    }

    /**
     * Check if a block is a logging-related block.
     */
    private boolean isLoggingBlock(String blockId) {
        if (LOG_BLOCKS.contains(blockId) || LEAF_BLOCKS.contains(blockId)) {
            return true;
        }
        // Also check for partial matches (different wood types)
        String lower = blockId.toLowerCase();
        return lower.contains("log") || lower.contains("wood") || lower.contains("leaves");
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

        // Fall back to defaults
        if (LOG_BLOCKS.contains(blockId) || blockId.toLowerCase().contains("log")) {
            return 5; // Log XP
        }
        if (LEAF_BLOCKS.contains(blockId) || blockId.toLowerCase().contains("leaves")) {
            return 1; // Small XP for leaves
        }

        return 0;
    }
}
