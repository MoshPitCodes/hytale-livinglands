package com.livinglands.modules.leveling.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener for block breaking events to award Mining XP.
 * Awards XP when players break ore blocks.
 *
 * Note: ECS block events require different registration via the ECS system.
 * This is a placeholder for integration when block break events are available.
 */
public class MiningXpListener {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;

    public MiningXpListener(@Nonnull LevelingSystem system,
                            @Nonnull LevelingModuleConfig config,
                            @Nonnull HytaleLogger logger) {
        this.system = system;
        this.config = config;
        this.logger = logger;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Note: Block break events in Hytale are ECS events that require
        // registration through the ECS system, not the standard EventRegistry.
        // This listener is a placeholder for future integration.
        //
        // To integrate when ECS events are available:
        // - Register for BreakBlockEvent or similar ECS event
        // - Extract block type and player from event
        // - Check if it's an ore and award XP

        logger.at(Level.INFO).log("Mining XP listener registered (ECS integration pending)");
    }

    /**
     * Award XP for mining an ore block.
     * This method can be called externally when block break events are detected.
     *
     * @param playerId The player who mined the ore
     * @param blockId The ID of the block that was mined
     */
    public void awardMiningXp(@Nonnull UUID playerId, @Nonnull String blockId) {
        if (!isOre(blockId)) return;

        int xp = config.xpSources.getOreXp(blockId);
        system.awardXp(playerId, ProfessionType.MINING, xp);

        logger.at(Level.FINE).log("Awarded %d Mining XP to player %s for breaking %s",
            xp, playerId, blockId);
    }

    /**
     * Check if a block ID represents an ore.
     */
    private boolean isOre(String blockId) {
        if (blockId == null) return false;
        String lower = blockId.toLowerCase();
        return lower.contains("ore") || lower.contains("_ore");
    }
}
