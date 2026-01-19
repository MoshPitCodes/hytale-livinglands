package com.livinglands.modules.metabolism.buff;

/**
 * Types of buffs that can be applied to players.
 *
 * Buffs are positive effects that enhance player abilities when:
 * - Metabolism stats are high (>= 90%)
 * - Consuming food/potions with buff effects
 */
public enum BuffType {
    /**
     * Speed buff - increased movement speed.
     * Triggered by high energy levels (>= 90%) or specific foods.
     */
    SPEED("Speed Boost", "Increases movement speed"),

    /**
     * Defense buff - increased max health (proxy for damage resistance).
     * Triggered by high hunger levels (>= 90%) or specific foods.
     */
    DEFENSE("Well Fed", "Increases max health"),

    /**
     * Stamina buff - increased max stamina and recovery.
     * Triggered by high thirst levels (>= 90%) or specific foods.
     */
    STAMINA_REGEN("Hydrated", "Increases stamina capacity"),

    /**
     * Strength buff - slower metabolism drain during combat.
     * Triggered by meat consumption.
     */
    STRENGTH("Strength", "Reduces metabolism drain"),

    /**
     * Vitality buff - faster metabolism recovery.
     * Triggered by fruit/veggie consumption.
     */
    VITALITY("Vitality", "Speeds up metabolism recovery");

    private final String displayName;
    private final String description;

    BuffType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the display name shown to players.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of what this buff does.
     */
    public String getDescription() {
        return description;
    }
}
