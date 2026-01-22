package com.livinglands.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for converting semantic color names to hexadecimal color codes.
 *
 * The Hytale Message API requires colors to be in one of these formats:
 * - Hex: #RGB or #RRGGBB (e.g., #0F0 or #00FF00)
 * - RGB: rgb(255, 128, 0)
 * - RGBA: rgba(255, 128, 0, 0.5)
 *
 * Color names like "green", "red", etc. are NOT supported and will display as white.
 * This utility provides a convenient way to use semantic names while generating
 * the correct hex format for the Hytale protocol.
 */
public final class ColorUtil {

    // Standard color definitions (hexadecimal format)
    private static final Map<String, String> COLOR_MAP = new HashMap<>();

    static {
        // Basic colors
        COLOR_MAP.put("black", "#000000");
        COLOR_MAP.put("red", "#FF0000");
        COLOR_MAP.put("green", "#00FF00");
        COLOR_MAP.put("blue", "#0000FF");
        COLOR_MAP.put("yellow", "#FFFF00");
        COLOR_MAP.put("cyan", "#00FFFF");
        COLOR_MAP.put("aqua", "#00FFFF");  // Alias for cyan
        COLOR_MAP.put("magenta", "#FF00FF");
        COLOR_MAP.put("white", "#FFFFFF");

        // Grayscale variants
        COLOR_MAP.put("gray", "#808080");
        COLOR_MAP.put("grey", "#808080");  // Alternative spelling
        COLOR_MAP.put("dark_gray", "#404040");
        COLOR_MAP.put("dark_grey", "#404040");  // Alternative spelling
        COLOR_MAP.put("light_gray", "#C0C0C0");
        COLOR_MAP.put("light_grey", "#C0C0C0");  // Alternative spelling

        // Extended colors
        COLOR_MAP.put("orange", "#FFA500");
        COLOR_MAP.put("gold", "#FFD700");
        COLOR_MAP.put("pink", "#FFC0CB");
        COLOR_MAP.put("purple", "#800080");
        COLOR_MAP.put("lime", "#00FF00");
        COLOR_MAP.put("navy", "#000080");
        COLOR_MAP.put("teal", "#008080");
        COLOR_MAP.put("olive", "#808000");
        COLOR_MAP.put("brown", "#A52A2A");
        COLOR_MAP.put("maroon", "#800000");
        COLOR_MAP.put("khaki", "#F0E68C");
        COLOR_MAP.put("salmon", "#FA8072");
        COLOR_MAP.put("coral", "#FF7F50");
        COLOR_MAP.put("turquoise", "#40E0D0");

        // Minecraft-style colors (if any)
        COLOR_MAP.put("dark_red", "#8B0000");
        COLOR_MAP.put("dark_blue", "#00008B");
        COLOR_MAP.put("dark_green", "#006400");

        // Additional safe web colors
        COLOR_MAP.put("indigo", "#4B0082");
        COLOR_MAP.put("violet", "#EE82EE");
        COLOR_MAP.put("crimson", "#DC143C");
        COLOR_MAP.put("scarlet", "#FF2400");

        // UI-specific colors (for panels, grids, buttons)
        COLOR_MAP.put("ui_background", "#0a1628");
        COLOR_MAP.put("ui_header", "#152238");
        COLOR_MAP.put("ui_cell", "#3a3a3a");
        COLOR_MAP.put("ui_cell_hover", "#4a4a4a");
        COLOR_MAP.put("ui_cell_pressed", "#2a2a2a");
        COLOR_MAP.put("ui_tab_active", "#2a4a6a");
        COLOR_MAP.put("ui_tab_inactive", "#1a2a3a");
        COLOR_MAP.put("ui_text_primary", "#ccd8e8");
        COLOR_MAP.put("ui_text_secondary", "#8899aa");
        COLOR_MAP.put("ui_text_muted", "#6e7681");

        // Claims module colors
        COLOR_MAP.put("claim_unclaimed", "#3a3a3a");
        COLOR_MAP.put("claim_selected", "#4a4a1a");
        COLOR_MAP.put("claim_own", "#4a1a4a");
        COLOR_MAP.put("claim_other", "#4a1a1a");

        // Ability/status colors
        COLOR_MAP.put("ability_unlocked", "#3fb950");
        COLOR_MAP.put("ability_locked", "#484f58");

        // Notification colors
        COLOR_MAP.put("notif_success", "#3FB950");
        COLOR_MAP.put("notif_warning", "#D29922");
        COLOR_MAP.put("notif_error", "#F85149");
        COLOR_MAP.put("notif_info", "#58A6FF");
        COLOR_MAP.put("notif_unlock", "#A371F7");
        COLOR_MAP.put("notif_subtitle", "#8B949E");
    }

