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
 * Handler for building-related passive abilities.
 *
 * New Abilities:
 * - Architect's Focus (Tier 1, Lv.15): +100% building XP (handled by BuildingXpSystem)
 * - Steady Hands (Tier 2, Lv.35): Pause stamina depletion for 30s
 * - Master Builder (Tier 3, Lv.60): Permanent +10% max stamina (handled by PermanentBuffManager)
 *
 * Note: XP boost abilities are now handled directly in the XP systems.
 * This handler triggers Tier 2 abilities when building occurs.
 */
public class BuildingAbilityHandler {

    private final AbilitySystem abilitySystem;
    private final PlayerRegistry playerRegistry;
    private final HytaleLogger logger;

    public BuildingAbilityHandler(@Nonnull AbilitySystem abilitySystem,
                                  @Nonnull PlayerRegistry playerRegistry,
                                  @Nonnull HytaleLogger logger) {
        this.abilitySystem = abilitySystem;
        this.playerRegistry = playerRegistry;
        this.logger = logger;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Block placement events are handled by the ECS BuildingXpSystem
        // which calls checkTier2Ability when blocks are placed
        logger.at(Level.FINE).log("Building ability handler registered");
    }

    /**
     * Called by BuildingXpSystem when a block is placed.
     * Checks and applies Tier 2 ability (Steady Hands).
     *
     * @param playerId The building player's UUID
     */
    public void onBlockPlaced(@Nonnull UUID playerId) {
        // Check Tier 2: Steady Hands (pause stamina for 30s)
        if (abilitySystem.shouldTrigger(playerId, AbilityType.STEADY_HANDS)) {
            abilitySystem.applyTier2Effect(playerId, AbilityType.STEADY_HANDS);
            abilitySystem.logAbilityTrigger(playerId, AbilityType.STEADY_HANDS, "Stamina paused");
        }

        // Note: Tier 1 XP boost is handled in BuildingXpSystem via checkXpBoost()
        // Note: Tier 3 (Master Builder) is handled by PermanentBuffManager on login
    }
}
