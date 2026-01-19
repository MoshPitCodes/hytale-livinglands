package com.livinglands.modules.metabolism.consumables;

/**
 * Types of consumable items.
 */
public enum ConsumableType {
    /**
     * Food items restore hunger.
     */
    FOOD,

    /**
     * Drink items restore thirst.
     */
    DRINK,

    /**
     * Energy items restore energy only.
     */
    ENERGY,

    /**
     * Combined items restore multiple stats (any combination).
     */
    COMBINED,

    /**
     * Poisonous items drain metabolism stats when consumed.
     */
    POISON
}
