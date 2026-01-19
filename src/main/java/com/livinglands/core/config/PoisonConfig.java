package com.livinglands.core.config;

import com.livinglands.modules.metabolism.poison.PoisonEffectType;

import java.util.List;

/**
 * Configuration for poison effects and poisonous items.
 * Poison effects drain metabolism stats (hunger, thirst, energy) rather than health.
 *
 * Supports two poison sources:
 * 1. Consumable items (mushrooms, potions) - uses mildToxin/slowPoison/purge configs
 * 2. Native Hytale poison debuffs (combat, NPCs) - uses nativePoison config
 */
public record PoisonConfig(
    boolean enabled,
    MildToxinConfig mildToxin,
    SlowPoisonConfig slowPoison,
    PurgeConfig purge,
    NativePoisonConfig nativePoison,
    PoisonItemsSection items
) {
    /**
     * Creates default poison configuration.
     */
    public static PoisonConfig defaults() {
        return new PoisonConfig(
            true,
            MildToxinConfig.defaults(),
            SlowPoisonConfig.defaults(),
            PurgeConfig.defaults(),
            NativePoisonConfig.defaults(),
            PoisonItemsSection.defaults()
        );
    }

    /**
     * Configuration for MILD_TOXIN effect type.
     * Short burst of metabolism drain - quick but noticeable.
     */
    public record MildToxinConfig(
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float tickIntervalSeconds,
        float durationSeconds
    ) {
        public static MildToxinConfig defaults() {
            // 8 seconds, drain every 1 sec = 8 ticks
            // Total drain: hunger -16, thirst -12, energy -8
            return new MildToxinConfig(2.0f, 1.5f, 1.0f, 1.0f, 8.0f);
        }
    }

    /**
     * Configuration for SLOW_POISON effect type.
     * Extended period of gradual metabolism drain.
     */
    public record SlowPoisonConfig(
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float tickIntervalSeconds,
        float durationSeconds
    ) {
        public static SlowPoisonConfig defaults() {
            // 45 seconds, drain every 3 sec = 15 ticks
            // Total drain: hunger -15, thirst -15, energy -7.5
            return new SlowPoisonConfig(1.0f, 1.0f, 0.5f, 3.0f, 45.0f);
        }
    }

    /**
     * Configuration for PURGE effect type.
     * Major burst drain followed by natural recovery period.
     */
    public record PurgeConfig(
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float drainIntervalSeconds,
        float drainDurationSeconds,
        float recoveryDurationSeconds
    ) {
        public static PurgeConfig defaults() {
            // 5 sec drain phase (every 0.5s = 10 ticks), then 20 sec recovery
            // Total drain: hunger -30, thirst -25, energy -20
            return new PurgeConfig(3.0f, 2.5f, 2.0f, 0.5f, 5.0f, 20.0f);
        }
    }

    /**
     * Configuration for native Hytale poison debuffs (green skull icon).
     * Applied when player receives poison from combat, NPCs, or environment.
     *
     * Drain rates are applied while the native poison debuff is active.
     * Tier multipliers scale the drain (T1=0.75x, T2=1.0x, T3=1.5x).
     */
    public record NativePoisonConfig(
        boolean enabled,
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float tickIntervalSeconds
    ) {
        public static NativePoisonConfig defaults() {
            // Moderate drain while native poison is active
            // Drain every 2 seconds to sync roughly with Hytale's 5-second damage cooldown
            return new NativePoisonConfig(true, 1.5f, 1.5f, 1.0f, 2.0f);
        }
    }

    /**
     * Section for configuring which items are poisonous.
     */
    public record PoisonItemsSection(
        List<PoisonItemEntry> vanilla,
        List<PoisonItemEntry> modded
    ) {
        public static PoisonItemsSection defaults() {
            return new PoisonItemsSection(
                List.of(
                    // Poison potions - intentionally poisonous drinks
                    new PoisonItemEntry("Potion_Poison", PoisonEffectType.SLOW_POISON, 0, 5),
                    new PoisonItemEntry("Potion_Poison_Minor", PoisonEffectType.MILD_TOXIN, 0, 3),
                    new PoisonItemEntry("Potion_Poison_Large", PoisonEffectType.PURGE, 0, 8),

                    // Spoiled/rotten items
                    new PoisonItemEntry("Food_Meat_Rotten", PoisonEffectType.SLOW_POISON, 2, 0),
                    new PoisonItemEntry("Food_Fish_Rotten", PoisonEffectType.MILD_TOXIN, 1, 0)
                ),
                List.of() // Empty modded section
            );
        }
    }

    /**
     * Entry for a poisonous item configuration.
     */
    public record PoisonItemEntry(
        String itemId,
        PoisonEffectType effectType,
        double hungerRestore,
        double thirstRestore
    ) {}
}
