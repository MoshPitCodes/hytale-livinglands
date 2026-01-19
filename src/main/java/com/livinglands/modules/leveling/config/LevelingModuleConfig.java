package com.livinglands.modules.leveling.config;

import com.livinglands.modules.leveling.profession.ProfessionType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Main configuration for the Leveling module.
 * Saved to LivingLands/leveling/config.json
 */
public class LevelingModuleConfig {

    // General settings
    public int maxLevel = 99;
    public long baseXp = 100;
    public double xpExponent = 1.5;

    // UI settings
    public boolean enableHud = true;
    public boolean showXpNotifications = true;
    public boolean showLevelUpTitles = true;
    public int xpNotificationCooldownMs = 500;  // Minimum time between XP notifications

    // Metabolism integration
    public boolean metabolismIntegration = true;
    public float wellFedXpMultiplier = 1.25f;     // 25% bonus when all stats > 70
    public float starvingXpMultiplier = 0.50f;    // 50% penalty when any stat < 20
    public int wellFedThreshold = 70;             // Stats must be above this for bonus
    public int starvingThreshold = 20;            // Stats below this incur penalty

    // Per-profession configurations
    public Map<String, ProfessionConfig> professions = defaultProfessions();

    // XP sources configuration
    public XpSourceConfig xpSources = XpSourceConfig.defaults();

    public LevelingModuleConfig() {}

    /**
     * Create default profession configurations.
     */
    public static Map<String, ProfessionConfig> defaultProfessions() {
        var map = new EnumMap<ProfessionType, ProfessionConfig>(ProfessionType.class);
        map.put(ProfessionType.COMBAT, ProfessionConfig.combat());
        map.put(ProfessionType.MINING, ProfessionConfig.mining());
        map.put(ProfessionType.BUILDING, ProfessionConfig.building());
        map.put(ProfessionType.LOGGING, ProfessionConfig.logging());
        map.put(ProfessionType.GATHERING, ProfessionConfig.gathering());

        // Convert to String keys for JSON serialization
        Map<String, ProfessionConfig> result = new java.util.HashMap<>();
        for (var entry : map.entrySet()) {
            result.put(entry.getKey().name().toLowerCase(), entry.getValue());
        }
        return result;
    }

    /**
     * Create default configuration with all default values.
     */
    public static LevelingModuleConfig defaults() {
        return new LevelingModuleConfig();
    }

    /**
     * Get configuration for a specific profession.
     */
    public ProfessionConfig getProfession(ProfessionType type) {
        return professions.get(type.name().toLowerCase());
    }

    /**
     * Get configuration for a specific profession by name.
     */
    public ProfessionConfig getProfession(String professionId) {
        return professions.get(professionId.toLowerCase());
    }

    /**
     * Check if a profession is enabled.
     */
    public boolean isProfessionEnabled(ProfessionType type) {
        var config = getProfession(type);
        return config != null && config.enabled;
    }

    /**
     * Get ability configuration for a specific profession and ability.
     */
    public AbilityConfig getAbilityConfig(ProfessionType profession, String abilityId) {
        var profConfig = getProfession(profession);
        if (profConfig == null) return null;
        return profConfig.getAbility(abilityId);
    }

    /**
     * Get ability configuration by AbilityType.
     * Convenience method that extracts profession and config key from the AbilityType.
     */
    public AbilityConfig getAbilityConfig(com.livinglands.modules.leveling.ability.AbilityType ability) {
        return getAbilityConfig(ability.getProfession(), ability.getConfigKey());
    }

    /**
     * Get the XP multiplier for a profession (from config).
     */
    public float getProfessionXpMultiplier(ProfessionType type) {
        var config = getProfession(type);
        return config != null ? config.xpMultiplier : 1.0f;
    }
}
