package com.livinglands.modules.leveling.config;

/**
 * Configuration for a single ability including unlock level and chance scaling.
 */
public class AbilityConfig {
    public boolean enabled = true;
    public int unlockLevel = 1;
    public float baseChance = 0.05f;        // 5% base chance
    public float chancePerLevel = 0.006f;   // 0.6% per level (3% per 5 levels)
    public float maxChance = 0.50f;         // 50% max chance

    public AbilityConfig() {}

    public AbilityConfig(boolean enabled, int unlockLevel, float baseChance,
                         float chancePerLevel, float maxChance) {
        this.enabled = enabled;
        this.unlockLevel = unlockLevel;
        this.baseChance = baseChance;
        this.chancePerLevel = chancePerLevel;
        this.maxChance = maxChance;
    }

    /**
     * Calculate the trigger chance at a given level.
     *
     * @param level The player's profession level
     * @return The chance (0.0 to 1.0), or 0 if ability is locked
     */
    public float getChanceAtLevel(int level) {
        if (!enabled || level < unlockLevel) {
            return 0f;
        }
        int levelsAboveUnlock = level - unlockLevel;
        float chance = baseChance + (levelsAboveUnlock * chancePerLevel);
        return Math.min(chance, maxChance);
    }

    /**
     * Check if the ability is unlocked at the given level.
     */
    public boolean isUnlocked(int level) {
        return enabled && level >= unlockLevel;
    }

    // Static factory methods for common ability configurations

    public static AbilityConfig fortune(int unlockLevel) {
        return new AbilityConfig(true, unlockLevel, 0.05f, 0.006f, 0.50f);
    }

    public static AbilityConfig rareAbility(int unlockLevel) {
        return new AbilityConfig(true, unlockLevel, 0.10f, 0.008f, 0.50f);
    }

    public static AbilityConfig highLevelAbility(int unlockLevel) {
        return new AbilityConfig(true, unlockLevel, 1.0f, 0f, 1.0f); // Always triggers once unlocked
    }
}
