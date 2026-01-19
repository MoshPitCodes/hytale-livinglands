package com.livinglands.modules.metabolism.buff;

/**
 * Details about an active buff on a player.
 *
 * Used to track native Hytale buff effects detected via EffectControllerComponent.
 */
public record ActiveBuffDetails(
    String effectId,           // Hytale effect ID (e.g., "Food_Health_Boost_T2")
    BuffType buffType,         // Mapped buff type
    float remainingDuration,   // Seconds remaining
    float initialDuration,     // Total duration
    int tier                   // Effect tier (1-3)
) {
    /**
     * Gets the tier multiplier for scaling buff effects.
     * T1 = 1.0x (base)
     * T2 = 1.25x (25% stronger)
     * T3 = 1.5x (50% stronger)
     */
    public float getTierMultiplier() {
        return switch (tier) {
            case 1 -> 1.0f;
            case 2 -> 1.25f;
            case 3 -> 1.5f;
            default -> 1.0f;
        };
    }

    /**
     * Gets the percentage of duration remaining (0.0 - 1.0).
     */
    public float getDurationPercent() {
        return initialDuration > 0 ? remainingDuration / initialDuration : 0f;
    }

    /**
     * Checks if this buff has expired.
     */
    public boolean isExpired() {
        return remainingDuration <= 0;
    }

    /**
     * Gets a formatted string showing remaining time.
     */
    public String getFormattedDuration() {
        int seconds = (int) remainingDuration;
        if (seconds >= 60) {
            int minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%dm %ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }
}
