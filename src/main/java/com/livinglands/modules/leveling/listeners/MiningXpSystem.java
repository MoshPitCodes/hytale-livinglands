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
 * ECS System for awarding Mining XP when players break blocks.
 * Extends EntityEventSystem to receive BreakBlockEvent from the ECS.
 */
public class MiningXpSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;
    private final ComponentType<EntityStore, PlayerRef> playerRefType;

    // Block types that award mining XP (ores and stone variants)
    private static final Set<String> ORE_BLOCKS = Set.of(
        "hytale:coal_ore", "hytale:iron_ore", "hytale:gold_ore",
        "hytale:copper_ore", "hytale:silver_ore", "hytale:cobalt_ore",
        "hytale:mythril_ore", "hytale:diamond_ore", "hytale:emerald_ore",
        "hytale:ruby_ore", "hytale:sapphire_ore"
    );

    private static final Set<String> STONE_BLOCKS = Set.of(
        "hytale:stone", "hytale:cobblestone", "hytale:granite",
        "hytale:diorite", "hytale:andesite", "hytale:sandstone"
    );

    public MiningXpSystem(@Nonnull LevelingSystem system,
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
            var blockPosition = event.getTargetBlock();

            // Get world ID for tracking
            var world = store.getExternalData().getWorld();
            String worldId = world != null ? world.getName() : "unknown";

            // Skip player-placed blocks - only award XP for naturally generated blocks
            if (PlayerPlacedBlockChecker.isPlayerPlaced(store, blockPosition)) {
                logger.at(Level.FINE).log("Skipping Mining XP for player-placed block %s", blockId);
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
            system.awardXp(playerId, ProfessionType.MINING, xp);

            logger.at(Level.FINE).log("Awarded %d Mining XP to player %s for breaking %s",
                xp, playerId, blockId);

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error processing mining XP");
        }
    }

    /**
     * Calculate XP for breaking a block.
     */
    private int calculateXp(String blockId) {
        // Check configured ore XP first
        int configuredXp = config.xpSources.getOreXp(blockId);
        if (configuredXp > 0) {
            return configuredXp;
        }

        // Fall back to defaults
        if (ORE_BLOCKS.contains(blockId)) {
            return 15; // Base ore XP
        }
        if (STONE_BLOCKS.contains(blockId)) {
            return 1; // Small XP for stone
        }

        return 0; // No XP for other blocks
    }
}
