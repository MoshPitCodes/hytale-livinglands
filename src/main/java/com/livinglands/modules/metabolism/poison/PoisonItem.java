package com.livinglands.modules.metabolism.poison;

import javax.annotation.Nonnull;

/**
 * Represents a poisonous consumable item with its effect configuration.
 *
 * @param itemId The item identifier
 * @param effectType The type of poison effect (or RANDOM for random selection)
 * @param hungerRestore Optional hunger restoration (can be positive for deceptive items)
 * @param thirstRestore Optional thirst restoration
 */
public record PoisonItem(
    @Nonnull String itemId,
    @Nonnull PoisonEffectType effectType,
    double hungerRestore,
    double thirstRestore
) {
    /**
     * Creates a poison item with no stat restoration.
     */
    public static PoisonItem of(@Nonnull String itemId, @Nonnull PoisonEffectType effectType) {
        return new PoisonItem(itemId, effectType, 0, 0);
    }

    /**
     * Creates a poison item with hunger restoration (deceptive food).
     */
    public static PoisonItem withHunger(@Nonnull String itemId, @Nonnull PoisonEffectType effectType, double hunger) {
        return new PoisonItem(itemId, effectType, hunger, 0);
    }

    /**
     * Creates a poison item with thirst restoration (poisoned drink).
     */
    public static PoisonItem withThirst(@Nonnull String itemId, @Nonnull PoisonEffectType effectType, double thirst) {
        return new PoisonItem(itemId, effectType, 0, thirst);
    }

    /**
     * Checks if this poison provides any stat restoration.
     */
    public boolean restoresStats() {
        return hungerRestore > 0 || thirstRestore > 0;
    }
}
