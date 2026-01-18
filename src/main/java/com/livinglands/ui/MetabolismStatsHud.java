package com.livinglands.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.livinglands.metabolism.PlayerMetabolismData;

import javax.annotation.Nonnull;

/**
 * Custom HUD overlay displaying metabolism stats (Hunger, Thirst, Energy).
 * Uses icon + percentage text display for simplicity and reliability.
 *
 * UI file: Common/UI/Custom/Hud/MetabolismHud.ui
 */
public class MetabolismStatsHud extends CustomUIHud {

    private static final String UI_FILE = "Hud/MetabolismHud.ui";

    // Current stat values (cached for comparison)
    private double lastHunger = -1;
    private double lastThirst = -1;
    private double lastEnergy = -1;

    // Configuration
    private final boolean showHunger;
    private final boolean showThirst;
    private final boolean showEnergy;

    // Initial values for build()
    private final double initialHunger;
    private final double initialThirst;
    private final double initialEnergy;

    /**
     * Creates a new metabolism stats HUD for a player.
     */
    public MetabolismStatsHud(@Nonnull PlayerRef playerRef,
                               boolean showHunger,
                               boolean showThirst,
                               boolean showEnergy,
                               double initialHunger,
                               double initialThirst,
                               double initialEnergy) {
        super(playerRef);
        this.showHunger = showHunger;
        this.showThirst = showThirst;
        this.showEnergy = showEnergy;
        this.initialHunger = initialHunger;
        this.initialThirst = initialThirst;
        this.initialEnergy = initialEnergy;
    }

    @Override
    protected void build(UICommandBuilder builder) {
        builder.append(UI_FILE);

        // Set initial visibility and values
        if (showHunger) {
            builder.set("#HungerText.Text", formatValue(initialHunger));
            lastHunger = initialHunger;
        } else {
            builder.set("#HungerStat.Visible", false);
        }

        if (showThirst) {
            builder.set("#ThirstText.Text", formatValue(initialThirst));
            lastThirst = initialThirst;
        } else {
            builder.set("#ThirstStat.Visible", false);
        }

        if (showEnergy) {
            builder.set("#EnergyText.Text", formatValue(initialEnergy));
            lastEnergy = initialEnergy;
        } else {
            builder.set("#EnergyStat.Visible", false);
        }
    }

    /**
     * Updates the HUD with current metabolism values.
     */
    public void updateStats(@Nonnull PlayerMetabolismData data) {
        var hunger = data.getHunger();
        var thirst = data.getThirst();
        var energy = data.getEnergy();

        // Only update if values changed
        if (hunger == lastHunger && thirst == lastThirst && energy == lastEnergy) {
            return;
        }

        var builder = new UICommandBuilder();
        boolean needsUpdate = false;

        if (showHunger && hunger != lastHunger) {
            builder.set("#HungerText.Text", formatValue(hunger));
            lastHunger = hunger;
            needsUpdate = true;
        }

        if (showThirst && thirst != lastThirst) {
            builder.set("#ThirstText.Text", formatValue(thirst));
            lastThirst = thirst;
            needsUpdate = true;
        }

        if (showEnergy && energy != lastEnergy) {
            builder.set("#EnergyText.Text", formatValue(energy));
            lastEnergy = energy;
            needsUpdate = true;
        }

        if (needsUpdate) {
            update(false, builder);
        }
    }

    /**
     * Formats a value as a whole number string.
     */
    private String formatValue(double value) {
        return String.format("%.0f", value);
    }

    /**
     * Resets cached values to force a full update on next call.
     */
    public void resetCache() {
        lastHunger = -1;
        lastThirst = -1;
        lastEnergy = -1;
    }
}
