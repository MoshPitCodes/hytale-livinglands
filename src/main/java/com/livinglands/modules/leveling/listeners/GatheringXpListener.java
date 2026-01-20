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
 * Listener for item pickup events to award Gathering XP.
 * Awards XP when players pick up items in the world.
 *
 * Note: Item pickup events may require ECS event registration.
 * This is a placeholder for integration when such events are available.
 */
public class GatheringXpListener {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;

    public GatheringXpListener(@Nonnull LevelingSystem system,
                               @Nonnull LevelingModuleConfig config,
                               @Nonnull HytaleLogger logger) {
        this.system = system;
        this.config = config;
        this.logger = logger;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Note: Item pickup events in Hytale may require ECS event registration.
        // This listener is a placeholder for future integration.

        logger.at(Level.FINE).log("Gathering XP listener registered (ECS integration pending)");
    }

    /**
     * Award XP for gathering an item.
     * This method can be called externally when item pickup events are detected.
     *
     * @param playerId The player who picked up the item
     * @param itemId The ID of the item that was picked up
     * @param count The number of items picked up
     */
    public void awardGatheringXp(@Nonnull UUID playerId, @Nonnull String itemId, int count) {
        int xpPerItem = config.xpSources.getGatherXp(itemId);
        int totalXp = xpPerItem * count;

        if (totalXp <= 0) return;

        system.awardXp(playerId, ProfessionType.GATHERING, totalXp);

        logger.at(Level.FINE).log("Awarded %d Gathering XP to player %s for picking up %dx %s",
            totalXp, playerId, count, itemId);
    }
}
