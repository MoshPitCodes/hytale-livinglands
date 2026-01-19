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
 * Handles Critical Strike and Lifesteal abilities.
 *
 * Note: Direct damage events are not currently available in the Hytale API.
 * This handler provides methods that can be called when damage events become available.
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
        // Note: Damage events are not available in the current Hytale API.
        // This handler provides trigger methods for when events are available.
        logger.at(Level.INFO).log("Combat ability handler registered (damage events pending)");
    }

    /**
     * Check and apply Critical Strike when damage is dealt.
     * Returns the modified damage amount.
     *
     * @param playerId The attacking player
     * @param baseDamage The base damage amount
     * @return Modified damage (1.5x if crit triggers, else unchanged)
     */
    public float checkCriticalStrike(@Nonnull UUID playerId, float baseDamage) {
        if (abilitySystem.shouldTrigger(playerId, AbilityType.CRITICAL_STRIKE)) {
            float modifiedDamage = abilitySystem.applyCriticalStrike(baseDamage);
            abilitySystem.logAbilityTrigger(playerId, AbilityType.CRITICAL_STRIKE,
                String.format("%.1f -> %.1f damage", baseDamage, modifiedDamage));
            return modifiedDamage;
        }
        return baseDamage;
    }

    /**
     * Check and apply Lifesteal when damage is dealt.
     * Returns the healing amount.
     *
     * @param playerId The attacking player
     * @param damageDealt The damage dealt
     * @return Healing amount (10% of damage if triggers, else 0)
     */
    public float checkLifesteal(@Nonnull UUID playerId, float damageDealt) {
        if (abilitySystem.shouldTrigger(playerId, AbilityType.LIFESTEAL)) {
            float healAmount = abilitySystem.calculateLifesteal(damageDealt);
            abilitySystem.logAbilityTrigger(playerId, AbilityType.LIFESTEAL,
                String.format("Healed %.1f HP from %.1f damage", healAmount, damageDealt));
            return healAmount;
        }
        return 0f;
    }
}
