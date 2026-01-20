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
 *
 * New Abilities:
 * - Forager's Intuition (Tier 1, Lv.15): +50% gathering XP (handled by GatheringXpSystem)
 * - Nature's Gift (Tier 2, Lv.35): Restore 3 hunger and 3 thirst
 * - Survivalist (Tier 3, Lv.60): Permanent -15% hunger/thirst depletion (handled by PermanentBuffManager)
 *
 * Note: XP boost abilities are now handled directly in the XP systems.
 * This handler triggers Tier 2 abilities when gathering occurs.
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
        // Item pickup events are handled by the ECS GatheringXpSystem
        // which calls checkTier2Ability when items are gathered
        logger.at(Level.FINE).log("Gathering ability handler registered");
    }

    /**
     * Called by GatheringXpSystem when an item is gathered.
     * Checks and applies Tier 2 ability (Nature's Gift).
     *
     * @param playerId The gathering player's UUID
     */
    public void onItemGathered(@Nonnull UUID playerId) {
        // Check Tier 2: Nature's Gift (restore 3 hunger and 3 thirst)
        if (abilitySystem.shouldTrigger(playerId, AbilityType.NATURES_GIFT)) {
            abilitySystem.applyTier2Effect(playerId, AbilityType.NATURES_GIFT);
            abilitySystem.logAbilityTrigger(playerId, AbilityType.NATURES_GIFT, "Hunger and thirst restored");
        }

        // Note: Tier 1 XP boost is handled in GatheringXpSystem via checkXpBoost()
        // Note: Tier 3 (Survivalist) is handled by PermanentBuffManager on login
    }
}
