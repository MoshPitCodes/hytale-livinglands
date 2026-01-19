package com.livinglands.modules.metabolism.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.livinglands.core.hud.HudElement;
import com.livinglands.modules.metabolism.DebuffsSystem;
import com.livinglands.modules.metabolism.MetabolismSystem;
import com.livinglands.modules.metabolism.config.MetabolismModuleConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD element for metabolism stats (hunger, thirst, energy).
 * Registers with the HudModule to display stats in the combined HUD.
 *
 * Uses text-based progress bars with labels showing current values.
 * Also displays active buffs and debuffs below the bars.
 */
public class MetabolismHudElement implements HudElement {

    private static final String ID = "metabolism";
    private static final String NAME = "Metabolism Stats";
    private static final double MAX_VALUE = 100.0;
    private static final int BAR_SEGMENTS = 10;  // Number of segments in text progress bar
    private static final int MAX_BUFFS = 2;      // Maximum buffs to display
    private static final int MAX_DEBUFFS = 2;    // Maximum debuffs to display

    private final MetabolismSystem system;
    private final MetabolismModuleConfig config;
    @Nullable
    private DebuffsSystem debuffsSystem;

    // Cache last values to avoid unnecessary updates
    private final Map<UUID, CachedValues> playerCache = new ConcurrentHashMap<>();

    private record CachedValues(double hunger, double thirst, double energy,
                                 List<String> buffs, List<String> debuffs) {}

    public MetabolismHudElement(@Nonnull MetabolismSystem system,
                                 @Nonnull MetabolismModuleConfig config) {
        this.system = system;
        this.config = config;
    }

    /**
     * Set the debuffs system for active effects display.
     */
    public void setDebuffsSystem(@Nullable DebuffsSystem debuffsSystem) {
        this.debuffsSystem = debuffsSystem;
    }

    @Override
    @Nonnull
    public String getId() {
        return ID;
    }

    @Override
    @Nonnull
    public String getName() {
        return NAME;
    }

    @Override
    public void buildInitialValues(@Nonnull UICommandBuilder builder, @Nonnull UUID playerId) {
        var dataOpt = system.getPlayerData(playerId);
        if (dataOpt.isEmpty()) {
            // Set placeholders if data not loaded
            if (config.metabolism.enableHunger) {
                builder.set("#HungerBar.Text", formatBar(0));
            }
            if (config.metabolism.enableThirst) {
                builder.set("#ThirstBar.Text", formatBar(0));
            }
            if (config.metabolism.enableEnergy) {
                builder.set("#EnergyBar.Text", formatBar(0));
            }
            // Clear effects
            for (int i = 1; i <= MAX_BUFFS; i++) {
                builder.set("#Buff" + i + ".Text", "");
            }
            for (int i = 1; i <= MAX_DEBUFFS; i++) {
                builder.set("#Debuff" + i + ".Text", "");
            }
            return;
        }

        var data = dataOpt.get();

        if (config.metabolism.enableHunger) {
            double hunger = data.getHunger();
            builder.set("#HungerBar.Text", formatBar(hunger));
        } else {
            builder.set("#HungerBar.Text", "");
            builder.set("#HungerName.Text", "");
        }

        if (config.metabolism.enableThirst) {
            double thirst = data.getThirst();
            builder.set("#ThirstBar.Text", formatBar(thirst));
        } else {
            builder.set("#ThirstBar.Text", "");
            builder.set("#ThirstName.Text", "");
        }

        if (config.metabolism.enableEnergy) {
            double energy = data.getEnergy();
            builder.set("#EnergyBar.Text", formatBar(energy));
        } else {
            builder.set("#EnergyBar.Text", "");
            builder.set("#EnergyName.Text", "");
        }

        // Build and display active effects (buffs and debuffs separately)
        var buffs = getActiveBuffs(data.getHunger(), data.getThirst(), data.getEnergy());
        var debuffs = getActiveDebuffs(data.getHunger(), data.getThirst(), data.getEnergy());
        updateBuffsDisplay(builder, buffs);
        updateDebuffsDisplay(builder, debuffs);

        // Cache initial values
        playerCache.put(playerId, new CachedValues(
            data.getHunger(), data.getThirst(), data.getEnergy(), buffs, debuffs
        ));
    }

