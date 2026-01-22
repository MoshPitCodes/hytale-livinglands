package com.livinglands.core.hud;

/**
 * Player HUD display preferences.
 * Stored in playerdata/hud/<uuid>.json
 */
public class HudPreferences {

    /** Whether metabolism stats (hunger, thirst, energy) are visible */
    public boolean statsVisible = true;

    /** Whether buffs (Well Fed, Hydrated, Energized) are visible */
    public boolean buffsVisible = true;

    /** Whether debuffs (Starving, Dehydrated, Exhausted) are visible */
    public boolean debuffsVisible = true;

    /** Whether passive abilities are visible */
    public boolean passivesVisible = false;

    /** Whether ability buffs (timed effects) are visible */
    public boolean abilityBuffsVisible = true;

    /**
     * Creates default preferences.
     */
    public HudPreferences() {
    }

    /**
     * Creates preferences with specified values.
     */
    public HudPreferences(boolean statsVisible, boolean buffsVisible, boolean debuffsVisible,
                         boolean passivesVisible, boolean abilityBuffsVisible) {
        this.statsVisible = statsVisible;
        this.buffsVisible = buffsVisible;
        this.debuffsVisible = debuffsVisible;
        this.passivesVisible = passivesVisible;
        this.abilityBuffsVisible = abilityBuffsVisible;
    }

    /**
     * Creates a copy of this preferences object.
     */
    public HudPreferences copy() {
        return new HudPreferences(statsVisible, buffsVisible, debuffsVisible,
            passivesVisible, abilityBuffsVisible);
    }

    /**
     * Returns default preferences.
     */
    public static HudPreferences defaults() {
        return new HudPreferences();
    }
}
