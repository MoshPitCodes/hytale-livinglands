package com.livinglands.modules.leveling.ability;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.config.AbilityConfig;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

/**
 * System for managing passive abilities and their triggers.
 * Handles ability chance calculations and trigger checks.
 */
public class AbilitySystem {

    private final LevelingSystem levelingSystem;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;
    private final Random random;

    public AbilitySystem(@Nonnull LevelingSystem levelingSystem,
                         @Nonnull LevelingModuleConfig config,
                         @Nonnull HytaleLogger logger) {
        this.levelingSystem = levelingSystem;
        this.config = config;
        this.logger = logger;
        this.random = new Random();
    }

    /**
     * Check if an ability should trigger for a player.
     *
     * @param playerId The player's UUID
     * @param ability The ability to check
     * @return true if the ability triggers
     */
    public boolean shouldTrigger(@Nonnull UUID playerId, @Nonnull AbilityType ability) {
        // Get the ability config
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null || !abilityConfig.enabled) {
            return false;
        }

        // Get player's level in the relevant profession
        int level = levelingSystem.getLevel(playerId, ability.getProfession());

        // Calculate trigger chance
        float chance = abilityConfig.getChanceAtLevel(level);
        if (chance <= 0) {
            return false;
        }

        // Roll for trigger
        boolean triggered = random.nextFloat() < chance;

        if (triggered) {
            logger.at(Level.FINE).log("Ability %s triggered for player %s (level %d, chance %.2f%%)",
                ability.getDisplayName(), playerId, level, chance * 100);
        }

        return triggered;
    }

    /**
     * Get the trigger chance for an ability at a player's current level.
     *
     * @param playerId The player's UUID
     * @param ability The ability to check
     * @return The trigger chance (0.0 to 1.0)
     */
    public float getTriggerChance(@Nonnull UUID playerId, @Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null || !abilityConfig.enabled) {
            return 0f;
        }

        int level = levelingSystem.getLevel(playerId, ability.getProfession());
        return abilityConfig.getChanceAtLevel(level);
    }

    /**
     * Check if a player has unlocked an ability.
     *
     * @param playerId The player's UUID
     * @param ability The ability to check
     * @return true if the ability is unlocked
     */
    public boolean isUnlocked(@Nonnull UUID playerId, @Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null || !abilityConfig.enabled) {
            return false;
        }

        int level = levelingSystem.getLevel(playerId, ability.getProfession());
        return level >= abilityConfig.unlockLevel;
    }

    /**
     * Get the level required to unlock an ability.
     *
     * @param ability The ability to check
     * @return The unlock level, or -1 if ability is disabled
     */
    public int getUnlockLevel(@Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null || !abilityConfig.enabled) {
            return -1;
        }
        return abilityConfig.unlockLevel;
    }

    /**
     * Get the max chance for an ability.
     *
     * @param ability The ability to check
     * @return The max chance (0.0 to 1.0), or 0 if ability is disabled
     */
    public float getMaxChance(@Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null || !abilityConfig.enabled) {
            return 0f;
        }
        return abilityConfig.maxChance;
    }

    /**
     * Apply critical strike damage multiplier.
     *
     * @param baseDamage The base damage amount
     * @return The modified damage (1.5x if crit triggers)
     */
    public float applyCriticalStrike(float baseDamage) {
        AbilityConfig crit = config.getAbilityConfig(AbilityType.CRITICAL_STRIKE);
        float multiplier = crit != null ? 1.5f : 1.5f; // Could make configurable
        return baseDamage * multiplier;
    }

    /**
     * Calculate lifesteal healing amount.
     *
     * @param damageDealt The damage dealt
     * @return The amount of health to restore (10% of damage)
     */
    public float calculateLifesteal(float damageDealt) {
        AbilityConfig lifesteal = config.getAbilityConfig(AbilityType.LIFESTEAL);
        float percentage = lifesteal != null ? 0.10f : 0.10f; // Could make configurable
        return damageDealt * percentage;
    }

    /**
     * Get double drop multiplier for mining/gathering abilities.
     *
     * @return 2 for double drops
     */
    public int getDoubleDropMultiplier() {
        return 2;
    }

    /**
     * Log ability trigger for debugging/analytics.
     */
    public void logAbilityTrigger(@Nonnull UUID playerId, @Nonnull AbilityType ability, String details) {
        logger.at(Level.INFO).log("[Ability] %s triggered %s: %s",
            playerId, ability.getDisplayName(), details);
    }
}