    private ColorUtil() {
        // Prevent instantiation
    }

    /**
     * Converts a semantic color name to its hexadecimal representation.
     *
     * @param colorName the semantic color name (e.g., "green", "dark_gray")
     * @return the hexadecimal color code (e.g., "#00FF00")
     * @throws IllegalArgumentException if the color name is not recognized
     */
    public static String getHexColor(String colorName) {
        if (colorName == null || colorName.isEmpty()) {
            throw new IllegalArgumentException("Color name cannot be null or empty");
        }

        // Normalize to lowercase for case-insensitive lookup
        String normalizedName = colorName.toLowerCase();
        String hexColor = COLOR_MAP.get(normalizedName);

        if (hexColor == null) {
            throw new IllegalArgumentException(
                "Unknown color name: '" + colorName + "'. " +
                "Supported colors: " + COLOR_MAP.keySet()
            );
        }

        return hexColor;
    }

    /**
     * Converts a semantic color name to its hexadecimal representation.
     * Returns a default color if the name is not recognized.
     *
     * @param colorName the semantic color name (e.g., "green", "dark_gray")
     * @param defaultColor the default hex color to return if colorName is not found (e.g., "#FFFFFF")
     * @return the hexadecimal color code
     */
    public static String getHexColor(String colorName, String defaultColor) {
        if (colorName == null || colorName.isEmpty()) {
            return defaultColor;
        }

        String normalizedName = colorName.toLowerCase();
        return COLOR_MAP.getOrDefault(normalizedName, defaultColor);
    }

    /**
     * Checks if a color name is recognized.
     *
     * @param colorName the semantic color name to check
     * @return true if the color is recognized, false otherwise
     */
    public static boolean isColorSupported(String colorName) {
        return colorName != null && COLOR_MAP.containsKey(colorName.toLowerCase());
    }

    /**
     * Gets all supported color names.
     *
     * @return a set of all supported color names
     */
    public static java.util.Set<String> getSupportedColors() {
        return COLOR_MAP.keySet();
    }

    /**
     * Converts a java.awt.Color to hexadecimal format.
     * This is useful for dynamic color calculations.
     *
     * @param color the AWT Color to convert
     * @return the hexadecimal color code (e.g., "#FF0000")
     */
    public static String colorToHex(java.awt.Color color) {
        if (color == null) {
            throw new IllegalArgumentException("Color cannot be null");
        }
        return String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }

    /**
     * Parses RGB components into a hexadecimal color code.
     *
     * @param red the red component (0-255)
     * @param green the green component (0-255)
     * @param blue the blue component (0-255)
     * @return the hexadecimal color code (e.g., "#FF0000")
     * @throws IllegalArgumentException if any component is outside the valid range
     */
    public static String rgbToHex(int red, int green, int blue) {
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
            throw new IllegalArgumentException(
                "RGB components must be between 0 and 255. Got: R=" + red + ", G=" + green + ", B=" + blue
            );
        }
        return String.format("#%02X%02X%02X", red, green, blue);
    }

    /**
     * Parses RGB components into a hexadecimal color code with alpha channel.
     *
     * @param red the red component (0-255)
     * @param green the green component (0-255)
     * @param blue the blue component (0-255)
     * @param alpha the alpha (transparency) value (0.0-1.0)
     * @return the RGBA color code in Hytale format (e.g., "rgba(#FF0000, 0.5)")
     * @throws IllegalArgumentException if RGB components are invalid or alpha is outside 0.0-1.0
     */
    public static String rgbaToString(int red, int green, int blue, double alpha) {
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
            throw new IllegalArgumentException(
                "RGB components must be between 0 and 255. Got: R=" + red + ", G=" + green + ", B=" + blue
            );
        }
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException(
                "Alpha must be between 0.0 and 1.0. Got: " + alpha
            );
        }
        String hex = String.format("#%02X%02X%02X", red, green, blue);
        return String.format("rgba(%s, %.2f)", hex, alpha);
    }
}
