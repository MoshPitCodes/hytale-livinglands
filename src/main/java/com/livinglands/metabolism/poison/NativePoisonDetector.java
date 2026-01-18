package com.livinglands.metabolism.poison;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.livinglands.core.PlayerSession;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Detects native Hytale poison debuffs on players.
 *
 * When the game applies a poison effect (shown as green skull icon in UI),
 * this detector identifies it so we can apply metabolism drain.
 *
 * Supported poison effect IDs (verified from Server/Entity/Effects/Status/):
 * - Poison (base effect: 10 poison damage/5s, 16s duration)
 * - Poison_T1 (tier 1: 6 poison damage/5s, 16s duration - applied by Spiders)
 * - Poison_T2 (tier 2: 12 poison damage/5s, 16s duration)
 * - Poison_T3 (tier 3: 12 poison damage/5s, 31s duration)
 *
 * Note: Hytale has no other poison/venom/toxin effects. Only these 4 IDs exist.
 * Burn effects (Burn, Lava_Burn, Flame_Staff_Burn) deal Fire damage, not Poison.
 */
public class NativePoisonDetector {

    // All poison effect IDs from Hytale's asset definitions (comprehensive list)
    private static final Set<String> POISON_EFFECT_IDS = Set.of(
        "Poison",
        "Poison_T1",
        "Poison_T2",
        "Poison_T3"
    );

    private final HytaleLogger logger;

    public NativePoisonDetector(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Checks if a player currently has an active poison debuff from the game.
     *
     * @param session The player's session with ECS access
     * @return true if the player has a native poison effect active
     */
    public boolean hasNativePoisonDebuff(@Nonnull PlayerSession session) {
        if (!session.isEcsReady()) {
            return false;
        }

        var ref = session.getEntityRef();
        var store = session.getStore();

        if (ref == null || store == null) {
            return false;
        }

        try {
            // Get the EffectControllerComponent which manages all entity effects
            var effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (effectController == null) {
                return false;
            }

            // Check all active effects for poison
            var activeEffects = effectController.getAllActiveEntityEffects();
            if (activeEffects == null || activeEffects.length == 0) {
                return false;
            }

            for (var activeEffect : activeEffects) {
                if (isPoisonEffect(activeEffect)) {
                    return true;
                }
            }

        } catch (Exception e) {
            // ECS access can fail if entity is being removed or world is unloading
            logger.at(Level.FINE).log("Error checking poison debuff: %s", e.getMessage());
        }

        return false;
    }

    /**
     * Gets details about the active poison effect, if present.
     *
     * @param session The player's session with ECS access
     * @return Optional containing poison details, or empty if no poison active
     */
    public Optional<ActivePoisonDetails> getActivePoisonDetails(@Nonnull PlayerSession session) {
        if (!session.isEcsReady()) {
            return Optional.empty();
        }

        var ref = session.getEntityRef();
        var store = session.getStore();

        if (ref == null || store == null) {
            return Optional.empty();
        }

        try {
            var effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (effectController == null) {
                return Optional.empty();
            }

            var activeEffects = effectController.getAllActiveEntityEffects();
            if (activeEffects == null || activeEffects.length == 0) {
                return Optional.empty();
            }

            for (var activeEffect : activeEffects) {
                if (isPoisonEffect(activeEffect)) {
                    return Optional.of(new ActivePoisonDetails(
                        getEffectId(activeEffect),
                        activeEffect.getRemainingDuration(),
                        activeEffect.getInitialDuration(),
                        getPoisonTier(activeEffect)
                    ));
                }
            }

        } catch (Exception e) {
            logger.at(Level.FINE).log("Error getting poison details: %s", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Checks if an active effect is a poison effect.
     */
    private boolean isPoisonEffect(ActiveEntityEffect activeEffect) {
        if (activeEffect == null || !activeEffect.isDebuff()) {
            return false;
        }

        // Get the effect ID from the effect index
        String effectId = getEffectId(activeEffect);
        return effectId != null && POISON_EFFECT_IDS.contains(effectId);
    }

    /**
     * Gets the effect ID string from an active effect.
     * The effect index maps to an EntityEffect in the asset store.
     */
    private String getEffectId(ActiveEntityEffect activeEffect) {
        try {
            int effectIndex = activeEffect.getEntityEffectIndex();
            var assetMap = EntityEffect.getAssetMap();
            if (assetMap != null) {
                var effect = assetMap.getAsset(effectIndex);
                if (effect != null) {
                    return effect.getId();
                }
            }
        } catch (Exception e) {
            // Index lookup can fail if assets aren't loaded
        }
        return null;
    }

    /**
     * Determines the poison tier (1-3) from the effect, or 0 for base poison.
     */
    private int getPoisonTier(ActiveEntityEffect activeEffect) {
        String effectId = getEffectId(activeEffect);
        if (effectId == null) {
            return 0;
        }

        return switch (effectId) {
            case "Poison_T1" -> 1;
            case "Poison_T2" -> 2;
            case "Poison_T3" -> 3;
            default -> 0; // Base "Poison" effect
        };
    }

    /**
     * Details about an active poison effect.
     */
    public record ActivePoisonDetails(
        String effectId,
        float remainingDuration,
        float initialDuration,
        int tier
    ) {
        /**
         * Gets the progress of the poison effect (0.0 = just started, 1.0 = about to expire).
         */
        public float progress() {
            if (initialDuration <= 0) {
                return 1.0f;
            }
            return 1.0f - (remainingDuration / initialDuration);
        }
    }
}
