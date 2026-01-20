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
 *
 * New Abilities:
 * - Lumberjack's Vigor (Tier 1, Lv.15): +50% logging XP (handled by LoggingXpSystem)
 * - Forest's Blessing (Tier 2, Lv.35): Restore 5 energy when chopping
 * - Nature's Endurance (Tier 3, Lv.60): Permanent +10% movement speed (handled by PermanentBuffManager)
 *
 * Note: XP boost abilities are now handled directly in the XP systems.
 * This handler triggers Tier 2 abilities when logging occurs.
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
        // Block break events are handled by the ECS LoggingXpSystem
        // which calls checkTier2Ability when logs are chopped
        logger.at(Level.FINE).log("Logging ability handler registered");
    }

    /**
     * Called by LoggingXpSystem when a log is chopped.
     * Checks and applies Tier 2 ability (Forest's Blessing).
     *
     * @param playerId The logging player's UUID
     */
    public void onLogChopped(@Nonnull UUID playerId) {
        // Check Tier 2: Forest's Blessing (restore 5 energy)
        if (abilitySystem.shouldTrigger(playerId, AbilityType.FORESTS_BLESSING)) {
            abilitySystem.applyTier2Effect(playerId, AbilityType.FORESTS_BLESSING);
            abilitySystem.logAbilityTrigger(playerId, AbilityType.FORESTS_BLESSING, "Energy restored");
        }

        // Note: Tier 1 XP boost is handled in LoggingXpSystem via checkXpBoost()
        // Note: Tier 3 (Nature's Endurance) is handled by PermanentBuffManager on login
    }
}
