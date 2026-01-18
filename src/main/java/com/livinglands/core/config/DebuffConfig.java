package com.livinglands.core.config;

/**
 * Configuration for all debuff effects and their metabolism impacts.
 *
 * Each debuff type has unique metabolism drain patterns:
 * - POISON: Moderate drain to all stats (hunger, thirst, energy)
 * - BURN: Very high thirst (dehydration), high energy (pain/stress), low hunger
 * - STUN: Very high energy (panic/struggle), low hunger/thirst
 * - FREEZE: Very high energy (hypothermia), low hunger/thirst
 * - ROOT: High energy (struggle to move), low hunger/thirst
 * - SLOW: Medium energy (fatigue), low hunger/thirst
 */
public record DebuffConfig(
    boolean enabled,
    PoisonDebuffConfig poison,
    BurnDebuffConfig burn,
    StunDebuffConfig stun,
    FreezeDebuffConfig freeze,
    RootDebuffConfig root,
    SlowDebuffConfig slow
) {
    /**
     * Creates default debuff configuration.
     */
    public static DebuffConfig defaults() {
        return new DebuffConfig(
            true,
            PoisonDebuffConfig.defaults(),
            BurnDebuffConfig.defaults(),
            StunDebuffConfig.defaults(),
            FreezeDebuffConfig.defaults(),
            RootDebuffConfig.defaults(),
            SlowDebuffConfig.defaults()
        );
    }

    /**
     * Configuration for POISON debuffs (Poison, Poison_T1, Poison_T2, Poison_T3).
     *
     * Poison drains all metabolism stats moderately.
     * Applied while the poison debuff is active.
     * Tier multipliers scale the drain (T1=1.0x, T2=1.25x, T3=1.5x).
     */
    public record PoisonDebuffConfig(
        boolean enabled,
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float tickIntervalSeconds
    ) {
        public static PoisonDebuffConfig defaults() {
            // Moderate drain while poison is active
            // Drain every 2 seconds to sync with Hytale's damage ticks
            return new PoisonDebuffConfig(true, 1.5f, 1.5f, 1.0f, 2.0f);
        }
    }

    /**
     * Configuration for BURN debuffs (Burn, Lava_Burn, Flame_Staff_Burn).
     *
     * Burn causes severe dehydration and high energy drain from pain/stress.
     * Very high thirst drain (water evaporates from body).
     * High energy drain (pain, stress response).
     * Low hunger drain (body focuses on survival, not digestion).
     */
    public record BurnDebuffConfig(
        boolean enabled,
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float tickIntervalSeconds
    ) {
        public static BurnDebuffConfig defaults() {
            // Very high thirst from dehydration, high energy from pain
            // Drain every 1 second (burns are intense)
            return new BurnDebuffConfig(true, 0.5f, 3.0f, 2.0f, 1.0f);
        }
    }

    /**
     * Configuration for STUN debuffs (Stun, Bomb_Explode_Stun).
     *
     * Stun causes very high energy drain from panic and struggle.
     * Very high energy drain (panic, trying to regain control).
     * Low hunger/thirst drain (short duration effect).
     */
    public record StunDebuffConfig(
        boolean enabled,
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float tickIntervalSeconds
    ) {
        public static StunDebuffConfig defaults() {
            // Very high energy from panic/struggle
            // Drain every 1 second (stun is intense but short)
            return new StunDebuffConfig(true, 0.5f, 0.5f, 3.0f, 1.0f);
        }
    }

    /**
     * Configuration for FREEZE debuffs (Freeze).
     *
     * Freeze causes very high energy drain from hypothermia and shivering.
     * Very high energy drain (body fighting cold, shivering).
     * Low hunger/thirst drain (metabolism slowed by cold).
     */
    public record FreezeDebuffConfig(
        boolean enabled,
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float tickIntervalSeconds
    ) {
        public static FreezeDebuffConfig defaults() {
            // Very high energy from hypothermia/shivering
            // Drain every 1.5 seconds
            return new FreezeDebuffConfig(true, 0.5f, 0.5f, 2.5f, 1.5f);
        }
    }

    /**
     * Configuration for ROOT debuffs (Root).
     *
     * Root causes high energy drain from struggling to move.
     * High energy drain (struggling against root).
     * Low hunger/thirst drain (can still rest in place).
     */
    public record RootDebuffConfig(
        boolean enabled,
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float tickIntervalSeconds
    ) {
        public static RootDebuffConfig defaults() {
            // High energy from struggling
            // Drain every 2 seconds
            return new RootDebuffConfig(true, 0.5f, 0.5f, 2.0f, 2.0f);
        }
    }

    /**
     * Configuration for SLOW debuffs (Slow, Two_Handed_Bow_Ability2_Slow).
     *
     * Slow causes medium energy drain from fatigue.
     * Medium energy drain (moving slowly is tiring).
     * Low hunger/thirst drain (minimal metabolic impact).
     */
    public record SlowDebuffConfig(
        boolean enabled,
        float hungerDrainPerTick,
        float thirstDrainPerTick,
        float energyDrainPerTick,
        float tickIntervalSeconds
    ) {
        public static SlowDebuffConfig defaults() {
            // Medium energy from fatigue
            // Drain every 2.5 seconds
            return new SlowDebuffConfig(true, 0.5f, 0.5f, 1.5f, 2.5f);
        }
    }
}
