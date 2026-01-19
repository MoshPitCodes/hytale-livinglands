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
 * Listener for block breaking events to award Logging XP.
 * Awards XP when players break wood/log blocks.
 *
 * Note: ECS block events require different registration via the ECS system.
 * This is a placeholder for integration when block break events are available.
 */
public class LoggingXpListener {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;

    public LoggingXpListener(@Nonnull LevelingSystem system,
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

        logger.at(Level.INFO).log("Logging XP listener registered (ECS integration pending)");
    }

    /**
     * Award XP for logging a wood block.
     * This method can be called externally when block break events are detected.
     *
     * @param playerId The player who broke the wood
     * @param blockId The ID of the block that was broken
     */
    public void awardLoggingXp(@Nonnull UUID playerId, @Nonnull String blockId) {
        if (!isWood(blockId)) return;

        int xp = config.xpSources.getWoodXp(blockId);
        system.awardXp(playerId, ProfessionType.LOGGING, xp);

        logger.at(Level.FINE).log("Awarded %d Logging XP to player %s for breaking %s",
            xp, playerId, blockId);
    }

    /**
     * Check if a block ID represents wood or a log.
     */
    private boolean isWood(String blockId) {
        if (blockId == null) return false;
        String lower = blockId.toLowerCase();
        return lower.contains("log") || lower.contains("wood") ||
               lower.contains("plank") || lower.contains("bark");
    }
}
