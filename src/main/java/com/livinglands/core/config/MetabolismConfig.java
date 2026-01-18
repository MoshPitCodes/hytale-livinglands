package com.livinglands.core.config;

/**
 * Metabolism system configuration.
 * Uses Java 25 record for immutable config data.
 */
public record MetabolismConfig(
    // Depletion rates (seconds per 1 point depletion)
    double hungerDepletionRate,
    double thirstDepletionRate,
    double energyDepletionRate,

    // Critical thresholds (percentage of max)
    double starvationThreshold,
    double dehydrationThreshold,
    double exhaustionThreshold,

    // Activity multipliers (applied to depletion rate)
    ActivityMultipliers activityMultipliers,

    // Initial values for new players
    double initialHunger,
    double initialThirst,
    double initialEnergy,

    // Enable/disable features
    boolean enableHunger,
    boolean enableThirst,
    boolean enableEnergy
) {
    /**
     * Default constructor with default values.
     */
    public MetabolismConfig() {
        this(
            60.0,  // hungerDepletionRate
            45.0,  // thirstDepletionRate
            90.0,  // energyDepletionRate (depletes slower than hunger/thirst)
            20.0,  // starvationThreshold
            20.0,  // dehydrationThreshold
            20.0,  // exhaustionThreshold
            new ActivityMultipliers(),
            100.0, // initialHunger
            100.0, // initialThirst
            100.0, // initialEnergy
            true,  // enableHunger
            true,  // enableThirst
            true   // enableEnergy
        );
    }
}
