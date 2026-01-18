package com.livinglands.metabolism;

/**
 * Energy/Tiredness stat for Living Lands metabolism system.
 *
 * Range: 0-100
 * - 100: Energized/Well-Rested
 * - 80: Rested
 * - 60: Alert
 * - 40: Tired
 * - 20: Exhausted (debuffs apply)
 * - 0: Critical Exhaustion
 */
public final class EnergyStat {

    public static final double MAX_VALUE = 100.0;
    public static final double DEFAULT_VALUE = 100.0;
    public static final double MIN_VALUE = 0.0;
    public static final String STAT_ID = "livinglands:energy";

    private static boolean initialized = false;

    private EnergyStat() {
        // Prevent instantiation
    }

    /**
     * Initializes the energy stat type.
     * Called during plugin setup.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        // Note: Actual stat registration with Hytale API would happen here
        // For Phase 2, we track energy manually in MetabolismSystem
        initialized = true;
    }

    /**
     * Gets the stat identifier.
     */
    public static String getStatId() {
        return STAT_ID;
    }

    /**
     * Checks if energy level is in exhaustion range.
     */
    public static boolean isExhausted(double value, double threshold) {
        return value <= threshold;
    }

    /**
     * Checks if energy level is in exhaustion range with default threshold.
     */
    public static boolean isExhausted(double value) {
        return isExhausted(value, 20.0);
    }

    /**
     * Checks if energy level is depleted.
     */
    public static boolean isDepleted(double value) {
        return value <= MIN_VALUE;
    }

    /**
     * Calculates energy percentage.
     */
    public static double getPercentage(double value) {
        return (value / MAX_VALUE) * 100.0;
    }

    /**
     * Clamps an energy value to valid range.
     */
    public static double clamp(double value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    /**
     * Gets display string for energy level.
     * Uses if-else chain (no preview features).
     */
    public static String getDisplayString(double value) {
        if (value >= 90) {
            return "Energized";
        } else if (value >= 70) {
            return "Rested";
        } else if (value >= 50) {
            return "Alert";
        } else if (value >= 30) {
            return "Tired";
        } else if (value >= 20) {
            return "Fatigued";
        } else if (value > 0) {
            return "Exhausted";
        } else {
            return "Critical";
        }
    }

    /**
     * Gets the color code for energy level display.
     * Uses if-else chain (no preview features).
     */
    public static String getColorCode(double value) {
        if (value >= 70) {
            return "green";
        } else if (value >= 40) {
            return "yellow";
        } else if (value >= 20) {
            return "gold";
        } else {
            return "red";
        }
    }
}
