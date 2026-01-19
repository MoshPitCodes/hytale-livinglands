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
 * Handler for logging-related passive abilities.
 * Handles Efficient Chopping and Bark Collector abilities.
 *
 * Note: Block break events require ECS event registration.
 * This handler provides methods that can be called when events are available.
 */
public class LoggingAbilityHandler {

    private final AbilitySystem abilitySystem;
    private final PlayerRegistry playerRegistry;
    private final HytaleLogger logger;

    public LoggingAbilityHandler(@Nonnull AbilitySystem abilitySystem,
                                 @Nonnull PlayerRegistry playerRegistry,
                                 @Nonnull HytaleLogger logger) {
        this.abilitySystem = abilitySystem;
        this.playerRegistry = playerRegistry;
        this.logger = logger;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Note: Block break events require ECS registration.
        // This handler provides trigger methods for when events are available.
        logger.at(Level.INFO).log("Logging ability handler registered (ECS events pending)");
    }

    /**
     * Check if Efficient Chopping should trigger.
     *
     * @param playerId The logging player
     * @return true if ability triggers
     */
    public boolean checkEfficientChopping(@Nonnull UUID playerId) {
        if (abilitySystem.shouldTrigger(playerId, AbilityType.EFFICIENT_CHOPPING)) {
            abilitySystem.logAbilityTrigger(playerId, AbilityType.EFFICIENT_CHOPPING, "Tree felling triggered");
            return true;
        }
        return false;
    }

    /**
     * Check if Bark Collector should trigger.
     *
     * @param playerId The logging player
     * @return true if ability triggers
     */
    public boolean checkBarkCollector(@Nonnull UUID playerId) {
        if (abilitySystem.shouldTrigger(playerId, AbilityType.BARK_COLLECTOR)) {
            abilitySystem.logAbilityTrigger(playerId, AbilityType.BARK_COLLECTOR, "Bonus bark drops");
            return true;
        }
        return false;
    }
}
