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
 * Handler for combat-related passive abilities.
 *
 * New Abilities:
 * - Adrenaline Rush (Tier 1, Lv.15): +20% speed for 10s after kill
 * - Warrior's Resilience (Tier 2, Lv.35): Restore 15% max health after kill
 * - Battle Hardened (Tier 3, Lv.60): Permanent +10% max health (handled by PermanentBuffManager)
 *
 * Note: Kill events are handled by CombatXpSystem (ECS).
 * This handler provides methods that can be called by CombatXpSystem when kills occur.
 */
public class CombatAbilityHandler {

    private final AbilitySystem abilitySystem;
    private final PlayerRegistry playerRegistry;
    private final HytaleLogger logger;

    public CombatAbilityHandler(@Nonnull AbilitySystem abilitySystem,
                                @Nonnull PlayerRegistry playerRegistry,
                                @Nonnull HytaleLogger logger) {
        this.abilitySystem = abilitySystem;
        this.playerRegistry = playerRegistry;
        this.logger = logger;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Kill events are handled by CombatXpSystem (ECS) which calls onKill()
        logger.at(Level.FINE).log("Combat ability handler registered");
    }

    /**
     * Called by CombatXpSystem when a player kills an entity.
     * Checks and applies combat abilities.
     *
     * @param playerId The killing player's UUID
     */
    public void onKill(@Nonnull UUID playerId) {
        // Check Tier 1: Adrenaline Rush (+20% speed for 10s)
        if (abilitySystem.shouldTrigger(playerId, AbilityType.ADRENALINE_RUSH)) {
            abilitySystem.applyAdrenalineRush(playerId);
            abilitySystem.logAbilityTrigger(playerId, AbilityType.ADRENALINE_RUSH, "Speed boost activated");
        }

        // Check Tier 2: Warrior's Resilience (health restore)
        if (abilitySystem.shouldTrigger(playerId, AbilityType.WARRIORS_RESILIENCE)) {
            abilitySystem.applyTier2Effect(playerId, AbilityType.WARRIORS_RESILIENCE);
            abilitySystem.logAbilityTrigger(playerId, AbilityType.WARRIORS_RESILIENCE, "Health restored");
        }

        // Note: Tier 3 (Battle Hardened) is handled by PermanentBuffManager on login
    }
}
