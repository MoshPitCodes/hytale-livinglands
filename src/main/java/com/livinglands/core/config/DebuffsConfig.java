package com.livinglands.core.config;

/**
 * Configuration for metabolism debuff effects.
 * Defines what happens when stats reach critical/zero levels.
 */
public record DebuffsConfig(
    HungerDebuffs hunger,
    ThirstDebuffs thirst,
    EnergyDebuffs energy
) {
    /**
     * Creates default debuffs configuration.
     */
    public static DebuffsConfig defaultConfig() {
        return new DebuffsConfig(
            HungerDebuffs.defaults(),
            ThirstDebuffs.defaults(),
            EnergyDebuffs.defaults()
        );
    }

    /**
     * Hunger debuff configuration.
     * At 0 hunger, player takes damage that increases over time.
     * Damage stops when hunger recovers to the recovery threshold.
     */
    public record HungerDebuffs(
        boolean enabled,
        double damageStartThreshold,       // Hunger level at/below which damage starts (e.g., 0)
        double recoveryThreshold,          // Hunger level needed to stop damage (e.g., 30)
        float damageTickIntervalSeconds,   // How often to deal damage
        float initialDamage,               // Starting damage per tick
        float damageIncreasePerTick,       // How much damage increases each tick
        float maxDamage                    // Maximum damage per tick
    ) {
        public static HungerDebuffs defaults() {
            return new HungerDebuffs(
                true,
                0.0,    // Start damage at 0 hunger
                30.0,   // Stop damage when hunger reaches 30
                3.0f,   // Damage every 3 seconds
                1.0f,   // Start with 1 damage
                0.5f,   // Increase by 0.5 each tick
                5.0f    // Max 5 damage per tick
            );
        }
    }

    /**
     * Thirst debuff configuration.
     * Low thirst causes visual impairment, reduced speed/stamina, and damage at 0.
     * Damage stops when thirst recovers to the recovery threshold.
     */
    public record ThirstDebuffs(
        boolean enabled,
        double damageStartThreshold,      // Thirst level at/below which damage starts (e.g., 0)
        double recoveryThreshold,         // Thirst level needed to stop damage (e.g., 30)
        double blurStartThreshold,        // Thirst level where blur starts (e.g., 20)
        float damageTickIntervalSeconds,  // How often to deal damage at 0
        float damageAtZero,               // Damage per tick at 0 thirst
        double slowStartThreshold,        // Thirst level where slowdown starts (e.g., 30)
        float minSpeedMultiplier,         // Minimum speed at 0 thirst (e.g., 0.85 = 85% speed)
        float minStaminaRegenMultiplier   // Minimum stamina regen at 0 thirst (e.g., 0.85 = 85%)
    ) {
        public static ThirstDebuffs defaults() {
            return new ThirstDebuffs(
                true,
                0.0,    // Start damage at 0 thirst
                30.0,   // Stop damage when thirst reaches 30
                20.0,   // Start blur effect at 20 thirst
                4.0f,   // Damage every 4 seconds at 0
                1.5f,   // 1.5 damage per tick at 0
                30.0,   // Start speed/stamina reduction at 30 thirst
                0.85f,  // Minimum 85% speed at 0 thirst (15% reduction)
                0.85f   // Minimum 85% stamina regen at 0 thirst (15% reduction)
            );
        }
    }

    /**
     * Energy debuff configuration.
     * Low energy reduces movement speed and increases stamina consumption.
     * At 0 energy, player's stamina is drained until energy recovers.
     */
    public record EnergyDebuffs(
        boolean enabled,
        double slowStartThreshold,             // Energy level where slowdown starts (e.g., 30)
        float minSpeedMultiplier,              // Minimum speed (e.g., 0.5 = 50% speed)
        float maxStaminaMultiplier,            // Maximum stamina consumption multiplier
        double staminaDrainStartThreshold,     // Energy level at/below which stamina drain starts (e.g., 0)
        double staminaDrainRecoveryThreshold,  // Energy level needed to stop stamina drain (e.g., 50)
        float staminaDrainPerTick,             // How much stamina to drain per tick
        float staminaDrainTickIntervalSeconds  // How often to drain stamina
    ) {
        public static EnergyDebuffs defaults() {
            return new EnergyDebuffs(
                true,
                30.0,   // Start speed reduction at 30 energy
                0.6f,   // Minimum 60% speed at 0 energy
                1.5f,   // 150% stamina consumption at 0 energy
                0.0,    // Start stamina drain at 0 energy
                50.0,   // Stop stamina drain when energy reaches 50
                5.0f,   // Drain 5 stamina per tick
                1.0f    // Drain every 1 second
            );
        }
    }
}
