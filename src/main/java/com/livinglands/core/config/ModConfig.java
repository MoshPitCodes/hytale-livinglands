package com.livinglands.core.config;

/**
 * Main configuration for Living Lands mod.
 * Uses Java 25 records for immutable configuration.
 */
public record ModConfig(
    MetabolismConfig metabolism,
    ConsumablesConfig consumables,
    SleepConfig sleep,
    DebuffsConfig debuffs
) {
    /**
     * Creates a default configuration instance.
     */
    public static ModConfig defaultConfig() {
        return new ModConfig(
            new MetabolismConfig(),
            ConsumablesConfig.defaultConfig(),
            SleepConfig.defaultConfig(),
            DebuffsConfig.defaultConfig()
        );
    }

    /**
     * Default constructor with default values.
     */
    public ModConfig() {
        this(new MetabolismConfig(), ConsumablesConfig.defaultConfig(), SleepConfig.defaultConfig(), DebuffsConfig.defaultConfig());
    }
}
