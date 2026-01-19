package com.livinglands.core.config;

/**
 * Configuration for the player leveling system.
 * Uses Java 25 records for immutable configuration.
 */
public record LevelingConfig(
    boolean enabled,
    int maxLevel,
    double baseXpPerLevel,
    double xpScalingFactor,
    double healthPerLevel,
    double staminaPerLevel,
    int skillPointsPerLevel
) {
    /**
     * Creates a default leveling configuration.
     */
    public static LevelingConfig defaults() {
        return new LevelingConfig(
            true,      // enabled
            100,       // maxLevel
            100.0,     // baseXpPerLevel (XP for level 2)
            1.15,      // xpScalingFactor (15% more per level)
            5.0,       // healthPerLevel (+5 max HP per level)
            3.0,       // staminaPerLevel (+3 max stamina per level)
            1          // skillPointsPerLevel (for future skill system)
        );
    }

    /**
     * Validates configuration values.
     */
    public LevelingConfig {
        if (maxLevel < 1 || maxLevel > 1000) {
            throw new IllegalArgumentException("maxLevel must be between 1 and 1000");
        }
        if (baseXpPerLevel <= 0) {
            throw new IllegalArgumentException("baseXpPerLevel must be positive");
        }
        if (xpScalingFactor < 1.0) {
            throw new IllegalArgumentException("xpScalingFactor must be >= 1.0");
        }
        if (healthPerLevel < 0 || staminaPerLevel < 0) {
            throw new IllegalArgumentException("stat bonuses cannot be negative");
        }
        if (skillPointsPerLevel < 0) {
            throw new IllegalArgumentException("skillPointsPerLevel cannot be negative");
        }
    }
}
