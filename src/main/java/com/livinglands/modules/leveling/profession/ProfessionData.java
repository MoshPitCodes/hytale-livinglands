package com.livinglands.modules.leveling.profession;

/**
 * Data class representing a player's progress in a single profession.
 * Stores the current level, XP, and XP required for the next level.
 */
public class ProfessionData {
    private int level;
    private long currentXp;
    private long xpToNextLevel;

    public ProfessionData() {
        this(1, 0, 100);
    }

    public ProfessionData(int level, long currentXp, long xpToNextLevel) {
        this.level = level;
        this.currentXp = currentXp;
        this.xpToNextLevel = xpToNextLevel;
    }

    /**
     * Create initial profession data at level 1 with 0 XP.
     */
    public static ProfessionData initial(long xpForLevel2) {
        return new ProfessionData(1, 0, xpForLevel2);
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getCurrentXp() {
        return currentXp;
    }

    public void setCurrentXp(long currentXp) {
        this.currentXp = currentXp;
    }

    public long getXpToNextLevel() {
        return xpToNextLevel;
    }

    public void setXpToNextLevel(long xpToNextLevel) {
        this.xpToNextLevel = xpToNextLevel;
    }

    /**
     * Add XP to this profession. Does not handle level-up logic.
     *
     * @param xp The XP to add
     */
    public void addXp(long xp) {
        this.currentXp += xp;
    }

    /**
     * Get the progress percentage towards the next level.
     *
     * @return Progress from 0.0 to 1.0
     */
    public float getProgressPercent() {
        if (xpToNextLevel <= 0) return 1.0f;
        return Math.min(1.0f, (float) currentXp / xpToNextLevel);
    }

    /**
     * Check if this profession has enough XP to level up.
     */
    public boolean canLevelUp() {
        return currentXp >= xpToNextLevel;
    }

    /**
     * Perform a level up, consuming XP and incrementing level.
     *
     * @param newXpToNextLevel The XP required for the next level after this one
     */
    public void levelUp(long newXpToNextLevel) {
        currentXp -= xpToNextLevel;
        level++;
        xpToNextLevel = newXpToNextLevel;
    }

    @Override
    public String toString() {
        return String.format("ProfessionData{level=%d, xp=%d/%d (%.1f%%)}",
            level, currentXp, xpToNextLevel, getProgressPercent() * 100);
    }
}
