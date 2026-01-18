package com.livinglands.metabolism.debuff;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.livinglands.core.PlayerSession;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;

/**
 * Detects all native Hytale debuff effects on players.
 *
 * Identifies and categorizes debuff effects into types:
 * - POISON: Poison, Poison_T1, Poison_T2, Poison_T3
 * - BURN: Burn, Lava_Burn, Flame_Staff_Burn
 * - STUN: Stun, Bomb_Explode_Stun
 * - FREEZE: Freeze
 * - ROOT: Root
 * - SLOW: Slow, Two_Handed_Bow_Ability2_Slow
 *
 * Thread Safety: All methods require ECS access, which must be called
 * from within world.execute() on the WorldThread.
 */
public class NativeDebuffDetector {

    // Mapping of effect IDs to their debuff types
    private static final Map<String, DebuffType> DEBUFF_EFFECT_MAP = Map.ofEntries(
        // Poison effects
        Map.entry("Poison", DebuffType.POISON),
        Map.entry("Poison_T1", DebuffType.POISON),
        Map.entry("Poison_T2", DebuffType.POISON),
        Map.entry("Poison_T3", DebuffType.POISON),

        // Burn/fire effects
        Map.entry("Burn", DebuffType.BURN),
        Map.entry("Lava_Burn", DebuffType.BURN),
        Map.entry("Flame_Staff_Burn", DebuffType.BURN),

        // Stun effects
        Map.entry("Stun", DebuffType.STUN),
        Map.entry("Bomb_Explode_Stun", DebuffType.STUN),

        // Freeze effects
        Map.entry("Freeze", DebuffType.FREEZE),

        // Root effects
        Map.entry("Root", DebuffType.ROOT),

        // Slow effects
        Map.entry("Slow", DebuffType.SLOW),
        Map.entry("Two_Handed_Bow_Ability2_Slow", DebuffType.SLOW)
    );

    // Poison tier mapping for backwards compatibility
    private static final Map<String, Integer> POISON_TIERS = Map.of(
        "Poison_T1", 1,
        "Poison_T2", 2,
        "Poison_T3", 3
    );

    private final HytaleLogger logger;

