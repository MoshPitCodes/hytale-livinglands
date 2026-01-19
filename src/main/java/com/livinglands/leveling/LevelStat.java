package com.livinglands.leveling;

/**
 * Constants and utilities for the leveling system.
 *
 * Level Bounds:
 * - MIN_LEVEL: 1 (starting level)
 * - MAX_LEVEL: 100 (configurable maximum)
 *
 * XP Formula: baseXP * pow(scalingFactor, level - 2)
 * - Level 1->2: baseXP (e.g., 100 XP)
 * - Level 2->3: baseXP * scalingFactor (e.g., 115 XP)
 * - Level 3->4: baseXP * scalingFactor^2 (e.g., 132 XP)
 *
 * Example with baseXP=100, scaling=1.15:
 * - Level 1->2:  100 XP
 * - Level 10->11: 398 XP
 * - Level 50->51: 28,062 XP
 * - Level 99->100: 1,174,313 XP
 */
public final class LevelStat {

    // Level bounds
    public static final int MIN_LEVEL = 1;
    public static final int DEFAULT_MAX_LEVEL = 100;

    // Default XP configuration (overridden by LevelingConfig)
    public static final double DEFAULT_BASE_XP = 100.0;
    public static final double DEFAULT_SCALING_FACTOR = 1.15;

    private LevelStat() {
        // Utility class - prevent instantiation
    }

    /**
     * Calculates XP required to reach the next level.
     *
     * @param currentLevel Current player level (1-99)
     * @param baseXP Base XP for level 2
     * @param scalingFactor Exponential scaling factor (typically 1.1-1.2)
     * @return XP required to level up
     */
    public static double calculateXpForNextLevel(int currentLevel, double baseXP, double scalingFactor) {
        if (currentLevel < MIN_LEVEL) {
            return 0.0;
        }

        // Formula: baseXP * scalingFactor^(level - 2)
        // Level 1->2: baseXP * 1.15^0 = baseXP
        // Level 2->3: baseXP * 1.15^1 = baseXP * 1.15
        var exponent = currentLevel - 1;
        return baseXP * Math.pow(scalingFactor, exponent - 1);
    }

    /**
     * Calculates total XP needed from level 1 to reach a specific level.
     * Useful for tracking cumulative XP progress.
     *
     * @param targetLevel Target level (2-100)
     * @param baseXP Base XP for level 2
     * @param scalingFactor Exponential scaling factor
     * @return Total XP required from level 1
     */
    public static double calculateTotalXpForLevel(int targetLevel, double baseXP, double scalingFactor) {
        if (targetLevel <= MIN_LEVEL) {
            return 0.0;
        }

        var totalXP = 0.0;
        for (var level = MIN_LEVEL; level < targetLevel; level++) {
            totalXP += calculateXpForNextLevel(level, baseXP, scalingFactor);
        }
        return totalXP;
    }

    /**
     * Validates a level value is within bounds.
     *
     * @param level Level to validate
     * @param maxLevel Maximum allowed level
     * @return Clamped level within [MIN_LEVEL, maxLevel]
     */
    public static int clampLevel(int level, int maxLevel) {
        return Math.max(MIN_LEVEL, Math.min(level, maxLevel));
    }
}
