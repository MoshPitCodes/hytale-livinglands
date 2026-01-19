package com.livinglands.modules.leveling.profession;

/**
 * Enum representing the different profession types available in the leveling system.
 * Each profession tracks its own level and XP independently.
 */
public enum ProfessionType {
    COMBAT("Combat", "Kill mobs and deal damage to earn XP"),
    MINING("Mining", "Break blocks and mine ores to earn XP"),
    BUILDING("Building", "Place blocks to earn XP"),
    LOGGING("Logging", "Chop wood and harvest trees to earn XP"),
    GATHERING("Gathering", "Pick up items and harvest resources to earn XP");

    private final String displayName;
    private final String description;

    ProfessionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get a profession type by its ID (case-insensitive).
     *
     * @param id The profession ID
     * @return The profession type, or null if not found
     */
    public static ProfessionType fromId(String id) {
        if (id == null) return null;
        for (var type : values()) {
            if (type.name().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Get the short display code for HUD display.
     * Returns first letter of the profession name.
     */
    public String getShortCode() {
        return displayName.substring(0, 1);
    }
}
