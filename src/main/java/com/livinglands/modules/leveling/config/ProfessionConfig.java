package com.livinglands.modules.leveling.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a single profession including XP multiplier and abilities.
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

    // Static factory methods for default profession configurations

    public static ProfessionConfig combat() {
        var config = new ProfessionConfig();
        config.abilities.put("parry", new AbilityConfig(true, 2, 0.0f, 0.0f, 1.0f)); // Always active once unlocked
        config.abilities.put("conditioned", AbilityConfig.highLevelAbility(50));
        return config;
    }

    public static ProfessionConfig mining() {
        var config = new ProfessionConfig();
        config.abilities.put("block_burst", new AbilityConfig(true, 2, 0.05f, 0.006f, 0.50f));
        config.abilities.put("ore_fortune", AbilityConfig.fortune(1));
        config.abilities.put("vein_miner", AbilityConfig.highLevelAbility(50));
        return config;
    }

    public static ProfessionConfig building() {
        var config = new ProfessionConfig();
        // Building has no special abilities currently
        return config;
    }

    public static ProfessionConfig logging() {
        var config = new ProfessionConfig();
        config.abilities.put("precision_cut", AbilityConfig.fortune(1));
        config.abilities.put("lumberjack_harvest", new AbilityConfig(true, 10, 0.10f, 0.005f, 0.50f));
        config.abilities.put("lumber_rage", new AbilityConfig(true, 50, 0.05f, 0.005f, 0.30f));
        return config;
    }

    public static ProfessionConfig gathering() {
        var config = new ProfessionConfig();
        config.abilities.put("gathering_fortune", AbilityConfig.fortune(1));
        return config;
    }
}
