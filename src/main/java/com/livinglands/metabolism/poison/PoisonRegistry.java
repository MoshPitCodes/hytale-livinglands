package com.livinglands.metabolism.poison;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.config.PoisonConfig;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Registry for poisonous items.
 * Provides lookup to check if an item is poisonous and get its poison effect config.
 */
public class PoisonRegistry {

    private static final Map<String, PoisonItem> POISON_ITEMS = new ConcurrentHashMap<>();
    private static HytaleLogger logger;
    private static boolean initialized = false;

    private PoisonRegistry() {}

    /**
     * Initializes the poison registry from configuration.
     *
     * @param config The poison configuration
     * @param log The logger for debug output
     */
    public static void initialize(@Nonnull PoisonConfig config, @Nonnull HytaleLogger log) {
        POISON_ITEMS.clear();
        logger = log;

        if (!config.enabled()) {
            logger.at(Level.INFO).log("Poison system is disabled");
            initialized = true;
            return;
        }

        // Register vanilla poison items
        for (var entry : config.items().vanilla()) {
            registerPoisonItem(entry);
        }

        // Register modded poison items
        for (var entry : config.items().modded()) {
            registerPoisonItem(entry);
        }

        initialized = true;
        logger.at(Level.INFO).log("Registered %d poisonous items", POISON_ITEMS.size());
    }

    /**
     * Registers a poison item from configuration.
     */
    private static void registerPoisonItem(PoisonConfig.PoisonItemEntry entry) {
        if (entry.itemId() == null || entry.itemId().isEmpty()) {
            return;
        }

        var item = new PoisonItem(
            entry.itemId(),
            entry.effectType(),
            entry.hungerRestore(),
            entry.thirstRestore()
        );

        var normalizedId = normalizeItemId(entry.itemId());
        POISON_ITEMS.put(normalizedId, item);

        logger.at(Level.FINE).log("Registered poison item: %s (%s, hunger: %.1f, thirst: %.1f)",
            normalizedId, entry.effectType(), entry.hungerRestore(), entry.thirstRestore());
    }

    /**
     * Checks if an item is poisonous.
     *
     * @param itemId The item identifier
     * @return true if the item is registered as poisonous
     */
    public static boolean isPoisonous(@Nonnull String itemId) {
        return POISON_ITEMS.containsKey(normalizeItemId(itemId));
    }

    /**
     * Gets the poison item configuration if the item is poisonous.
     *
     * @param itemId The item identifier
     * @return Optional containing the poison item config, or empty if not poisonous
     */
    public static Optional<PoisonItem> getPoisonItem(@Nonnull String itemId) {
        return Optional.ofNullable(POISON_ITEMS.get(normalizeItemId(itemId)));
    }

    /**
     * Gets the number of registered poison items.
     */
    public static int getRegisteredCount() {
        return POISON_ITEMS.size();
    }

    /**
     * Checks if the registry is initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Normalizes an item ID for consistent lookup.
     * Removes common prefixes and converts to lowercase.
     */
    private static String normalizeItemId(String itemId) {
        if (itemId == null) {
            return "";
        }

        var normalized = itemId.toLowerCase();

        // Remove common prefixes
        if (normalized.startsWith("hytale:")) {
            normalized = normalized.substring(7);
        }

        return normalized;
    }
}
