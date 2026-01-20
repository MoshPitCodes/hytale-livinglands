package com.livinglands.modules.metabolism.consumables;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.livinglands.core.PlayerSession;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Detects when players consume food by monitoring for food buff effects.
 *
 * When a player eats food in Hytale, an EntityEffect is applied (e.g., Meat_Buff_T2).
 * By detecting when these effects are NEWLY applied via getAllActiveEntityEffects(),
 * we can reliably detect food consumption without false positives from item drops.
 *
 * The MetabolismSystem runs this detector at 50ms intervals to catch instant heal
 * effects (Food_Instant_Heal_*) which only last 100ms (Duration: 0.1s).
 *
 * Food effect ID patterns:
 * - Food_Instant_Heal_* (instant health restoration - Duration: 0.1s)
 * - Food_Health_Boost_* (max health buff - Duration: 150s)
 * - Food_Stamina_Boost_* (max stamina buff - Duration: 150s)
 * - Food_Health_Regen_* (health regen over time)
 * - Meat_Buff_* (meat food buffs - Duration: 150s)
 * - FruitVeggie_Buff_* (fruit/vegetable buffs)
 * - HealthRegen_Buff_* (health regen buffs)
 */
public class FoodEffectDetector {

    private final HytaleLogger logger;

    // Track previously seen effect indexes per player to detect NEW effects
    // Key: player UUID, Value: set of effect indexes currently active
    private final Map<UUID, Set<Integer>> previousEffects = new ConcurrentHashMap<>();

    // Track recently processed effect indexes per player to prevent duplicate detections
    // Key: player UUID, Value: set of effect indexes already processed
    // This prevents re-detecting the same effect if it persists across multiple ticks
    // while allowing new effects (with new indexes) to be detected immediately
    private final Map<UUID, Set<Integer>> processedEffectIndexes = new ConcurrentHashMap<>();

    // Track when we last cleaned up processed effects for each player
    // Key: player UUID, Value: timestamp of last cleanup
    private final Map<UUID, Long> lastCleanupTime = new ConcurrentHashMap<>();

    // How often to clean up old processed effect indexes (milliseconds)
    // This allows the same effect index to be re-detected after this time
    // (e.g., if player drinks another potion of the same type later)
    // Set to 200ms to allow rapid consecutive potion use while still preventing
    // duplicate detection within a single consumption animation
    private static final long CLEANUP_INTERVAL_MS = 200;

    // Food effect ID prefixes to detect
    private static final Set<String> FOOD_EFFECT_PREFIXES = Set.of(
        "Food_Instant_Heal",     // Food_Instant_Heal_T1/T2/T3/Bread
        "Food_Health_Restore",   // Food_Health_Restore_Tiny/Small/Medium/Large (water/drinks)
        "Food_Stamina_Restore",  // Food_Stamina_Restore_Tiny/Small/Medium/Large (stamina drinks)
        "Food_Health_Boost",     // Food_Health_Boost_Tiny/Small/Medium/Large
        "Food_Stamina_Boost",    // Food_Stamina_Boost_Tiny/Small/Medium/Large
        "Food_Health_Regen",     // Food_Health_Regen_Tiny/Small/Medium/Large
        "Food_Stamina_Regen",    // Food_Stamina_Regen_Tiny/Small/Medium/Large
        "Meat_Buff",             // Meat_Buff_T1/T2/T3
        "FruitVeggie_Buff",      // FruitVeggie_Buff_T1/T2/T3
        "HealthRegen_Buff",      // HealthRegen_Buff_T1/T2/T3
        "Food_Buff",             // Deprecated but might still be used
        "Antidote"               // Milk bucket applies Antidote effect
    );

    // Potion effect ID prefixes to detect
    // Hytale generates effect IDs from item names + interaction paths, e.g.:
    // "Potion_Health_Large_InteractionVars_Effect_Interactions_0_EffectId"
    // "Potion_Regen_Health_Small_InteractionVars_..." (alternative naming)
    // So we match on the item name prefix pattern
    private static final Set<String> POTION_EFFECT_PREFIXES = Set.of(
        "Potion_Health",       // Health potions (Instant/Regen, Lesser/Greater)
        "Potion_Regen_Health", // Health regen potions (alternative naming)
        "Potion_Stamina",      // Stamina potions (Instant/Regen, Lesser/Greater)
        "Potion_Regen_Stamina",// Stamina regen potions (alternative naming)
        "Potion_Signature",    // Mana/Signature potions (Regen, Lesser/Greater)
        "Potion_Regen_Signature", // Signature regen potions (alternative naming)
        "Potion_Mana",         // Mana potions (alternative naming)
        "Potion_Regen_Mana",   // Mana regen potions (alternative naming)
        "Potion_Regen",        // Generic regen potions
        "Potion_Morph"         // Morph potions (Dog, Frog, Mosshorn, Mouse, Pigeon)
    );