    @Override
    public boolean update(@Nonnull UICommandBuilder builder, @Nonnull UUID playerId) {
        var dataOpt = system.getPlayerData(playerId);
        if (dataOpt.isEmpty()) {
            return false;
        }

        var data = dataOpt.get();
        var cached = playerCache.get(playerId);

        double hunger = data.getHunger();
        double thirst = data.getThirst();
        double energy = data.getEnergy();

        // Get current active effects (buffs and debuffs separately)
        var buffs = getActiveBuffs(hunger, thirst, energy);
        var debuffs = getActiveDebuffs(hunger, thirst, energy);

        // Check if any values changed (use small tolerance for floating point)
        boolean valuesChanged = cached == null ||
            Math.abs(hunger - cached.hunger) >= 0.1 ||
            Math.abs(thirst - cached.thirst) >= 0.1 ||
            Math.abs(energy - cached.energy) >= 0.1;

        boolean buffsChanged = cached == null || !buffs.equals(cached.buffs);
        boolean debuffsChanged = cached == null || !debuffs.equals(cached.debuffs);

        if (!valuesChanged && !buffsChanged && !debuffsChanged) {
            return false;
        }

        boolean hasUpdates = false;

        if (config.metabolism.enableHunger && (cached == null || Math.abs(hunger - cached.hunger) >= 0.1)) {
            builder.set("#HungerBar.Text", formatBar(hunger));
            hasUpdates = true;
        }

        if (config.metabolism.enableThirst && (cached == null || Math.abs(thirst - cached.thirst) >= 0.1)) {
            builder.set("#ThirstBar.Text", formatBar(thirst));
            hasUpdates = true;
        }

        if (config.metabolism.enableEnergy && (cached == null || Math.abs(energy - cached.energy) >= 0.1)) {
            builder.set("#EnergyBar.Text", formatBar(energy));
            hasUpdates = true;
        }

        if (buffsChanged) {
            updateBuffsDisplay(builder, buffs);
            hasUpdates = true;
        }

        if (debuffsChanged) {
            updateDebuffsDisplay(builder, debuffs);
            hasUpdates = true;
        }

        if (hasUpdates) {
            playerCache.put(playerId, new CachedValues(hunger, thirst, energy, buffs, debuffs));
        }

        return hasUpdates;
    }

    @Override
    public void removePlayer(@Nonnull UUID playerId) {
        playerCache.remove(playerId);
    }

    @Override
    public boolean isEnabled() {
        return config.metabolism.enableHunger ||
               config.metabolism.enableThirst ||
               config.metabolism.enableEnergy;
    }

    /**
     * Format just the progress bar with value (no label name).
     * Example: "75 [|||||||...]"
     */
    private String formatBar(double value) {
        int filledSegments = (int) Math.round((value / MAX_VALUE) * BAR_SEGMENTS);
        filledSegments = Math.max(0, Math.min(BAR_SEGMENTS, filledSegments));

        StringBuilder bar = new StringBuilder();
        bar.append(String.format("%.0f ", value));
        bar.append("[");
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            bar.append(i < filledSegments ? "|" : ".");
        }
        bar.append("]");

        return bar.toString();
    }

    /**
     * Get list of active buffs for display (violet color).
     * Returns formatted strings like "[+] Well Fed"
     */
    private List<String> getActiveBuffs(double hunger, double thirst, double energy) {
        List<String> buffs = new ArrayList<>();

        if (hunger >= 80.0) {
            buffs.add("[+] Well Fed");
        }
        if (thirst >= 80.0) {
            buffs.add("[+] Hydrated");
        }
        if (energy >= 80.0) {
            buffs.add("[+] Energized");
        }

        // Limit to max buffs
        if (buffs.size() > MAX_BUFFS) {
            buffs = buffs.subList(0, MAX_BUFFS);
        }

        return buffs;
    }

    /**
     * Get list of active debuffs for display (red color).
     * Returns formatted strings like "[-] Starving"
     */
    private List<String> getActiveDebuffs(double hunger, double thirst, double energy) {
        List<String> debuffs = new ArrayList<>();

        if (hunger <= 20.0) {
            debuffs.add("[-] Starving");
        }
        if (thirst <= 20.0) {
            debuffs.add("[-] Dehydrated");
        }
        if (energy <= 20.0) {
            debuffs.add("[-] Exhausted");
        }

        // Limit to max debuffs
        if (debuffs.size() > MAX_DEBUFFS) {
            debuffs = debuffs.subList(0, MAX_DEBUFFS);
        }

        return debuffs;
    }

    /**
     * Update the buffs display labels (violet).
     */
    private void updateBuffsDisplay(UICommandBuilder builder, List<String> buffs) {
        for (int i = 1; i <= MAX_BUFFS; i++) {
            if (i <= buffs.size()) {
                builder.set("#Buff" + i + ".Text", buffs.get(i - 1));
            } else {
                builder.set("#Buff" + i + ".Text", "");
            }
        }
    }

    /**
     * Update the debuffs display labels (red).
     */
    private void updateDebuffsDisplay(UICommandBuilder builder, List<String> debuffs) {
        for (int i = 1; i <= MAX_DEBUFFS; i++) {
            if (i <= debuffs.size()) {
                builder.set("#Debuff" + i + ".Text", debuffs.get(i - 1));
            } else {
                builder.set("#Debuff" + i + ".Text", "");
            }
        }
    }
}
