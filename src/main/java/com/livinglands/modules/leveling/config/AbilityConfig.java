package com.livinglands.modules.leveling.config;

import com.livinglands.modules.leveling.ability.AbilityType;

/**
 * Configuration for a single ability including unlock level, chance scaling, and effect parameters.
 *
 * Abilities are organized into tiers:
 * - Tier 1 (Lv.15): XP boosters with chance-based triggers
 * - Tier 2 (Lv.35): Resource management with chance-based triggers
 * - Tier 3 (Lv.60): Permanent passive bonuses (always active)
 */
public class AbilityConfig {

    // Core settings
    public boolean enabled = true;
    public int unlockLevel = 15;

    // Chance settings (for non-permanent abilities)
    public float baseChance = 0.08f;        // 8% base chance
    public float chancePerLevel = 0.004f;   // 0.4% per level
    public float maxChance = 0.35f;         // 35% max chance

    // Effect settings
    public float effectDuration = 10.0f;    // Duration in seconds (for timed buffs)
    public float effectStrength = 0.20f;    // Multiplier/percentage (e.g., 0.20 = 20%)
    public boolean isPermanent = false;     // If true, always active once unlocked

    // Feedback settings
    public boolean showChatMessage = true;
    public boolean playSound = true;

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
     * Full constructor with all parameters.
     */
    public AbilityConfig(boolean enabled, int unlockLevel, float baseChance,
                         float chancePerLevel, float maxChance, float effectDuration,
                         float effectStrength, boolean isPermanent) {
        this.enabled = enabled;
        this.unlockLevel = unlockLevel;
        this.baseChance = baseChance;
        this.chancePerLevel = chancePerLevel;
        this.maxChance = maxChance;
        this.effectDuration = effectDuration;
        this.effectStrength = effectStrength;
        this.isPermanent = isPermanent;
    }

    /**
     * Calculate the trigger chance at a given level.
     * For permanent abilities, returns 1.0 if unlocked.
     *
     * @param level The player's profession level
     * @return The chance (0.0 to 1.0), or 0 if ability is locked
     */
    public float getChanceAtLevel(int level) {
        if (!enabled || level < unlockLevel) {
            return 0f;
        }

        // Permanent abilities always "trigger" once unlocked
        if (isPermanent) {
            return 1.0f;
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

    // ========== Static Factory Methods ==========

    /**
     * Create a Tier 1 ability config (XP boosters).
     * Unlocks at level 15, 10% base chance, 40% max, +50% XP effect.
     */
    public static AbilityConfig tier1XpBoost(float xpMultiplier) {
        var config = new AbilityConfig();
        config.unlockLevel = AbilityType.AbilityTier.TIER_1.getDefaultUnlockLevel();
        config.baseChance = 0.10f;
        config.chancePerLevel = 0.005f;
        config.maxChance = 0.40f;
        config.effectStrength = xpMultiplier;
        config.effectDuration = 0f;  // Instant effect
        config.isPermanent = false;
        return config;
    }

    /**
     * Create a Tier 1 ability config for Combat (timed speed buff).
     * Unlocks at level 15, 8% base chance, 35% max, +20% speed for 10s.
     */
    public static AbilityConfig tier1SpeedBuff(float speedMultiplier, float durationSeconds) {
        var config = new AbilityConfig();
        config.unlockLevel = AbilityType.AbilityTier.TIER_1.getDefaultUnlockLevel();
        config.baseChance = 0.08f;
        config.chancePerLevel = 0.004f;
        config.maxChance = 0.35f;
        config.effectStrength = speedMultiplier;
        config.effectDuration = durationSeconds;
        config.isPermanent = false;
        return config;
    }

    /**
     * Create a Tier 2 ability config (resource management).
     * Unlocks at level 35, 8% base chance, 30% max.
     */
    public static AbilityConfig tier2ResourceManagement(float effectStrength, float durationSeconds) {
        var config = new AbilityConfig();
        config.unlockLevel = AbilityType.AbilityTier.TIER_2.getDefaultUnlockLevel();
        config.baseChance = 0.08f;
        config.chancePerLevel = 0.003f;
        config.maxChance = 0.30f;
        config.effectStrength = effectStrength;
        config.effectDuration = durationSeconds;
        config.isPermanent = false;
        return config;
    }

    /**
     * Create a Tier 2 ability config for instant stat restore.
     * Unlocks at level 35, 6% base chance, 25% max.
     */
    public static AbilityConfig tier2StatRestore(float restoreAmount) {
        var config = new AbilityConfig();
        config.unlockLevel = AbilityType.AbilityTier.TIER_2.getDefaultUnlockLevel();
        config.baseChance = 0.06f;
        config.chancePerLevel = 0.003f;
        config.maxChance = 0.25f;
        config.effectStrength = restoreAmount;
        config.effectDuration = 0f;  // Instant effect
        config.isPermanent = false;
        return config;
    }

    /**
     * Create a Tier 3 permanent ability config.
     * Unlocks at level 60, always active once unlocked.
     */
    public static AbilityConfig tier3Permanent(float statBonus) {
        var config = new AbilityConfig();
        config.unlockLevel = AbilityType.AbilityTier.TIER_3.getDefaultUnlockLevel();
        config.baseChance = 1.0f;
        config.chancePerLevel = 0f;
        config.maxChance = 1.0f;
        config.effectStrength = statBonus;
        config.effectDuration = 0f;  // Permanent
        config.isPermanent = true;
        return config;
    }

    // ========== Legacy Factory Methods (for backwards compatibility) ==========

    /**
     * @deprecated Use tier-specific factory methods instead.
     */
    @Deprecated
    public static AbilityConfig fortune(int unlockLevel) {
        return new AbilityConfig(true, unlockLevel, 0.05f, 0.006f, 0.50f);
    }

    /**
     * @deprecated Use tier-specific factory methods instead.
     */
    @Deprecated
    public static AbilityConfig rareAbility(int unlockLevel) {
        return new AbilityConfig(true, unlockLevel, 0.10f, 0.008f, 0.50f);
    }

    /**
     * @deprecated Use tier3Permanent() instead.
     */
    @Deprecated
    public static AbilityConfig highLevelAbility(int unlockLevel) {
        return new AbilityConfig(true, unlockLevel, 1.0f, 0f, 1.0f);
    }
}