    public NativeDebuffDetector(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Checks if a player currently has any active debuff.
     *
     * @param session The player's session with ECS access
     * @return true if the player has any tracked debuff active
     */
    public boolean hasAnyDebuff(@Nonnull PlayerSession session) {
        return !getActiveDebuffs(session).isEmpty();
    }

    /**
     * Checks if a player has a specific debuff type active.
     *
     * @param session The player's session with ECS access
     * @param debuffType The type of debuff to check for
     * @return true if the player has this debuff type active
     */
    public boolean hasDebuffType(@Nonnull PlayerSession session, @Nonnull DebuffType debuffType) {
        return getActiveDebuffs(session).stream()
            .anyMatch(d -> d.debuffType() == debuffType);
    }

    /**
     * Checks if a player has a native poison debuff (backwards compatibility).
     *
     * @param session The player's session with ECS access
     * @return true if the player has a poison debuff active
     */
    public boolean hasNativePoisonDebuff(@Nonnull PlayerSession session) {
        return hasDebuffType(session, DebuffType.POISON);
    }

    /**
     * Gets all active debuffs on the player.
     *
     * @param session The player's session with ECS access
     * @return List of active debuff details (empty if none)
     */
    @Nonnull
    public List<ActiveDebuffDetails> getActiveDebuffs(@Nonnull PlayerSession session) {
        if (!session.isEcsReady()) {
            return List.of();
        }

        var ref = session.getEntityRef();
        var store = session.getStore();

        if (ref == null || store == null) {
            return List.of();
        }

        try {
            var effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (effectController == null) {
                return List.of();
            }

            var activeEffects = effectController.getAllActiveEntityEffects();
            if (activeEffects == null || activeEffects.length == 0) {
                return List.of();
            }

            var debuffs = new ArrayList<ActiveDebuffDetails>();
            for (var activeEffect : activeEffects) {
                var debuffOpt = createDebuffDetails(activeEffect);
                debuffOpt.ifPresent(debuffs::add);
            }

            return debuffs;

        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Error checking debuffs");
            return List.of();
        }
    }

    /**
     * Gets details about active poison effects (backwards compatibility).
     *
     * @param session The player's session with ECS access
     * @return Optional containing poison details, or empty if no poison active
     */
    public Optional<ActivePoisonDetails> getActivePoisonDetails(@Nonnull PlayerSession session) {
        var poisonDebuffs = getActiveDebuffs(session).stream()
            .filter(d -> d.debuffType() == DebuffType.POISON)
            .findFirst();

        return poisonDebuffs.map(d -> new ActivePoisonDetails(
            d.effectId(),
            d.remainingDuration(),
            d.initialDuration(),
            POISON_TIERS.getOrDefault(d.effectId(), 0)
        ));
    }

    /**
     * Gets all active debuffs of a specific type.
     *
     * @param session The player's session with ECS access
     * @param debuffType The type of debuff to filter for
     * @return List of matching debuff details (empty if none)
     */
    @Nonnull
    public List<ActiveDebuffDetails> getDebuffsByType(@Nonnull PlayerSession session,
                                                      @Nonnull DebuffType debuffType) {
        return getActiveDebuffs(session).stream()
            .filter(d -> d.debuffType() == debuffType)
            .toList();
    }

    /**
     * Creates debuff details from an active effect if it's a tracked debuff.
     */
    private Optional<ActiveDebuffDetails> createDebuffDetails(ActiveEntityEffect activeEffect) {
        if (activeEffect == null || !activeEffect.isDebuff()) {
            return Optional.empty();
        }

        var effectId = getEffectId(activeEffect);
        if (effectId == null) {
            return Optional.empty();
        }

        var debuffType = DEBUFF_EFFECT_MAP.get(effectId);
        if (debuffType == null) {
            return Optional.empty();
        }

        return Optional.of(new ActiveDebuffDetails(
            effectId,
            debuffType,
            activeEffect.getRemainingDuration(),
            activeEffect.getInitialDuration(),
            getEffectTier(effectId, debuffType)
        ));
    }

    /**
     * Gets the effect ID string from an active effect.
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
     * Gets the tier/intensity of an effect (used for scaling drain rates).
     * Returns 0 for base effects, 1-3 for tiered effects.
     */
    private int getEffectTier(String effectId, DebuffType debuffType) {
        // Poison has explicit tiers
        if (debuffType == DebuffType.POISON) {
            return POISON_TIERS.getOrDefault(effectId, 0);
        }

        // Other debuffs can have implicit tiers based on severity
        return switch (effectId) {
            // Burn tiers (Lava_Burn is most severe)
            case "Burn" -> 1;
            case "Flame_Staff_Burn" -> 1;
            case "Lava_Burn" -> 2;

            // Stun tiers
            case "Stun" -> 1;
            case "Bomb_Explode_Stun" -> 1;

            // Default tier
            default -> 0;
        };
    }

    /**
     * Details about an active debuff effect.
     */
    public record ActiveDebuffDetails(
        String effectId,
        DebuffType debuffType,
        float remainingDuration,
        float initialDuration,
        int tier
    ) {
        /**
         * Gets the progress of the debuff (0.0 = just started, 1.0 = about to expire).
         */
        public float progress() {
            if (initialDuration <= 0) {
                return 1.0f;
            }
            return 1.0f - (remainingDuration / initialDuration);
        }

        /**
         * Gets the severity multiplier based on tier (1.0 = base, higher = more severe).
         */
        public float tierMultiplier() {
            return switch (tier) {
                case 0 -> 1.0f;
                case 1 -> 1.0f;
                case 2 -> 1.25f;
                case 3 -> 1.5f;
                default -> 1.0f;
            };
        }
    }

    /**
     * Details about an active poison effect (backwards compatibility).
     */
    public record ActivePoisonDetails(
        String effectId,
        float remainingDuration,
        float initialDuration,
        int tier
    ) {
        public float progress() {
            if (initialDuration <= 0) {
                return 1.0f;
            }
            return 1.0f - (remainingDuration / initialDuration);
        }
    }
}
