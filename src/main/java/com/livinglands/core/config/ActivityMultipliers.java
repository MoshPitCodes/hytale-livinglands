package com.livinglands.core.config;

/**
 * Activity-based multipliers for stat depletion.
 * Uses Java 25 record for immutable config data.
 */
public record ActivityMultipliers(
    double idle,
    double walking,
    double sprinting,
    double swimming,
    double combat,
    double jumping
) {
    /**
     * Default constructor with default values.
     */
    public ActivityMultipliers() {
        this(
            1.0,  // idle
            1.0,  // walking
            2.0,  // sprinting
            1.5,  // swimming
            1.5,  // combat
            1.2   // jumping
        );
    }
}
