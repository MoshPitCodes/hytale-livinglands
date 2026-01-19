package com.livinglands.modules.metabolism.debuff;

/**
 * Categories of debuff effects that impact player metabolism.
 *
 * Each debuff type has different metabolic impacts:
 * - POISON: Drains all stats moderately
 * - BURN: High thirst drain (dehydration), high energy drain, low hunger
 * - STUN: Very high energy drain (panic/struggle), low hunger/thirst
 * - FREEZE: Very high energy drain (hypothermia), low hunger/thirst
 * - ROOT: High energy drain (struggle to move), low hunger/thirst
 * - SLOW: Medium energy drain (fatigue), low hunger/thirst
 */
public enum DebuffType {
    /**
     * Poison effects - damage over time from toxins.
     * Effects: Poison, Poison_T1, Poison_T2, Poison_T3
     * Metabolism: Moderate drain to all stats
     */
    POISON,

    /**
     * Burn/fire effects - damage from heat and flames.
     * Effects: Burn, Lava_Burn, Flame_Staff_Burn
     * Metabolism: Very high thirst (dehydration), high energy, low hunger
     */
    BURN,

    /**
     * Stun effects - complete inability to act.
     * Effects: Stun, Bomb_Explode_Stun
     * Metabolism: Very high energy (panic/stress), low hunger/thirst
     */
    STUN,

    /**
     * Freeze effects - complete immobilization from cold.
     * Effects: Freeze
     * Metabolism: Very high energy (hypothermia), low hunger/thirst
     */
    FREEZE,

    /**
     * Root effects - rooted in place, cannot move.
     * Effects: Root
     * Metabolism: High energy (struggle), low hunger/thirst
     */
    ROOT,

    /**
     * Slow effects - reduced movement speed.
     * Effects: Slow, Two_Handed_Bow_Ability2_Slow
     * Metabolism: Medium energy (fatigue), low hunger/thirst
     */
    SLOW
}
