package com.livinglands.modules.leveling.ability.handlers;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.leveling.ability.AbilitySystem;
import com.livinglands.modules.leveling.ability.AbilityType;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handler for gathering-related passive abilities.
 * Handles Double Harvest and Rare Find abilities.
 *
 * Note: Item pickup events may require ECS event registration.
 * This handler provides methods that can be called when events are available.
 */
public class GatheringAbilityHandler {

    private final AbilitySystem abilitySystem;
    private final PlayerRegistry playerRegistry;
    private final HytaleLogger logger;

    public GatheringAbilityHandler(@Nonnull AbilitySystem abilitySystem,
                                   @Nonnull PlayerRegistry playerRegistry,
                                   @Nonnull HytaleLogger logger) {
        this.abilitySystem = abilitySystem;
        this.playerRegistry = playerRegistry;
        this.logger = logger;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Note: Item pickup events may require ECS registration.
        // This handler provides trigger methods for when events are available.
        logger.at(Level.INFO).log("Gathering ability handler registered (ECS events pending)");
    }

    /**
     * Check if Double Harvest should trigger.
     *
     * @param playerId The gathering player
     * @return true if ability triggers
     */
    public boolean checkDoubleHarvest(@Nonnull UUID playerId) {
        if (abilitySystem.shouldTrigger(playerId, AbilityType.DOUBLE_HARVEST)) {
            abilitySystem.logAbilityTrigger(playerId, AbilityType.DOUBLE_HARVEST, "Double harvest triggered");
            return true;
        }
        return false;
    }

    /**
     * Check if Rare Find should trigger.
     *
     * @param playerId The gathering player
     * @return true if ability triggers
     */
    public boolean checkRareFind(@Nonnull UUID playerId) {
        if (abilitySystem.shouldTrigger(playerId, AbilityType.RARE_FIND)) {
            abilitySystem.logAbilityTrigger(playerId, AbilityType.RARE_FIND, "Rare item found!");
            return true;
        }
        return false;
    }
}
