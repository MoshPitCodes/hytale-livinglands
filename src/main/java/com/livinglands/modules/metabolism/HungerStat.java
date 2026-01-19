package com.livinglands.modules.metabolism;

import com.livinglands.util.ColorUtil;

/**
 * Hunger stat for Living Lands metabolism system.
 *
 * Range: 0-100
 * - 100: Full/Satiated
 * - 50: Moderately Hungry
 * - 20: Starving (debuffs apply)
 * - 0: Critical Starvation
 */
public final class HungerStat {

    public static final double MAX_VALUE = 100.0;
    public static final double DEFAULT_VALUE = 100.0;
    public static final double MIN_VALUE = 0.0;
    public static final String STAT_ID = "livinglands:hunger";

    private static boolean initialized = false;

    private HungerStat() {
        // Prevent instantiation
    }

    /**
     * Initializes the hunger stat type.
     * Called during plugin setup.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        // Note: Actual stat registration with Hytale API would happen here
        // For Phase 1, we track hunger manually in MetabolismSystem
        initialized = true;
    }

    /**
     * Gets the stat identifier.
     */
    public static String getStatId() {
        return STAT_ID;
    }

    /**
     * Checks if hunger level is in critical range (starvation).
     */
    public static boolean isStarving(double value, double threshold) {
        return value <= threshold;
    }

    /**
     * Checks if hunger level is in critical range (starvation) with default threshold.
     */
    public static boolean isStarving(double value) {
        return isStarving(value, 20.0);
    }

    /**
     * Checks if hunger level is depleted.
     */
    public static boolean isDepleted(double value) {
        return value <= MIN_VALUE;
    }

    /**
     * Calculates hunger percentage.
     */
    public static double getPercentage(double value) {
        return (value / MAX_VALUE) * 100.0;
    }

    /**
     * Clamps a hunger value to valid range.
     */
    public static double clamp(double value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    /**
     * Gets display string for hunger level.
     */
    public static String getDisplayString(double value) {
        if (value >= 90) return "Satiated";
        if (value >= 70) return "Well Fed";
        if (value >= 50) return "Peckish";
        if (value >= 30) return "Hungry";
        if (value >= 20) return "Very Hungry";
        if (value > 0) return "Starving";
        return "Critical";
    }

    /**
     * Gets the color code (hex format) for hunger level display.
     * Returns a hexadecimal color code compatible with Hytale's Message API.
     */
    public static String getColorCode(double value) {
        if (value >= 70) return ColorUtil.getHexColor("green");
        if (value >= 40) return ColorUtil.getHexColor("yellow");
        if (value >= 20) return ColorUtil.getHexColor("gold");
        return ColorUtil.getHexColor("red");
    }
}
