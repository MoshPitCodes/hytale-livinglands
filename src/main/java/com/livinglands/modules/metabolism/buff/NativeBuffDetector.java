package com.livinglands.modules.metabolism.buff;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.PlayerSession;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Detects active buff effects from Hytale's native effect system.
 *
 * Monitors the EffectControllerComponent for buff effects from food and potions:
 * - Food_Health_Boost_* - Max health buffs
 * - Food_Stamina_Boost_* - Max stamina buffs
 * - Meat_Buff_* - Strength from meat
 * - FruitVeggie_Buff_* - Vitality from fruits/vegetables
 */
public class NativeBuffDetector {

    private final HytaleLogger logger;

    // Map effect ID patterns to buff types
    private static final Map<String, BuffType> BUFF_EFFECT_PATTERNS = Map.ofEntries(
        // Health boost buffs
        Map.entry("Food_Health_Boost", BuffType.DEFENSE),
        Map.entry("Food_MaxHealth", BuffType.DEFENSE),
        Map.entry("Health_Boost", BuffType.DEFENSE),

        // Stamina boost buffs
        Map.entry("Food_Stamina_Boost", BuffType.STAMINA_REGEN),
        Map.entry("Food_MaxStamina", BuffType.STAMINA_REGEN),
        Map.entry("Stamina_Boost", BuffType.STAMINA_REGEN),

        // Meat buffs (strength - slower metabolism drain)
        Map.entry("Meat_Buff", BuffType.STRENGTH),
        Map.entry("Cooked_Meat", BuffType.STRENGTH),

        // Fruit/veggie buffs (vitality - faster recovery)
        Map.entry("FruitVeggie_Buff", BuffType.VITALITY),
        Map.entry("Fruit_Buff", BuffType.VITALITY),
        Map.entry("Veggie_Buff", BuffType.VITALITY),
        Map.entry("Berry_Buff", BuffType.VITALITY)
    );

    public NativeBuffDetector(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Gets all active buff effects for a player.
     *
     * @param session The player session with ECS access
     * @return List of active buff details
     */
    public List<ActiveBuffDetails> getActiveBuffs(@Nonnull PlayerSession session) {
        var buffs = new ArrayList<ActiveBuffDetails>();

        try {
            if (!session.isEcsReady()) {
                return buffs;
            }

            Ref<EntityStore> ref = session.getEntityRef();
            Store<EntityStore> store = session.getStore();

            if (ref == null || store == null || !ref.isValid()) {
                return buffs;
            }

            var effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (effectController == null) {
                return buffs;
            }

            var activeEffects = effectController.getAllActiveEntityEffects();
            if (activeEffects == null) {
                return buffs;
            }

            for (var effect : activeEffects) {
                // Skip debuffs - we only want positive effects
                if (effect.isDebuff()) {
                    continue;
                }

                String effectId = getEffectId(effect);
                if (effectId == null || effectId.isEmpty()) {
                    continue;
                }

                // Check if this effect matches any buff pattern
                BuffType buffType = matchBuffType(effectId);
                if (buffType != null) {
                    int tier = determineTier(effectId);
                    float remaining = effect.getRemainingDuration();
                    float initial = effect.getInitialDuration();

                    buffs.add(new ActiveBuffDetails(effectId, buffType, remaining, initial, tier));

                    logger.at(Level.FINE).log(
                        "Detected buff: %s (%s, tier %d, %.1fs remaining)",
                        effectId, buffType, tier, remaining
                    );
                }
            }

        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("Error detecting native buffs");
        }

        return buffs;
    }

    /**
     * Gets the effect ID from an active effect via reflection.
     * Uses multiple fallback strategies for robustness.
     */
    private String getEffectId(Object effect) {
        if (effect == null) {
            return null;
        }

        // Strategy 1: Try getType().getId() pattern
        try {
            var effectClass = effect.getClass();
            if (hasMethod(effectClass, "getType")) {
                var getType = effectClass.getMethod("getType");
                var type = getType.invoke(effect);
                if (type != null && hasMethod(type.getClass(), "getId")) {
                    var getId = type.getClass().getMethod("getId");
                    var id = getId.invoke(type);
                    if (id != null) {
                        return id.toString();
                    }
                }
            }
        } catch (Exception e) {
            logger.at(Level.FINEST).withCause(e).log("Failed to get effect ID via getType().getId()");
        }

        // Strategy 2: Try direct getId() method
        try {
            if (hasMethod(effect.getClass(), "getId")) {
                var getId = effect.getClass().getMethod("getId");
                var id = getId.invoke(effect);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            logger.at(Level.FINEST).withCause(e).log("Failed to get effect ID via direct getId()");
        }

        // Strategy 3: Fall back to toString
        try {
            return effect.toString();
        } catch (Exception e) {
            logger.at(Level.FINEST).withCause(e).log("Failed to get effect ID via toString");
        }

        return null;
    }

    /**
     * Checks if a class has a method with the given name (no parameters).
     */
    private boolean hasMethod(Class<?> clazz, String methodName) {
        try {
            clazz.getMethod(methodName);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Match an effect ID to a buff type.
     */
    private BuffType matchBuffType(String effectId) {
        String normalizedId = effectId.toLowerCase();

        for (var entry : BUFF_EFFECT_PATTERNS.entrySet()) {
            if (normalizedId.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Determine the tier of an effect based on its ID.
     */
    private int determineTier(String effectId) {
        String id = effectId.toLowerCase();

        // Check for explicit tier markers
        if (id.contains("_t3") || id.contains("tier3") || id.contains("_large") || id.contains("_greater")) {
            return 3;
        }
        if (id.contains("_t2") || id.contains("tier2") || id.contains("_medium")) {
            return 2;
        }
        if (id.contains("_t1") || id.contains("tier1") || id.contains("_small") || id.contains("_lesser")) {
            return 1;
        }

        // Default to tier 2 if no marker found
        return 2;
    }
}
