package com.livinglands.metabolism.poison;

/**
 * Types of poison effects that drain metabolism stats.
 *
 * Unlike vanilla poisons that damage health, these effects
 * drain the player's hunger, thirst, and/or energy stats.
 */
public enum PoisonEffectType {
    /**
     * Mild toxin: Short burst of metabolism drain.
     * Quick but noticeable effect on hunger/thirst.
     */
    MILD_TOXIN,

    /**
     * Slow poison: Extended period of gradual metabolism drain.
     * Lower drain per tick but lasts much longer.
     */
    SLOW_POISON,

    /**
     * Purge: Major burst drain followed by recovery boost.
     * Severe initial drain but metabolism recovers faster afterward.
     */
    PURGE,

    /**
     * Random: Randomly selects one of the above effect types.
     * Used for unpredictable items like mystery mushrooms.
     */
    RANDOM
}
