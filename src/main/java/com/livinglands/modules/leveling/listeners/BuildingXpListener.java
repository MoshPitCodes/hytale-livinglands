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
 * Listener for block placement events to award Building XP.
 * Awards XP when players place blocks.
 *
 * Note: ECS block events require different registration via the ECS system.
 * This is a placeholder for integration when block placement events are available.
 */
public class BuildingXpListener {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;

    public BuildingXpListener(@Nonnull LevelingSystem system,
                              @Nonnull LevelingModuleConfig config,
                              @Nonnull HytaleLogger logger) {
        this.system = system;
        this.config = config;
        this.logger = logger;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Note: Block placement events in Hytale are ECS events that require
        // registration through the ECS system, not the standard EventRegistry.
        // This listener is a placeholder for future integration.

        logger.at(Level.FINE).log("Building XP listener registered (ECS integration pending)");
    }

    /**
     * Award XP for placing a block.
     * This method can be called externally when block placement events are detected.
     *
     * @param playerId The player who placed the block
     * @param blockId The ID of the block that was placed
     */
    public void awardBuildingXp(@Nonnull UUID playerId, @Nonnull String blockId) {
        int xp = config.xpSources.blockPlaceXp;
        system.awardXp(playerId, ProfessionType.BUILDING, xp);

        logger.at(Level.FINE).log("Awarded %d Building XP to player %s for placing %s",
            xp, playerId, blockId);
    }
}
