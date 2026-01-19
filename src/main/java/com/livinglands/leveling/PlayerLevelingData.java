package com.livinglands.leveling;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Immutable record representing a player's leveling data.
 * Tracks level, experience, skill points, and cumulative XP earned.
 *
 * Thread Safety: This record is immutable, but mutations return new instances.
 * The LevelingSystem maintains a ConcurrentHashMap of these records.
 */
public record PlayerLevelingData(
    @Nonnull UUID playerId,
    int level,              // Current level (1-100)
    double experience,      // Current XP progress toward next level
    int skillPoints,        // Unspent skill points (stored for future skill system)
    double totalXpEarned    // Cumulative XP earned (for statistics)
) {
    /**
     * Creates a new player starting at level 1 with no XP.
     */
    public static PlayerLevelingData create(@Nonnull UUID playerId) {
        return new PlayerLevelingData(playerId, LevelStat.MIN_LEVEL, 0.0, 0, 0.0);
    }

    /**
     * Adds experience to the player.
     * Returns a new instance with updated XP and possibly a new level.
     *
     * @param amount Amount of XP to add
     * @param baseXP Base XP per level (from config)
     * @param scalingFactor XP scaling factor (from config)
     * @param maxLevel Maximum allowed level (from config)
     * @param skillPointsPerLevel Skill points awarded per level (from config)
     * @return LevelUpResult containing new data and whether a level up occurred
     */
    public LevelUpResult addExperience(double amount,
                                       double baseXP,
                                       double scalingFactor,
                                       int maxLevel,
                                       int skillPointsPerLevel) {
        var newExperience = this.experience + amount;
        var newTotalXp = this.totalXpEarned + amount;
        var currentLevel = this.level;
        var currentSkillPoints = this.skillPoints;
        var levelsGained = 0;

        // Check for level ups (may be multiple if large XP gain)
        while (currentLevel < maxLevel && newExperience >= getXpForNextLevel(currentLevel, baseXP, scalingFactor)) {
            var xpNeeded = getXpForNextLevel(currentLevel, baseXP, scalingFactor);
            newExperience -= xpNeeded;
            currentLevel++;
            currentSkillPoints += skillPointsPerLevel;
            levelsGained++;
        }

        // Cap level at maximum
        if (currentLevel >= maxLevel) {
            currentLevel = maxLevel;
            newExperience = 0.0; // No overflow XP at max level
        }

        var newData = new PlayerLevelingData(
            this.playerId,
            currentLevel,
            newExperience,
            currentSkillPoints,
            newTotalXp
        );

        return new LevelUpResult(newData, levelsGained > 0, levelsGained, currentLevel);
    }

    /**
     * Sets level directly (for admin commands).
     * Adjusts skill points accordingly.
     *
     * @param newLevel Target level
     * @param maxLevel Maximum allowed level
     * @param skillPointsPerLevel Skill points per level
     * @return New PlayerLevelingData with updated level
     */
    public PlayerLevelingData setLevel(int newLevel, int maxLevel, int skillPointsPerLevel) {
        var clampedLevel = LevelStat.clampLevel(newLevel, maxLevel);
        var levelDifference = clampedLevel - this.level;
        var newSkillPoints = this.skillPoints + (levelDifference * skillPointsPerLevel);

        return new PlayerLevelingData(
            this.playerId,
            clampedLevel,
            0.0, // Reset XP when setting level
            Math.max(0, newSkillPoints), // Don't allow negative skill points
            this.totalXpEarned
        );
    }

    /**
     * Spends skill points (for future skill system).
     *
     * @param amount Skill points to spend
     * @return New PlayerLevelingData with reduced skill points, or same if insufficient
     */
    public PlayerLevelingData spendSkillPoints(int amount) {
        if (amount <= 0 || this.skillPoints < amount) {
            return this; // No change
        }

        return new PlayerLevelingData(
            this.playerId,
            this.level,
            this.experience,
            this.skillPoints - amount,
            this.totalXpEarned
        );
    }

    /**
     * Gets XP progress as a percentage (0.0-1.0).
     *
     * @param baseXP Base XP per level
     * @param scalingFactor XP scaling factor
     * @return Progress percentage
     */
    public double getXpProgressPercent(double baseXP, double scalingFactor) {
        if (this.level >= LevelStat.DEFAULT_MAX_LEVEL) {
            return 1.0; // Max level
        }

        var xpNeeded = getXpForNextLevel(this.level, baseXP, scalingFactor);
        if (xpNeeded <= 0) {
            return 0.0;
        }

        return Math.min(1.0, this.experience / xpNeeded);
    }

    private double getXpForNextLevel(int currentLevel, double baseXP, double scalingFactor) {
        return LevelStat.calculateXpForNextLevel(currentLevel, baseXP, scalingFactor);
    }

    /**
     * Result of adding experience to a player.
     */
    public record LevelUpResult(
        PlayerLevelingData newData,
        boolean leveledUp,
        int levelsGained,
        int newLevel
    ) {}
}
