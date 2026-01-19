package com.livinglands.modules.leveling.profession;

/**
 * Calculates XP requirements for each level based on configurable formula.
 * Default formula: baseXp * level^exponent
 */
public class XpCalculator {

    private final int maxLevel;
    private final long baseXp;
    private final double exponent;
    private final long[] xpTable;

    /**
     * Create an XP calculator with custom parameters.
     *
     * @param maxLevel Maximum achievable level
     * @param baseXp Base XP for level 2
     * @param exponent Exponential scaling factor
     */
    public XpCalculator(int maxLevel, long baseXp, double exponent) {
        this.maxLevel = maxLevel;
        this.baseXp = baseXp;
        this.exponent = exponent;
        this.xpTable = precomputeXpTable();
    }

    /**
     * Create an XP calculator with default values.
     * Default: maxLevel=99, baseXp=100, exponent=1.5
     */
    public static XpCalculator defaults() {
        return new XpCalculator(99, 100, 1.5);
    }

    /**
     * Precompute XP requirements for all levels for fast lookup.
     */
    private long[] precomputeXpTable() {
        long[] table = new long[maxLevel + 2];
        table[0] = 0;
        table[1] = 0; // Level 1 requires 0 XP (starting level)

        for (int level = 2; level <= maxLevel + 1; level++) {
            table[level] = calculateXpForLevel(level);
        }
        return table;
    }

    /**
     * Calculate the XP required to reach a specific level.
     *
     * @param level The target level
     * @return XP required
     */
    private long calculateXpForLevel(int level) {
        if (level <= 1) return 0;
        return Math.round(baseXp * Math.pow(level - 1, exponent));
    }

    /**
     * Get the XP required to advance from the given level to the next.
     *
     * @param currentLevel The current level
     * @return XP needed to reach currentLevel + 1
     */
    public long getXpToNextLevel(int currentLevel) {
        if (currentLevel >= maxLevel) {
            return Long.MAX_VALUE; // Max level reached
        }
        if (currentLevel < 1) {
            return xpTable[2];
        }
        return xpTable[currentLevel + 1];
    }

    /**
     * Get the total XP accumulated to reach a specific level from level 1.
     *
     * @param level The target level
     * @return Total cumulative XP
     */
    public long getTotalXpForLevel(int level) {
        if (level <= 1) return 0;
        if (level > maxLevel) level = maxLevel;

        long total = 0;
        for (int l = 2; l <= level; l++) {
            total += xpTable[l];
        }
        return total;
    }

    /**
     * Calculate what level a player would be at given total XP.
     *
     * @param totalXp The total accumulated XP
     * @return The level and remaining XP
     */
    public LevelResult calculateLevelFromTotalXp(long totalXp) {
        long remaining = totalXp;
        int level = 1;

        while (level < maxLevel && remaining >= xpTable[level + 1]) {
            remaining -= xpTable[level + 1];
            level++;
        }

        return new LevelResult(level, remaining, xpTable[Math.min(level + 1, maxLevel + 1)]);
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public long getBaseXp() {
        return baseXp;
    }

    public double getExponent() {
        return exponent;
    }

    /**
     * Result of level calculation containing level and remaining XP.
     */
    public record LevelResult(int level, long currentXp, long xpToNextLevel) {
        public ProfessionData toProfessionData() {
            return new ProfessionData(level, currentXp, xpToNextLevel);
        }
    }
}
