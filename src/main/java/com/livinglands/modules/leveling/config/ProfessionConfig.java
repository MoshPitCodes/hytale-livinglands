package com.livinglands.modules.leveling.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a single profession including XP multiplier and abilities.
 *
 * Each profession has 3 abilities:
 * - Tier 1 (Lv.15): XP boosters or timed buffs
 * - Tier 2 (Lv.35): Resource management abilities
 * - Tier 3 (Lv.60): Permanent passive bonuses
 */
public class ProfessionConfig {
    public boolean enabled = true;
    public float xpMultiplier = 1.0f;
    public Map<String, AbilityConfig> abilities = new HashMap<>();

    public ProfessionConfig() {}

    public ProfessionConfig(boolean enabled, float xpMultiplier, Map<String, AbilityConfig> abilities) {
        this.enabled = enabled;
        this.xpMultiplier = xpMultiplier;
        this.abilities = abilities;
    }

    /**
     * Get the ability config for a specific ability.
     */
    public AbilityConfig getAbility(String abilityId) {
        return abilities.get(abilityId);
    }

    /**
     * Check if an ability is enabled and configured.
     */
    public boolean hasAbility(String abilityId) {
        var config = abilities.get(abilityId);
        return config != null && config.enabled;
    }

    // ========== Static Factory Methods ==========
    // Ability keys must match AbilityType.getConfigKey() values

    /**
     * Combat profession abilities:
     * - Adrenaline Rush (Lv.15): +20% speed for 10s after kill
     * - Warrior's Resilience (Lv.35): Restore 15% max health after kill
     * - Battle Hardened (Lv.60): Permanent +10% max health
     */
    public static ProfessionConfig combat() {
        var config = new ProfessionConfig();

        // Tier 1: Adrenaline Rush - +20% speed for 10 seconds after a kill
        config.abilities.put("adrenalineRush", AbilityConfig.tier1SpeedBuff(0.20f, 10.0f));

        // Tier 2: Warrior's Resilience - Restore 15% max health after a kill
        config.abilities.put("warriorsResilience", AbilityConfig.tier2StatRestore(0.15f));

        // Tier 3: Battle Hardened - Permanent +10% max health
        config.abilities.put("battleHardened", AbilityConfig.tier3Permanent(0.10f));

        return config;
    }

    /**
     * Mining profession abilities:
     * - Prospector's Eye (Lv.15): +50% mining XP
     * - Efficient Extraction (Lv.35): Pause hunger depletion for 30s
     * - Iron Constitution (Lv.60): Permanent +15% max stamina
     */
    public static ProfessionConfig mining() {
        var config = new ProfessionConfig();

        // Tier 1: Prospector's Eye - +50% mining XP
        config.abilities.put("prospectorsEye", AbilityConfig.tier1XpBoost(0.50f));

        // Tier 2: Efficient Extraction - Pause hunger depletion for 30 seconds
        config.abilities.put("efficientExtraction", AbilityConfig.tier2ResourceManagement(1.0f, 30.0f));

        // Tier 3: Iron Constitution - Permanent +15% max stamina
        config.abilities.put("ironConstitution", AbilityConfig.tier3Permanent(0.15f));

        return config;
    }

    /**
     * Logging profession abilities:
     * - Lumberjack's Vigor (Lv.15): +50% logging XP
     * - Forest's Blessing (Lv.35): Restore 5 energy when chopping
     * - Nature's Endurance (Lv.60): Permanent +10% movement speed
     */
    public static ProfessionConfig logging() {
        var config = new ProfessionConfig();

        // Tier 1: Lumberjack's Vigor - +50% logging XP
        config.abilities.put("lumberjacksVigor", AbilityConfig.tier1XpBoost(0.50f));

        // Tier 2: Forest's Blessing - Restore 5 energy (effectStrength = flat amount)
        config.abilities.put("forestsBlessing", AbilityConfig.tier2StatRestore(5.0f));

        // Tier 3: Nature's Endurance - Permanent +10% movement speed
        config.abilities.put("naturesEndurance", AbilityConfig.tier3Permanent(0.10f));

        return config;
    }

    /**
     * Building profession abilities:
     * - Architect's Focus (Lv.15): +100% building XP (double)
     * - Steady Hands (Lv.35): Pause stamina depletion for 30s
     * - Master Builder (Lv.60): Permanent +10% max stamina
     */
    public static ProfessionConfig building() {
        var config = new ProfessionConfig();

        // Tier 1: Architect's Focus - +100% building XP (double)
        config.abilities.put("architectsFocus", AbilityConfig.tier1XpBoost(1.00f));

        // Tier 2: Steady Hands - Pause stamina depletion for 30 seconds
        config.abilities.put("steadyHands", AbilityConfig.tier2ResourceManagement(1.0f, 30.0f));

        // Tier 3: Master Builder - Permanent +10% max stamina
        config.abilities.put("masterBuilder", AbilityConfig.tier3Permanent(0.10f));

        return config;
    }

    /**
     * Gathering profession abilities:
     * - Forager's Intuition (Lv.15): +50% gathering XP
     * - Nature's Gift (Lv.35): Restore 3 hunger and 3 thirst
     * - Survivalist (Lv.60): Permanent -15% hunger/thirst depletion
     */
    public static ProfessionConfig gathering() {
        var config = new ProfessionConfig();

        // Tier 1: Forager's Intuition - +50% gathering XP
        config.abilities.put("foragersIntuition", AbilityConfig.tier1XpBoost(0.50f));

        // Tier 2: Nature's Gift - Restore 3 hunger and 3 thirst (effectStrength = flat amount each)
        config.abilities.put("naturesGift", AbilityConfig.tier2StatRestore(3.0f));

        // Tier 3: Survivalist - Permanent -15% hunger/thirst depletion rate
        config.abilities.put("survivalist", AbilityConfig.tier3Permanent(0.15f));

        return config;
    }
}
