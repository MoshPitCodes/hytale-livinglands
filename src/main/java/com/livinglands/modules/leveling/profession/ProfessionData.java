package com.livinglands.modules.leveling.profession;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Data class representing a player's progress in a single profession.
 * Stores the current level, XP, and XP required for the next level.
 * Thread-safe for concurrent access.
 */
public class ProfessionData {
    private final AtomicInteger level;
    private final AtomicLong currentXp;
    private long xpToNextLevel; // Only modified in synchronized levelUp()

    public ProfessionData() {
        this(1, 0, 100);
    }

    public ProfessionData(int level, long currentXp, long xpToNextLevel) {
        this.level = new AtomicInteger(level);
        this.currentXp = new AtomicLong(currentXp);
        this.xpToNextLevel = xpToNextLevel;
    }

    /**
     * Create initial profession data at level 1 with 0 XP.
     */
    public static ProfessionData initial(long xpForLevel2) {
        return new ProfessionData(1, 0, xpForLevel2);
    }

    public int getLevel() {
        return level.get();
    }

    public void setLevel(int level) {
        this.level.set(level);
    }

    public long getCurrentXp() {
        return currentXp.get();
    }

    public void setCurrentXp(long currentXp) {
        this.currentXp.set(currentXp);
    }

    public synchronized long getXpToNextLevel() {
        return xpToNextLevel;
    }

    public synchronized void setXpToNextLevel(long xpToNextLevel) {
        this.xpToNextLevel = xpToNextLevel;
    }

    /**
     * Add XP to this profession. Does not handle level-up logic.
     * Thread-safe atomic operation.
     *
     * @param xp The XP to add
     */
    public void addXp(long xp) {
        this.currentXp.addAndGet(xp);
    }

    /**
     * Get the progress percentage towards the next level.
     * Thread-safe snapshot of current progress.
     *
     * @return Progress from 0.0 to 1.0
     */
    public synchronized float getProgressPercent() {
        if (xpToNextLevel <= 0) return 1.0f;
        return Math.min(1.0f, (float) currentXp.get() / xpToNextLevel);
    }

    /**
     * Check if this profession has enough XP to level up.
     * Thread-safe snapshot check.
     */
    public synchronized boolean canLevelUp() {
        return currentXp.get() >= xpToNextLevel;
    }

    /**
     * Perform a level up, consuming XP and incrementing level.
     * Synchronized to ensure atomic multi-field update.
     *
     * @param newXpToNextLevel The XP required for the next level after this one
     */
    public synchronized void levelUp(long newXpToNextLevel) {
        currentXp.addAndGet(-xpToNextLevel);
        level.incrementAndGet();
        xpToNextLevel = newXpToNextLevel;
    }

    @Override
    public synchronized String toString() {
        return String.format("ProfessionData{level=%d, xp=%d/%d (%.1f%%)}",
            level.get(), currentXp.get(), xpToNextLevel, getProgressPercent() * 100);
    }
}
