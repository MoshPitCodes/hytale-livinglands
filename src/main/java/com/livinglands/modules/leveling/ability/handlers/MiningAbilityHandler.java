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
 * Handler for mining-related passive abilities.
 * Handles Double Ore and Lucky Strike abilities.
 *
 * Note: Block break events require ECS event registration.
 * This handler provides methods that can be called when events are available.
 */
public class MiningAbilityHandler {

    private final AbilitySystem abilitySystem;
    private final PlayerRegistry playerRegistry;
    private final HytaleLogger logger;

    public MiningAbilityHandler(@Nonnull AbilitySystem abilitySystem,
                                @Nonnull PlayerRegistry playerRegistry,
                                @Nonnull HytaleLogger logger) {
        this.abilitySystem = abilitySystem;
        this.playerRegistry = playerRegistry;
        this.logger = logger;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Note: Block break events require ECS registration.
        // This handler provides trigger methods for when events are available.
        logger.at(Level.INFO).log("Mining ability handler registered (ECS events pending)");
    }

    /**
     * Check if Double Ore should trigger.
     *
     * @param playerId The mining player
     * @return true if ability triggers
     */
    public boolean checkDoubleOre(@Nonnull UUID playerId) {
        if (abilitySystem.shouldTrigger(playerId, AbilityType.DOUBLE_ORE)) {
            abilitySystem.logAbilityTrigger(playerId, AbilityType.DOUBLE_ORE, "Double drops triggered");
            return true;
        }
        return false;
    }

    /**
     * Check if Lucky Strike should trigger.
     *
     * @param playerId The mining player
     * @return true if ability triggers
     */
    public boolean checkLuckyStrike(@Nonnull UUID playerId) {
        if (abilitySystem.shouldTrigger(playerId, AbilityType.LUCKY_STRIKE)) {
            abilitySystem.logAbilityTrigger(playerId, AbilityType.LUCKY_STRIKE, "Found rare gem!");
            return true;
        }
        return false;
    }
}
