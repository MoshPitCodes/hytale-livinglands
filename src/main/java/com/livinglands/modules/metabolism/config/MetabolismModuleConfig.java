package com.livinglands.modules.metabolism.config;

import com.livinglands.core.config.ActivityMultipliers;
import com.livinglands.core.config.ConsumablesConfig;
import com.livinglands.core.config.DebuffConfig;
import com.livinglands.core.config.DebuffsConfig;
import com.livinglands.core.config.MetabolismConfig;
import com.livinglands.core.config.PoisonConfig;
import com.livinglands.core.config.SleepConfig;
import com.livinglands.modules.metabolism.buff.BuffConfig;

/**
 * Complete configuration for the Metabolism module.
 *
 * This consolidates all metabolism-related configs into a single
 * JSON-serializable structure for the module's config.json file.
 *
 * Structure in LivingLands/metabolism/config.json:
 * {
 *   "metabolism": { ... },
 *   "consumables": { ... },
 *   "sleep": { ... },
 *   "debuffs": { ... },
 *   "poison": { ... },
 *   "nativeDebuffs": { ... }
 * }
 */
public class MetabolismModuleConfig {

    public MetabolismSection metabolism = new MetabolismSection();
    public ConsumablesConfig consumables = ConsumablesConfig.defaultConfig();
    public SleepConfig sleep = SleepConfig.defaultConfig();
    public DebuffsConfig debuffs = DebuffsConfig.defaultConfig();
    public PoisonConfig poison = PoisonConfig.defaults();
    public DebuffConfig nativeDebuffs = DebuffConfig.defaults();
    public BuffConfig buffs = BuffConfig.defaults();

    /**
     * Creates default configuration.
     */
    public static MetabolismModuleConfig defaults() {
        return new MetabolismModuleConfig();
    }

    /**
     * Metabolism section for JSON serialization.
     * Uses simple types for Gson compatibility.
     */
    public static class MetabolismSection {
        public boolean enableHunger = true;
        public boolean enableThirst = true;
        public boolean enableEnergy = true;

        public double hungerDepletionRate = 60.0;
        public double thirstDepletionRate = 45.0;
        public double energyDepletionRate = 90.0;

        public double starvationThreshold = 20.0;
        public double dehydrationThreshold = 20.0;
        public double exhaustionThreshold = 20.0;

        public double initialHunger = 100.0;
        public double initialThirst = 100.0;
        public double initialEnergy = 100.0;

        public ActivitySection activityMultipliers = new ActivitySection();

        /**
         * Converts to MetabolismConfig record.
         */
        public MetabolismConfig toRecord() {
            return new MetabolismConfig(
                hungerDepletionRate,
                thirstDepletionRate,
                energyDepletionRate,
                starvationThreshold,
                dehydrationThreshold,
                exhaustionThreshold,
                activityMultipliers.toRecord(),
                initialHunger,
                initialThirst,
                initialEnergy,
                enableHunger,
                enableThirst,
                enableEnergy
            );
        }
    }

    /**
     * Activity multipliers section.
     */
    public static class ActivitySection {
        public double idle = 1.0;
        public double walking = 1.0;
        public double sprinting = 2.0;
        public double swimming = 1.5;
        public double combat = 1.5;
        public double jumping = 1.2;

        public ActivityMultipliers toRecord() {
            return new ActivityMultipliers(idle, walking, sprinting, swimming, combat, jumping);
        }
    }

    /**
     * Gets the metabolism config as a record.
     */
    public MetabolismConfig getMetabolismConfig() {
        return metabolism.toRecord();
    }
}
