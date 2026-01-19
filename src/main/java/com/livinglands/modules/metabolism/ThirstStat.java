package com.livinglands.modules.metabolism;

/**
 * Thirst stat for Living Lands metabolism system.
 *
 * Range: 0-100
 * - 100: Fully Hydrated
 * - 50: Moderately Thirsty
 * - 20: Dehydrated (debuffs apply)
 * - 0: Critical Dehydration
 */
public final class ThirstStat {

    public static final double MAX_VALUE = 100.0;
    public static final double DEFAULT_VALUE = 100.0;
    public static final double MIN_VALUE = 0.0;
    public static final String STAT_ID = "livinglands:thirst";

    private static boolean initialized = false;

    private ThirstStat() {
        // Prevent instantiation
    }

    /**
     * Initializes the thirst stat type.
     * Called during plugin setup.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        // Note: Actual stat registration with Hytale API would happen here
        // For Phase 1, we track thirst manually in MetabolismSystem
        initialized = true;
    }

    /**
     * Gets the stat identifier.
     */
    public static String getStatId() {
        return STAT_ID;
    }

    /**
     * Checks if thirst level is in critical range (dehydration).
     */
    public static boolean isDehydrated(double value, double threshold) {
        return value <= threshold;
    }

    /**
     * Checks if thirst level is in critical range (dehydration) with default threshold.
     */
    public static boolean isDehydrated(double value) {
        return isDehydrated(value, 20.0);
    }

    /**
     * Checks if thirst level is depleted.
     */
    public static boolean isDepleted(double value) {
        return value <= MIN_VALUE;
    }

    /**
     * Calculates thirst percentage.
     */
    public static double getPercentage(double value) {
        return (value / MAX_VALUE) * 100.0;
    }

    /**
     * Clamps a thirst value to valid range.
     */
    public static double clamp(double value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    /**
     * Gets display string for thirst level.
     */
    public static String getDisplayString(double value) {
        if (value >= 90) return "Hydrated";
        if (value >= 70) return "Quenched";
        if (value >= 50) return "Slightly Thirsty";
        if (value >= 30) return "Thirsty";
        if (value >= 20) return "Very Thirsty";
        if (value > 0) return "Dehydrated";
        return "Critical";
    }

    /**
     * Gets the color code for thirst level display.
     */
    public static String getColorCode(double value) {
        if (value >= 70) return "aqua";
        if (value >= 40) return "blue";
        if (value >= 20) return "gold";
        return "red";
    }
}
