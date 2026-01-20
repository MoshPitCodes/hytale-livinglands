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
 *
 * New Abilities:
 * - Prospector's Eye (Tier 1, Lv.15): +50% mining XP (handled by MiningXpSystem)
 * - Efficient Extraction (Tier 2, Lv.35): Pause hunger depletion for 30s
 * - Iron Constitution (Tier 3, Lv.60): Permanent +15% max stamina (handled by PermanentBuffManager)
 *
 * Note: XP boost abilities are now handled directly in the XP systems.
 * This handler triggers Tier 2 abilities when mining occurs.
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
        // Block break events are handled by the ECS MiningXpSystem
        // which calls checkTier2Ability when ores are mined
        logger.at(Level.FINE).log("Mining ability handler registered");
    }

    /**
     * Called by MiningXpSystem when an ore is mined.
     * Checks and applies Tier 2 ability (Efficient Extraction).
     *
     * @param playerId The mining player's UUID
     */
    public void onOreMined(@Nonnull UUID playerId) {
        // Check Tier 2: Efficient Extraction (pause hunger for 30s)
        if (abilitySystem.shouldTrigger(playerId, AbilityType.EFFICIENT_EXTRACTION)) {
            abilitySystem.applyTier2Effect(playerId, AbilityType.EFFICIENT_EXTRACTION);
            abilitySystem.logAbilityTrigger(playerId, AbilityType.EFFICIENT_EXTRACTION, "Hunger paused");
        }

        // Note: Tier 1 XP boost is handled in MiningXpSystem via checkXpBoost()
        // Note: Tier 3 (Iron Constitution) is handled by PermanentBuffManager on login
    }
}
