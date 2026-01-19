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

    // Track recently processed consumable effects to prevent duplicate detections
    // Key: player UUID, Value: timestamp when last consumable was processed
    // This prevents detecting multiple effects from the same food/potion item
    // (e.g., one apple applying Food_Instant_Heal_T1 + FruitVeggie_Buff_T1 + HealthRegen_Buff_T1)
    private final Map<UUID, Long> recentConsumables = new ConcurrentHashMap<>();

    // Time window for deduplication (milliseconds) - effects from the same consumable
    // may appear across multiple ticks, so we use a longer window
    // Food consumption animation takes ~500ms, so 500ms should cover all effects from one item
    private static final long DEDUP_WINDOW_MS = 500;

    // Food effect ID prefixes to detect
    private static final Set<String> FOOD_EFFECT_PREFIXES = Set.of(
        "Food_Instant_Heal",
        "Food_Health_Boost",
        "Food_Stamina_Boost",
        "Food_Health_Regen",
        "Meat_Buff",
        "FruitVeggie_Buff",
        "HealthRegen_Buff",
        "Food_Buff"  // Deprecated but might still be used
    );

    // Potion effect ID prefixes to detect
    // Hytale generates effect IDs from item names + interaction paths, e.g.:
    // "Potion_Health_Large_InteractionVars_Effect_Interactions_0_EffectId"
    // "Potion_Regen_Health_Small_InteractionVars_..." (alternative naming)
    // So we match on the item name prefix pattern
    private static final Set<String> POTION_EFFECT_PREFIXES = Set.of(
        "Potion_Health",       // Health potions (Small, Large, Lesser, Greater, etc.)
        "Potion_Regen_Health", // Health regen potions (alternative naming: Potion_Regen_Health_Small)
        "Potion_Stamina",      // Stamina potions
        "Potion_Regen_Stamina",// Stamina regen potions (alternative naming)
        "Potion_Signature",    // Mana/Signature potions
        "Potion_Regen_Signature", // Signature regen potions (alternative naming)
        "Potion_Mana",         // Mana potions (alternative naming)
        "Potion_Regen_Mana",   // Mana regen potions (alternative naming)
        "Potion_Regen"         // Generic regen potions
    );

    public FoodEffectDetector(@Nonnull HytaleLogger logger) {
        this.logger = logger;
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

            // Check if we recently processed a consumable for this player
            Long lastConsumableTime = recentConsumables.get(playerId);
            boolean recentlyProcessed = lastConsumableTime != null &&
                currentTime - lastConsumableTime <= DEDUP_WINDOW_MS;

            // Find NEW effects (in current but not in previous)
            // We only process the FIRST consumable effect we find per dedup window
            boolean foundConsumable = false;

            for (var effect : activeEffects) {
                if (effect == null) continue;

                int effectIndex = effect.getEntityEffectIndex();

                // Skip if we already knew about this effect
                if (previous.contains(effectIndex)) {
                    continue;
                }

                // Check if this is a food or potion effect
                String effectId = getEffectId(effectIndex);

                if (effectId != null && (isFoodEffect(effectId) || isPotionEffect(effectId))) {
                    // Skip if we recently processed a consumable (deduplication)
                    if (recentlyProcessed || foundConsumable) {
                        continue;
                    }

                    // Mark that we found and are processing a consumable
                    foundConsumable = true;
                    recentConsumables.put(playerId, currentTime);

                    // Found a newly applied food or potion effect
                    detectedConsumptions.add(new DetectedFoodConsumption(
                        effectId,
                        determineFoodTier(effectId),
                        determineFoodType(effectId)
                    ));
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
     * Gets the effect ID string from an effect index.
     */
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
        } else if (cleanedId.startsWith("Potion_Regen")) {
            // Generic regen potion - treat as health
            return FoodType.HEALTH_POTION;
        }

        // Then check for food types
        if (cleanedId.contains("Meat")) {
            return FoodType.MEAT;
        } else if (cleanedId.contains("FruitVeggie") || cleanedId.contains("Fruit") || cleanedId.contains("Veggie")) {
            return FoodType.FRUIT_VEGGIE;
        } else if (cleanedId.contains("Bread")) {
            return FoodType.BREAD;
        } else if (cleanedId.contains("Health_Regen") || cleanedId.contains("HealthRegen")) {
            return FoodType.HEALTH_REGEN;
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
        recentConsumables.remove(playerId);
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
        GENERIC         // Unknown food type
    }
}
