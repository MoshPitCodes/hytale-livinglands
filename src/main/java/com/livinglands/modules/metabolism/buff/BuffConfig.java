package com.livinglands.modules.metabolism.buff;

/**
 * Configuration for the buff system.
 *
 * Buffs are positive effects that enhance player abilities when:
 * - Metabolism stats are high (>= activation threshold)
 * - Consuming food/potions with buff effects
 */
public class BuffConfig {

    /**
     * Whether the buff system is enabled.
     */
    public boolean enabled = true;

    /**
     * Configuration for energy-based speed buff.
     */
    public StatBuffConfig energy = new StatBuffConfig();

    /**
     * Configuration for hunger-based defense buff (max health).
     */
    public StatBuffConfig hunger = new StatBuffConfig();

    /**
     * Configuration for thirst-based stamina buff.
     */
    public StatBuffConfig thirst = new StatBuffConfig();

    /**
     * Configuration for food-triggered buffs.
     */
    public FoodBuffConfig foodBuffs = new FoodBuffConfig();

    /**
     * How often to process buff checks (in seconds).
     */
    public double tickIntervalSeconds = 1.0;

    /**
     * Configuration for stat-level buffs (triggered by high metabolism).
     */
    public static class StatBuffConfig {
        /**
         * Whether this stat buff is enabled.
         */
        public boolean enabled = true;

        /**
         * Stat value at or above which the buff activates (0-100).
         * Default: 90 (buff when stat >= 90%)
         */
        public double activationThreshold = 90.0;

        /**
         * Stat value below which the buff deactivates (hysteresis).
         * Default: 80 (deactivate when stat < 80%)
         * Must be less than activationThreshold to prevent flickering.
         */
        public double deactivationThreshold = 80.0;

        /**
         * Multiplier applied to the relevant stat.
         * Default: 1.132 (13.2% increase, reduced by 12% from original 15%)
         */
        public float multiplier = 1.132f;

        /**
         * Creates default stat buff config.
         */
        public static StatBuffConfig defaults() {
            return new StatBuffConfig();
        }
    }

    /**
     * Configuration for food-triggered buffs.
     */
    public static class FoodBuffConfig {
        /**
         * Whether food buffs are enabled.
         */
        public boolean enabled = true;

        /**
         * Whether to slowly restore metabolism while a food buff is active.
         */
        public boolean restoreMetabolismWhileBuffed = true;

        /**
         * Amount of metabolism to restore per tick while buffed.
         */
        public double metabolismRestorePerTick = 0.5;

        /**
         * Additional multiplier for health boost effects.
         */
        public float healthBoostMultiplier = 1.0f;

        /**
         * Additional multiplier for stamina boost effects.
         */
        public float staminaBoostMultiplier = 1.0f;

        /**
         * Creates default food buff config.
         */
        public static FoodBuffConfig defaults() {
            return new FoodBuffConfig();
        }
    }

    /**
     * Creates default buff config.
     */
    public static BuffConfig defaults() {
        return new BuffConfig();
    }
}