    // Note: Water/milk items use Food_Health_Restore_* effects (covered in FOOD_EFFECT_PREFIXES)
    // No separate drink prefixes needed - detection is handled via Food_Health_Restore

    public FoodEffectDetector(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Cleans up old processed effect indexes to allow re-detection.
     * Called periodically to prevent memory buildup and allow the same
     * effect index to be detected again after the cleanup interval.
     */
    private void cleanupProcessedEffects(UUID playerId, long currentTime) {
        Long lastCleanup = lastCleanupTime.get(playerId);
        if (lastCleanup != null && currentTime - lastCleanup < CLEANUP_INTERVAL_MS) {
            return; // Not time to clean up yet
        }

        // Clear processed effects and update cleanup time
        Set<Integer> processed = processedEffectIndexes.get(playerId);
        if (processed != null) {
            processed.clear();
        }
        lastCleanupTime.put(playerId, currentTime);
    }

    /**
     * Checks for newly applied food effects on a player.
     * Returns information about any NEW food effects detected since last check.
     *
     * This method compares currently active effects with the previous tick's effects
     * to detect when a new food buff has been applied.
     *
     * @param playerId The player's UUID
     * @param session The player's session with ECS access
     * @return List of detected food consumptions (may contain multiple if several effects were applied)
     */
    public List<DetectedFoodConsumption> checkForNewFoodEffects(
            @Nonnull UUID playerId,
            @Nonnull PlayerSession session) {

        List<DetectedFoodConsumption> detectedConsumptions = new ArrayList<>();

        if (!session.isEcsReady()) {
            return detectedConsumptions;
        }

        var ref = session.getEntityRef();
        var store = session.getStore();

        if (ref == null || store == null) {
            return detectedConsumptions;
        }

        try {
            var effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (effectController == null) {
                return detectedConsumptions;
            }

            var activeEffects = effectController.getAllActiveEntityEffects();
            if (activeEffects == null) {
                previousEffects.put(playerId, Set.of());
                return detectedConsumptions;
            }

            // Get current effect indexes
            Set<Integer> currentEffectIndexes = new HashSet<>();
            for (var effect : activeEffects) {
                if (effect != null) {
                    currentEffectIndexes.add(effect.getEntityEffectIndex());
                }
            }

            // Get previous effect indexes for this player
            Set<Integer> previous = previousEffects.getOrDefault(playerId, Set.of());
            long currentTime = System.currentTimeMillis();

            // Periodically clean up processed effect indexes to allow re-detection
            // of the same effect type after the cleanup interval
            cleanupProcessedEffects(playerId, currentTime);

            // Get the set of already-processed effect indexes for this player
            Set<Integer> processed = processedEffectIndexes.computeIfAbsent(
                playerId, k -> ConcurrentHashMap.newKeySet()
            );

            // Find NEW effects (in current but not in previous)
            for (var effect : activeEffects) {
                if (effect == null) continue;

                int effectIndex = effect.getEntityEffectIndex();

                // Skip if this effect was already active in the previous tick
                // (we only want to detect NEW effects, not ongoing ones)
                if (previous.contains(effectIndex)) {
                    continue;
                }

                // Skip if we already processed this effect index recently
                // (prevents duplicate detection if effect persists across ticks)
                if (processed.contains(effectIndex)) {
                    continue;
                }

                // Get effect ID using multiple strategies (handles dynamic effects)
                String effectId = getEffectIdFromEffect(effect);

                // Debug log new effects to help diagnose detection issues
                if (effectId != null) {
                    logger.at(Level.FINE).log(
                        "New effect detected for player %s: %s (index: %d, isFood: %b, isPotion: %b)",
                        playerId, effectId, effectIndex, isFoodEffect(effectId), isPotionEffect(effectId)
                    );
                }

                if (effectId != null && (isFoodEffect(effectId) || isPotionEffect(effectId))) {
                    // Mark this effect index as processed to prevent duplicate detection
                    processed.add(effectIndex);

                    // Found a newly applied food or potion effect
                    detectedConsumptions.add(new DetectedFoodConsumption(
                        effectId,
                        determineFoodTier(effectId),
                        determineFoodType(effectId)
                    ));

                    logger.at(Level.FINE).log(
                        "Detected consumable for player %s: %s (tier: %d, type: %s)",
                        playerId, effectId, determineFoodTier(effectId), determineFoodType(effectId)
                    );
                }
            }

            // Update tracking
            previousEffects.put(playerId, currentEffectIndexes);

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error checking food effects for player %s", playerId);
        }

        return detectedConsumptions;
    }

    /**
     * Legacy method for backwards compatibility - returns first detected consumption.
     */
    public Optional<DetectedFoodConsumption> checkForNewFoodEffect(
            @Nonnull UUID playerId,
            @Nonnull PlayerSession session) {
        var consumptions = checkForNewFoodEffects(playerId, session);
        return consumptions.isEmpty() ? Optional.empty() : Optional.of(consumptions.get(0));
    }

    /**
     * Gets the effect ID string from an effect object.
     * Uses multiple strategies to handle both predefined and dynamically generated effects.
     *
     * @param effect The active effect object
     * @return The effect ID, or null if not found
     */
    private String getEffectIdFromEffect(Object effect) {
        if (effect == null) {
            return null;
        }

        // Strategy 1: Try getType().getId() pattern (works for most effects)
        try {
            var effectClass = effect.getClass();
            var getType = effectClass.getMethod("getType");
            var type = getType.invoke(effect);
            if (type != null) {
                var getId = type.getClass().getMethod("getId");
                var id = getId.invoke(type);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            // Method not available, try next strategy
        }

        // Strategy 2: Try direct getId() method
        try {
            var getId = effect.getClass().getMethod("getId");
            var id = getId.invoke(effect);
            if (id != null) {
                return id.toString();
            }
        } catch (Exception e) {
            // Method not available, try next strategy
        }

        // Strategy 3: Fall back to asset map lookup by index
        try {
            var getIndex = effect.getClass().getMethod("getEntityEffectIndex");
            var index = (Integer) getIndex.invoke(effect);
            if (index != null) {
                var assetMap = EntityEffect.getAssetMap();
                if (assetMap != null) {
                    var asset = assetMap.getAsset(index);
                    if (asset != null) {
                        return asset.getId();
                    }
                }
            }
        } catch (Exception e) {
            // Asset lookup failed
        }

        return null;
    }

    /**
     * Gets the effect ID string from an effect index (legacy method).
     * @deprecated Use getEffectIdFromEffect instead for better dynamic effect support
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    private String getEffectId(int effectIndex) {
        try {
            var assetMap = EntityEffect.getAssetMap();
            if (assetMap != null) {
                var effect = assetMap.getAsset(effectIndex);
                if (effect != null) {
                    return effect.getId();
                }
            }
        } catch (Exception e) {
            // Asset lookup can fail
        }
        return null;
    }

    /**
     * Checks if an effect ID represents a food effect.
     * Handles effect IDs that may have leading markers like "***".
     */
    private boolean isFoodEffect(String effectId) {
        if (effectId == null) {
            return false;
        }

        // Strip any leading non-letter characters (like "***" markers)
        String cleanedId = stripLeadingMarkers(effectId);

        for (String prefix : FOOD_EFFECT_PREFIXES) {
            if (cleanedId.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an effect ID represents a potion effect.
     * Handles effect IDs that may have leading markers like "***".
     */
    private boolean isPotionEffect(String effectId) {
        if (effectId == null) {
            return false;
        }

        // Strip any leading non-letter characters (like "***" markers)
        String cleanedId = stripLeadingMarkers(effectId);

        for (String prefix : POTION_EFFECT_PREFIXES) {
            if (cleanedId.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strips leading non-letter characters from an effect ID.
     * Some effect IDs have markers like "***" at the start.
     */
    private String stripLeadingMarkers(String effectId) {
        if (effectId == null) {
            return null;
        }
        int i = 0;
        while (i < effectId.length() && !Character.isLetter(effectId.charAt(i))) {
            i++;
        }
        return i > 0 ? effectId.substring(i) : effectId;
    }

    /**
     * Extracts the "base potion name" from a potion effect ID.
     *
     * This groups related effects from the same potion together.
     * For example:
     * - "Potion_Health_Greater_Regen" → "Potion_Health_Greater"
     * - "Potion_Health_Instant_Greater" → "Potion_Health_Greater"
     * - "Potion_Health_Lesser_Regen" → "Potion_Health_Lesser"
     * - "Potion_Health_Instant_Lesser" → "Potion_Health_Lesser"
     * - "***Potion_Stamina_Large_InteractionVars_..." → "Potion_Stamina_Large"
     *
     * The key insight is that potions are identified by their TYPE and QUALITY/SIZE:
     * - Type: Health, Stamina, Signature/Mana
     * - Quality/Size: Greater, Lesser, Large, Small
     *
     * We extract: Potion_{Type}_{Quality/Size}
     */
    private String extractPotionBaseName(String effectId) {
        if (effectId == null || !isPotionEffect(effectId)) {
            return null;
        }

        // Strip any leading markers like "***"
        String cleanedId = stripLeadingMarkers(effectId);

        // Quality/size identifiers that define the potion tier
        String[] qualityIdentifiers = {
            "_Greater", "_Lesser", "_Large", "_Small", "_Medium"
        };

        // Find the potion type (Health, Stamina, Signature)
        // Handles both "Potion_Health_..." and "Potion_Regen_Health_..." patterns
        String potionType = null;
        if (cleanedId.startsWith("Potion_Regen_Health") || cleanedId.startsWith("Potion_Health")) {
            potionType = "Potion_Health";
        } else if (cleanedId.startsWith("Potion_Regen_Stamina") || cleanedId.startsWith("Potion_Stamina")) {
            potionType = "Potion_Stamina";
        } else if (cleanedId.startsWith("Potion_Regen_Signature") || cleanedId.startsWith("Potion_Signature")) {
            potionType = "Potion_Signature";
        } else if (cleanedId.startsWith("Potion_Regen_Mana") || cleanedId.startsWith("Potion_Mana")) {
            potionType = "Potion_Mana";
        } else if (cleanedId.startsWith("Potion_Regen")) {
            // Generic regen potion - treat as health
            potionType = "Potion_Health";
        } else {
            // Unknown potion type, use the whole ID up to first underscore after "Potion_"
            int idx = cleanedId.indexOf('_', 7); // After "Potion_"
            if (idx > 0) {
                int nextIdx = cleanedId.indexOf('_', idx + 1);
                potionType = nextIdx > 0 ? cleanedId.substring(0, nextIdx) : cleanedId.substring(0, idx);
            } else {
                potionType = cleanedId;
            }
        }

        // Find the quality/size identifier anywhere in the effect ID
        for (String quality : qualityIdentifiers) {
            if (cleanedId.contains(quality)) {
                // Return the potion type + quality as the base name
                return potionType + quality;
            }
        }

        // No quality identifier found, just return the potion type
        return potionType;
    }

    /**
     * Determines the food tier from the effect ID (1, 2, 3, or 0 for unknown).
     * Handles patterns like:
     * - "_T1", "_T2", "_T3" (explicit tiers)
     * - "_Small", "_Medium", "_Large" (size-based, also for potions)
     * - "_Lesser", "_Greater" (quality-based)
     */
    private int determineFoodTier(String effectId) {
        if (effectId == null) {
            return 0;
        }

        // Check for tier suffixes like _T1, _T2, _T3
        if (effectId.contains("_T1")) {
            return 1;
        } else if (effectId.contains("_T2")) {
            return 2;
        } else if (effectId.contains("_T3")) {
            return 3;
        }

        // Check for size descriptors (also used by potions: Potion_Health_Small, Potion_Health_Large)
        if (effectId.contains("_Tiny") || effectId.contains("_Small")) {
            return 1;
        } else if (effectId.contains("_Medium")) {
            return 2;
        } else if (effectId.contains("_Large")) {
            return 3;
        }

        // Check for potion quality (Lesser = tier 1, Greater = tier 3)
        if (effectId.contains("_Lesser")) {
            return 1;
        } else if (effectId.contains("_Greater")) {
            return 3;
        }

        return 2; // Default to medium tier
    }

    /**
     * Determines the food type from the effect ID.
     * Handles both predefined effects (e.g., "Meat_Buff_T2") and
     * auto-generated effects (e.g., "***Potion_Health_Large_InteractionVars_...").
     */
    private FoodType determineFoodType(String effectId) {
        if (effectId == null) {
            return FoodType.GENERIC;
        }

        // Strip any leading markers like "***"
        String cleanedId = stripLeadingMarkers(effectId);

        // Check for potions first (they have priority)
        // Handles patterns like:
        // - "Potion_Health_Instant_Greater"
        // - "Potion_Health_Large_InteractionVars_..."
        // - "Potion_Regen_Health_Small_InteractionVars_..."
        if (cleanedId.startsWith("Potion_Regen_Health") || cleanedId.startsWith("Potion_Health")) {
            return FoodType.HEALTH_POTION;
        } else if (cleanedId.startsWith("Potion_Regen_Signature") || cleanedId.startsWith("Potion_Signature") ||
                   cleanedId.startsWith("Potion_Regen_Mana") || cleanedId.startsWith("Potion_Mana")) {
            return FoodType.MANA_POTION;
        } else if (cleanedId.startsWith("Potion_Regen_Stamina") || cleanedId.startsWith("Potion_Stamina")) {
            return FoodType.STAMINA_POTION;
        } else if (cleanedId.startsWith("Potion_Morph")) {
            // Morph potions - treat as mana potion for metabolism purposes
            return FoodType.MANA_POTION;
        } else if (cleanedId.startsWith("Potion_Regen")) {
            // Generic regen potion - treat as health
            return FoodType.HEALTH_POTION;
        }

        // Check for restore effects and drinks
        // Food_Health_Restore_* is used by water mugs/buckets
        if (cleanedId.startsWith("Food_Health_Restore")) {
            return FoodType.WATER;
        } else if (cleanedId.startsWith("Food_Stamina_Restore")) {
            return FoodType.STAMINA_POTION; // Stamina restore drinks
        } else if (cleanedId.equals("Antidote")) {
            return FoodType.MILK; // Milk bucket applies Antidote effect
        }

        // Then check for food types
        if (cleanedId.contains("Meat")) {
            return FoodType.MEAT;
        } else if (cleanedId.contains("FruitVeggie") || cleanedId.contains("Fruit") || cleanedId.contains("Veggie")) {
            return FoodType.FRUIT_VEGGIE;
        } else if (cleanedId.contains("Bread")) {
            return FoodType.BREAD;
        } else if (cleanedId.contains("Health_Regen") || cleanedId.contains("HealthRegen") ||
                   cleanedId.startsWith("Food_Health_Regen")) {
            return FoodType.HEALTH_REGEN;
        } else if (cleanedId.contains("Stamina_Regen") || cleanedId.startsWith("Food_Stamina_Regen")) {
            return FoodType.STAMINA_BOOST; // Stamina regen food gives stamina boost effect
        } else if (cleanedId.contains("Instant_Heal")) {
            return FoodType.INSTANT_HEAL;
        } else if (cleanedId.contains("Health_Boost")) {
            return FoodType.HEALTH_BOOST;
        } else if (cleanedId.contains("Stamina_Boost")) {
            return FoodType.STAMINA_BOOST;
        }

        return FoodType.GENERIC;
    }

    /**
     * Removes tracking for a player (on disconnect).
     */
    public void removePlayer(@Nonnull UUID playerId) {
        previousEffects.remove(playerId);
        processedEffectIndexes.remove(playerId);
        lastCleanupTime.remove(playerId);
    }

    /**
     * Information about a detected food consumption.
     */
    public record DetectedFoodConsumption(
        String effectId,
        int tier,
        FoodType foodType
    ) {}

    /**
     * Types of food based on effect patterns.
     */
    public enum FoodType {
        MEAT,           // Meat products (cooked meat, etc.)
        FRUIT_VEGGIE,   // Fruits and vegetables
        BREAD,          // Bread and baked goods
        INSTANT_HEAL,   // Instant health restore
        HEALTH_REGEN,   // Health regeneration over time
        HEALTH_BOOST,   // Max health buff
        STAMINA_BOOST,  // Max stamina buff
        HEALTH_POTION,  // Health potions (instant heal or regen)
        MANA_POTION,    // Mana/Signature potions
        STAMINA_POTION, // Stamina potions (instant restore or regen)
        WATER,          // Water (bucket, mug, etc.)
        MILK,           // Milk (bucket, mug, etc.)
        GENERIC         // Unknown food type
    }
}
