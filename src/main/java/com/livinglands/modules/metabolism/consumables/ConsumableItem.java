package com.livinglands.modules.metabolism.consumables;

/**
 * Defines restoration values for a consumable item.
 *
 * @param itemId The Hytale item ID (e.g., "hytale:bread")
 * @param type The type of consumable (FOOD, DRINK, COMBINED)
 * @param hungerRestore Amount of hunger restored (0-100)
 * @param thirstRestore Amount of thirst restored (0-100)
 * @param energyRestore Amount of energy restored (0-100)
 */
public record ConsumableItem(
    String itemId,
    ConsumableType type,
    double hungerRestore,
    double thirstRestore,
    double energyRestore
) {
    /**
     * Creates a food item that only restores hunger.
     */
    public static ConsumableItem food(String itemId, double hungerRestore) {
        return new ConsumableItem(itemId, ConsumableType.FOOD, hungerRestore, 0, 0);
    }

    /**
     * Creates a drink item that only restores thirst.
     */
    public static ConsumableItem drink(String itemId, double thirstRestore) {
        return new ConsumableItem(itemId, ConsumableType.DRINK, 0, thirstRestore, 0);
    }

    /**
     * Creates a combined item that restores both hunger and thirst.
     */
    public static ConsumableItem combined(String itemId, double hungerRestore, double thirstRestore) {
        return new ConsumableItem(itemId, ConsumableType.COMBINED, hungerRestore, thirstRestore, 0);
    }

    /**
     * Creates an item with full custom restoration values.
     */
    public static ConsumableItem custom(String itemId, ConsumableType type,
            double hungerRestore, double thirstRestore, double energyRestore) {
        return new ConsumableItem(itemId, type, hungerRestore, thirstRestore, energyRestore);
    }

    /**
     * Checks if this item restores hunger.
     */
    public boolean restoresHunger() {
        return hungerRestore > 0;
    }

    /**
     * Checks if this item restores thirst.
     */
    public boolean restoresThirst() {
        return thirstRestore > 0;
    }

    /**
     * Checks if this item restores energy.
     */
    public boolean restoresEnergy() {
        return energyRestore > 0;
    }
}
