package com.livinglands.modules.metabolism.consumables;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.config.ConsumablesConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Registry of all consumable items and their restoration values.
 *
 * This registry maps Hytale item IDs to their hunger/thirst/energy restoration values.
 * Items not in this registry will use the vanilla consumable behavior without
 * affecting Living Lands metabolism stats.
 *
 * Configuration is loaded from the mod's config file, allowing server admins
 * to customize consumable items and add modded items.
 *
 * Note: Hytale item IDs do NOT use namespace prefixes. They are the filename
 * without the .json extension (e.g., "Food_Bread" not "hytale:bread").
 */
public final class ConsumableRegistry {

    private static final Map<String, ConsumableItem> CONSUMABLES = new HashMap<>();
    private static boolean initialized = false;
    private static HytaleLogger logger;

    private ConsumableRegistry() {
        // Utility class
    }

    /**
     * Initializes the registry from configuration.
     * Must be called during plugin setup before any consumable lookups.
     *
     * @param config The consumables configuration
     * @param log Logger for warnings and errors
     */
    public static void initialize(ConsumablesConfig config, HytaleLogger log) {
        CONSUMABLES.clear();
        logger = log;

        // Register foods (hunger only)
        registerFoods(config.foods());

        // Register drinks (thirst only)
        registerDrinks(config.drinks());

        // Register energy items (energy only)
        registerEnergyItems(config.energy());

        // Register combined items (multiple stats)
        registerCombinedItems(config.combined());

        initialized = true;
    }

    /**
     * Registers food items from config section.
     */
    private static void registerFoods(ConsumablesConfig.ConsumableSection section) {
        // Vanilla foods
        for (var entry : section.vanilla()) {
            registerFood(entry.itemId(), entry.restoreAmount());
        }
        // Modded foods
        for (var entry : section.modded()) {
            registerFood(entry.itemId(), entry.restoreAmount());
        }
    }

    /**
     * Registers drink items from config section.
     */
    private static void registerDrinks(ConsumablesConfig.ConsumableSection section) {
        // Vanilla drinks
        for (var entry : section.vanilla()) {
            registerDrink(entry.itemId(), entry.restoreAmount());
        }
        // Modded drinks
        for (var entry : section.modded()) {
            registerDrink(entry.itemId(), entry.restoreAmount());
        }
    }

    /**
     * Registers energy items from config section.
     */
    private static void registerEnergyItems(ConsumablesConfig.ConsumableSection section) {
        // Vanilla energy items
        for (var entry : section.vanilla()) {
            registerEnergy(entry.itemId(), entry.restoreAmount());
        }
        // Modded energy items
        for (var entry : section.modded()) {
            registerEnergy(entry.itemId(), entry.restoreAmount());
        }
    }

    /**
     * Registers combined items from config section.
     */
    private static void registerCombinedItems(ConsumablesConfig.CombinedConsumableSection section) {
        // Vanilla combined
        for (var entry : section.vanilla()) {
            registerCombined(entry.itemId(), entry.hunger(), entry.thirst(), entry.energy());
        }
        // Modded combined
        for (var entry : section.modded()) {
            registerCombined(entry.itemId(), entry.hunger(), entry.thirst(), entry.energy());
        }
    }

    /**
     * Registers a food item that restores only hunger.
     */
    private static void registerFood(String itemId, double hungerRestore) {
        if (!validateEntry(itemId, "food")) {
            return;
        }
        if (hungerRestore <= 0) {
            logWarning("Food item '%s' has invalid restore amount: %.1f (must be > 0)", itemId, hungerRestore);
            return;
        }
        CONSUMABLES.put(normalizeItemId(itemId), ConsumableItem.food(itemId, hungerRestore));
    }

    /**
     * Registers a drink item that restores only thirst.
     */
    private static void registerDrink(String itemId, double thirstRestore) {
        if (!validateEntry(itemId, "drink")) {
            return;
        }
        if (thirstRestore <= 0) {
            logWarning("Drink item '%s' has invalid restore amount: %.1f (must be > 0)", itemId, thirstRestore);
            return;
        }
        CONSUMABLES.put(normalizeItemId(itemId), ConsumableItem.drink(itemId, thirstRestore));
    }

    /**
     * Registers an energy item that restores only energy.
     */
    private static void registerEnergy(String itemId, double energyRestore) {
        if (!validateEntry(itemId, "energy")) {
            return;
        }
        if (energyRestore <= 0) {
            logWarning("Energy item '%s' has invalid restore amount: %.1f (must be > 0)", itemId, energyRestore);
            return;
        }
        CONSUMABLES.put(normalizeItemId(itemId),
            ConsumableItem.custom(itemId, ConsumableType.ENERGY, 0, 0, energyRestore));
    }

    /**
     * Registers a combined item with all restoration values.
     */
    private static void registerCombined(String itemId, double hungerRestore, double thirstRestore, double energyRestore) {
        if (!validateEntry(itemId, "combined")) {
            return;
        }
        if (hungerRestore <= 0 && thirstRestore <= 0 && energyRestore <= 0) {
            logWarning("Combined item '%s' has no positive restore values", itemId);
            return;
        }
        CONSUMABLES.put(normalizeItemId(itemId),
            ConsumableItem.custom(itemId, ConsumableType.COMBINED, hungerRestore, thirstRestore, energyRestore));
    }

    /**
     * Validates a consumable entry.
     * @return true if valid, false if invalid (with warning logged)
     */
    private static boolean validateEntry(String itemId, String type) {
        if (itemId == null || itemId.isBlank()) {
            logWarning("Invalid %s entry: itemId is null or empty", type);
            return false;
        }
        return true;
    }

    /**
     * Logs a warning message if logger is available.
     */
    private static void logWarning(String format, Object... args) {
        if (logger != null) {
            logger.at(Level.WARNING).log("[ConsumableRegistry] " + format, args);
        }
    }

    /**
     * Gets the consumable data for an item by its ID.
     *
     * @param itemId The item ID (case-insensitive)
     * @return Optional containing consumable data if found
     */
    public static Optional<ConsumableItem> getConsumable(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CONSUMABLES.get(normalizeItemId(itemId)));
    }

    /**
     * Checks if an item is registered as a consumable.
     *
     * @param itemId The item ID
     * @return true if the item is a registered consumable
     */
    public static boolean isRegistered(String itemId) {
        return itemId != null && CONSUMABLES.containsKey(normalizeItemId(itemId));
    }

    /**
     * Normalizes an item ID by converting to lowercase.
     * Hytale item IDs don't use namespace prefixes.
     */
    private static String normalizeItemId(String itemId) {
        if (itemId == null) {
            return "";
        }
        return itemId.toLowerCase();
    }

    /**
     * Gets the total number of registered consumables.
     */
    public static int getRegisteredCount() {
        return CONSUMABLES.size();
    }

    /**
     * Checks if the registry has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
