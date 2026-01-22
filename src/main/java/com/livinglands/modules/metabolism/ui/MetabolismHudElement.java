package com.livinglands.modules.metabolism.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.livinglands.core.hud.HudElement;
import com.livinglands.modules.leveling.ability.AbilitySystem;
import com.livinglands.modules.leveling.ability.AbilityType;
import com.livinglands.modules.leveling.ability.TimedBuffManager;
import com.livinglands.modules.leveling.config.AbilityConfig;
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
public class MetabolismHudElement implements HudElement, AbilitySystem.AbilityUnlockCacheListener {

    private static final String ID = "metabolism";
    private static final String NAME = "Metabolism Stats";
    private static final double MAX_VALUE = 100.0;
    private static final int BAR_SEGMENTS = 10;  // Number of segments in text progress bar
    private static final int MAX_BUFFS = 3;      // Maximum buffs to display
    private static final int MAX_DEBUFFS = 3;    // Maximum debuffs to display
    private static final int MAX_PASSIVES = 5;   // Maximum passive abilities to display
    private static final int MAX_ABILITY_BUFFS = 3; // Maximum active ability buffs to display

    private final MetabolismSystem system;
    private final MetabolismModuleConfig config;
    @Nullable
    private DebuffsSystem debuffsSystem;
    @Nullable
    private AbilitySystem abilitySystem;
    @Nullable
    private TimedBuffManager timedBuffManager;
    @Nullable
    private com.livinglands.core.hud.HudModule hudModule;

    // Note: Display preferences are now persisted via HudModule.getPreferences()/savePreferences()
    // The old in-memory sets have been removed in favor of persistent storage

    // Cache last values to avoid unnecessary updates
    private final Map<UUID, CachedValues> playerCache = new ConcurrentHashMap<>();

    // Cache for formatted passive ability strings (invalidated by AbilitySystem)
    private final Map<UUID, List<String>> passivesStringCache = new ConcurrentHashMap<>();

    private record CachedValues(double hunger, double thirst, double energy,
                                 List<String> buffs, List<String> debuffs,
                                 List<String> passives, List<String> abilityBuffs) {}

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

    /**
     * Set the ability system for passive abilities display.
     */
    public void setAbilitySystem(@Nullable AbilitySystem abilitySystem) {
        // Unregister from old system if any
        if (this.abilitySystem != null) {
            this.abilitySystem.removeCacheListener(this);
        }

        this.abilitySystem = abilitySystem;

        // Register as cache listener to invalidate our string cache on level-up
        if (abilitySystem != null) {
            abilitySystem.addCacheListener(this);
        }
    }

    /**
     * Set the timed buff manager for active ability buffs display.
     */
    public void setTimedBuffManager(@Nullable TimedBuffManager timedBuffManager) {
        this.timedBuffManager = timedBuffManager;
    }

    /**
     * Called when a player's ability unlock cache is invalidated (e.g., level-up).
     * Clears our cached passive strings so they get rebuilt on next update.
     */
    @Override
    public void onAbilityCacheInvalidated(UUID playerId) {
        passivesStringCache.remove(playerId);
    }

    /**
     * Set the HUD module for immediate updates.
     */
    public void setHudModule(@Nonnull com.livinglands.core.hud.HudModule hudModule) {
        this.hudModule = hudModule;
    }

    /**
     * Toggle the passive abilities display visibility for a player.
     * @return true if now visible, false if hidden
     */
    public boolean togglePassivesDisplay(@Nonnull UUID playerId) {
        if (hudModule == null) {
            return false;
        }

        var prefs = hudModule.getPreferences(playerId);
        prefs.passivesVisible = !prefs.passivesVisible;
        boolean nowVisible = prefs.passivesVisible;
        hudModule.savePreferences(playerId, prefs);

        // Send immediate update
        if (hudModule.isHudReady(playerId)) {
            var builder = new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder();
            if (nowVisible) {
                var passives = getUnlockedPassiveAbilities(playerId);
                updatePassivesDisplay(builder, passives);
            } else {
                // Hide all passive containers
                for (int i = 1; i <= MAX_PASSIVES; i++) {
                    builder.set("#Passive" + i + ".Text", "");
                    builder.set("#Passive" + i + "Container.Visible", false);
                }
            }
            hudModule.sendImmediateUpdate(playerId, builder);
        }

        return nowVisible;
    }

    /**
     * Check if passives display is visible for a player.
     */
    public boolean isPassivesVisible(@Nonnull UUID playerId) {
        if (hudModule == null) {
            return false; // Default hidden if no HudModule
        }
        return hudModule.getPreferences(playerId).passivesVisible;
    }

    /**
     * Toggle the stats display visibility for a player.
     * @return true if now visible, false if hidden
     */
    public boolean toggleStatsDisplay(@Nonnull UUID playerId) {
        if (hudModule == null) {
            return true;
        }

        var prefs = hudModule.getPreferences(playerId);
        prefs.statsVisible = !prefs.statsVisible;
        boolean nowVisible = prefs.statsVisible;
        hudModule.savePreferences(playerId, prefs);

        // Send immediate update
        if (hudModule.isHudReady(playerId)) {
            var builder = new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder();
            if (nowVisible) {
                // Show the entire metabolism bars container (includes backdrop)
                builder.set("#MetabolismBars.Visible", true);
                var dataOpt = system.getPlayerData(playerId);
                if (dataOpt.isPresent()) {
                    var data = dataOpt.get();
                    if (config.metabolism.enableHunger) {
                        builder.set("#HungerBar.Text", formatBar(data.getHunger()));
                    }
                    if (config.metabolism.enableThirst) {
                        builder.set("#ThirstBar.Text", formatBar(data.getThirst()));
                    }
                    if (config.metabolism.enableEnergy) {
                        builder.set("#EnergyBar.Text", formatBar(data.getEnergy()));
                    }
                }
            } else {
                // Hide the entire metabolism bars container (includes backdrop)
                builder.set("#MetabolismBars.Visible", false);
            }
            hudModule.sendImmediateUpdate(playerId, builder);
        }

        return nowVisible;
    }

    /**
     * Check if stats display is visible for a player.
     */
    public boolean isStatsVisible(@Nonnull UUID playerId) {
        if (hudModule == null) {
            return true; // Default visible if no HudModule
        }
        return hudModule.getPreferences(playerId).statsVisible;
    }

    /**
     * Toggle the buffs display visibility for a player.
     * @return true if now visible, false if hidden
     */
    public boolean toggleBuffsDisplay(@Nonnull UUID playerId) {
        if (hudModule == null) {
            return true;
        }

        var prefs = hudModule.getPreferences(playerId);
        prefs.buffsVisible = !prefs.buffsVisible;
        boolean nowVisible = prefs.buffsVisible;
        hudModule.savePreferences(playerId, prefs);

        // Send immediate update
        if (hudModule.isHudReady(playerId)) {
            var builder = new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder();
            if (nowVisible) {
                var dataOpt = system.getPlayerData(playerId);
                if (dataOpt.isPresent()) {
                    var data = dataOpt.get();
                    var buffs = getActiveBuffs(data.getHunger(), data.getThirst(), data.getEnergy());
                    updateBuffsDisplay(builder, buffs);
                }
            } else {
                // Hide all buff containers
                for (int i = 1; i <= MAX_BUFFS; i++) {
                    builder.set("#Buff" + i + ".Text", "");
                    builder.set("#Buff" + i + "Container.Visible", false);
                }
            }
            hudModule.sendImmediateUpdate(playerId, builder);
        }

        return nowVisible;
    }

    /**
     * Check if buffs display is visible for a player.
     */
    public boolean isBuffsVisible(@Nonnull UUID playerId) {
        if (hudModule == null) {
            return true; // Default visible if no HudModule
        }
        return hudModule.getPreferences(playerId).buffsVisible;
    }

    /**
     * Toggle the debuffs display visibility for a player.
     * @return true if now visible, false if hidden
     */
    public boolean toggleDebuffsDisplay(@Nonnull UUID playerId) {
        if (hudModule == null) {
            return true;
        }

        var prefs = hudModule.getPreferences(playerId);
        prefs.debuffsVisible = !prefs.debuffsVisible;
        boolean nowVisible = prefs.debuffsVisible;
        hudModule.savePreferences(playerId, prefs);

        // Send immediate update
        if (hudModule.isHudReady(playerId)) {
            var builder = new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder();
            if (nowVisible) {
                var dataOpt = system.getPlayerData(playerId);
                if (dataOpt.isPresent()) {
                    var data = dataOpt.get();
                    var debuffs = getActiveDebuffs(data.getHunger(), data.getThirst(), data.getEnergy());
                    updateDebuffsDisplay(builder, debuffs);
                }
            } else {
                // Hide all debuff containers
                for (int i = 1; i <= MAX_DEBUFFS; i++) {
                    builder.set("#Debuff" + i + ".Text", "");
                    builder.set("#Debuff" + i + "Container.Visible", false);
                }
            }
            hudModule.sendImmediateUpdate(playerId, builder);
        }

        return nowVisible;
    }

    /**
     * Check if debuffs display is visible for a player.
     */
    public boolean isDebuffsVisible(@Nonnull UUID playerId) {
        if (hudModule == null) {
            return true; // Default visible if no HudModule
        }
        return hudModule.getPreferences(playerId).debuffsVisible;
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
                builder.set("#Buff" + i + "Container.Visible", false);
            }
            for (int i = 1; i <= MAX_DEBUFFS; i++) {
                builder.set("#Debuff" + i + ".Text", "");
                builder.set("#Debuff" + i + "Container.Visible", false);
            }
            // Clear ability buffs
            for (int i = 1; i <= MAX_ABILITY_BUFFS; i++) {
                builder.set("#AbilityBuff" + i + ".Text", "");
                builder.set("#AbilityBuff" + i + "Container.Visible", false);
            }
            // Clear passive abilities
            for (int i = 1; i <= MAX_PASSIVES; i++) {
                builder.set("#Passive" + i + ".Text", "");
                builder.set("#Passive" + i + "Container.Visible", false);
            }
            return;
        }

        var data = dataOpt.get();
        boolean statsVisible = isStatsVisible(playerId);
        boolean buffsVisible = isBuffsVisible(playerId);
        boolean debuffsVisible = isDebuffsVisible(playerId);

        // Stats display (respects toggle)
        if (statsVisible) {
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
        } else {
            // Hide stats by hiding the entire metabolism bars container (includes backdrop)
            builder.set("#MetabolismBars.Visible", false);
        }

        // Build and display active effects (buffs and debuffs separately, respecting toggles)
        var buffs = getActiveBuffs(data.getHunger(), data.getThirst(), data.getEnergy());
        var debuffs = getActiveDebuffs(data.getHunger(), data.getThirst(), data.getEnergy());

        if (buffsVisible) {
            updateBuffsDisplay(builder, buffs);
        } else {
            for (int i = 1; i <= MAX_BUFFS; i++) {
                builder.set("#Buff" + i + ".Text", "");
                builder.set("#Buff" + i + "Container.Visible", false);
            }
        }

        if (debuffsVisible) {
            updateDebuffsDisplay(builder, debuffs);
        } else {
            for (int i = 1; i <= MAX_DEBUFFS; i++) {
                builder.set("#Debuff" + i + ".Text", "");
                builder.set("#Debuff" + i + "Container.Visible", false);
            }
        }

        // Build and display active ability buffs (timed effects from passive abilities)
        var abilityBuffs = getActiveAbilityBuffs(playerId);
        updateAbilityBuffsDisplay(builder, abilityBuffs);

        // Build and display passive abilities (if enabled for player)
        var passives = getUnlockedPassiveAbilities(playerId);
        if (isPassivesVisible(playerId)) {
            updatePassivesDisplay(builder, passives);
        } else {
            // Ensure passives are hidden
            for (int i = 1; i <= MAX_PASSIVES; i++) {
                builder.set("#Passive" + i + ".Text", "");
                builder.set("#Passive" + i + "Container.Visible", false);
            }
        }

        // Cache initial values
        playerCache.put(playerId, new CachedValues(
            data.getHunger(), data.getThirst(), data.getEnergy(), buffs, debuffs, passives, abilityBuffs
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
        var abilityBuffs = getActiveAbilityBuffs(playerId);
        var passives = getUnlockedPassiveAbilities(playerId);

        // Check if any values changed (use small tolerance for floating point)
        boolean valuesChanged = cached == null ||
            Math.abs(hunger - cached.hunger) >= 0.1 ||
            Math.abs(thirst - cached.thirst) >= 0.1 ||
            Math.abs(energy - cached.energy) >= 0.1;

        boolean buffsChanged = cached == null || !buffs.equals(cached.buffs);
        boolean debuffsChanged = cached == null || !debuffs.equals(cached.debuffs);
        boolean abilityBuffsChanged = cached == null || !abilityBuffs.equals(cached.abilityBuffs);
        boolean passivesChanged = cached == null || !passives.equals(cached.passives);

        if (!valuesChanged && !buffsChanged && !debuffsChanged && !abilityBuffsChanged && !passivesChanged) {
            return false;
        }

        boolean hasUpdates = false;
        boolean statsVisible = isStatsVisible(playerId);
        boolean buffsVisible = isBuffsVisible(playerId);
        boolean debuffsVisible = isDebuffsVisible(playerId);

        // Only update stats if visible
        if (statsVisible) {
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
        }

        // Always update buffs and debuffs together to ensure consistency (if visible)
        if (buffsChanged || debuffsChanged) {
            if (buffsVisible) {
                updateBuffsDisplay(builder, buffs);
            }
            if (debuffsVisible) {
                updateDebuffsDisplay(builder, debuffs);
            }
            hasUpdates = true;
        }

        // Update ability buffs (always update since they have countdown timers)
        if (abilityBuffsChanged) {
            updateAbilityBuffsDisplay(builder, abilityBuffs);
            hasUpdates = true;
        }

        // Update passive abilities if changed and visible for player
        if (passivesChanged && isPassivesVisible(playerId)) {
            updatePassivesDisplay(builder, passives);
            hasUpdates = true;
        }

        if (hasUpdates) {
            playerCache.put(playerId, new CachedValues(hunger, thirst, energy, buffs, debuffs, passives, abilityBuffs));
        }

        return hasUpdates;
    }

    @Override
    public void removePlayer(@Nonnull UUID playerId) {
        playerCache.remove(playerId);
        passivesStringCache.remove(playerId);
        // Note: Preferences are persisted via HudModule and saved on player disconnect
    }

    @Override
    public boolean isEnabled() {
        return config.metabolism.enableHunger ||
               config.metabolism.enableThirst ||
               config.metabolism.enableEnergy;
    }

    /**
     * Format just the progress bar with value (no label name).
     * Example: "[|||||||...] 75"
     * Bar comes first for consistent alignment, value displayed after.
     */
    private String formatBar(double value) {
        int filledSegments = (int) Math.round((value / MAX_VALUE) * BAR_SEGMENTS);
        filledSegments = Math.max(0, Math.min(BAR_SEGMENTS, filledSegments));

        StringBuilder bar = new StringBuilder();
        bar.append("[");
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            bar.append(i < filledSegments ? "|" : ".");
        }
        bar.append("] ");
        bar.append(String.format("%.0f", value));

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

        return debuffs;
    }

    /**
     * Update the buffs display labels (violet).
     * Also controls container visibility - hidden when empty.
     * Always clears text first to prevent stale data.
     */
    private void updateBuffsDisplay(UICommandBuilder builder, List<String> buffs) {
        for (int i = 1; i <= MAX_BUFFS; i++) {
            String selector = "#Buff" + i;
            String containerSelector = selector + "Container";

            if (i <= buffs.size()) {
                builder.set(selector + ".Text", buffs.get(i - 1));
                builder.set(containerSelector + ".Visible", true);
            } else {
                // Always clear text and hide when not in use
                builder.set(selector + ".Text", "");
                builder.set(containerSelector + ".Visible", false);
            }
        }
    }

    /**
     * Update the debuffs display labels (red).
     * Also controls container visibility - hidden when empty.
     * Always clears text first to prevent stale data.
     */
    private void updateDebuffsDisplay(UICommandBuilder builder, List<String> debuffs) {
        for (int i = 1; i <= MAX_DEBUFFS; i++) {
            String selector = "#Debuff" + i;
            String containerSelector = selector + "Container";

            if (i <= debuffs.size()) {
                builder.set(selector + ".Text", debuffs.get(i - 1));
                builder.set(containerSelector + ".Visible", true);
            } else {
                // Always clear text and hide when not in use
                builder.set(selector + ".Text", "");
                builder.set(containerSelector + ".Visible", false);
            }
        }
    }

    /**
     * Get list of active ability buffs for display (cyan color).
     * Returns formatted strings like "[!] Adrenaline Rush (8s)"
     */
    private List<String> getActiveAbilityBuffs(UUID playerId) {
        List<String> buffs = new ArrayList<>();

        if (timedBuffManager == null) {
            return buffs;
        }

        var activeBuffs = timedBuffManager.getActiveBuffsForDisplay(playerId);
        for (var buff : activeBuffs) {
            String timeStr = buff.remainingSeconds() >= 1.0f
                ? String.format("%.0fs", buff.remainingSeconds())
                : String.format("%.1fs", buff.remainingSeconds());
            buffs.add(String.format("[!] %s (%s)", buff.type().getDisplayName(), timeStr));
        }

        // Limit to max ability buffs
        if (buffs.size() > MAX_ABILITY_BUFFS) {
            buffs = new ArrayList<>(buffs.subList(0, MAX_ABILITY_BUFFS));
        }

        return buffs;
    }

    /**
     * Update the ability buffs display labels (cyan).
     * Also controls container visibility - hidden when empty.
     */
    private void updateAbilityBuffsDisplay(UICommandBuilder builder, List<String> abilityBuffs) {
        for (int i = 1; i <= MAX_ABILITY_BUFFS; i++) {
            String selector = "#AbilityBuff" + i;
            String containerSelector = selector + "Container";

            if (i <= abilityBuffs.size()) {
                builder.set(selector + ".Text", abilityBuffs.get(i - 1));
                builder.set(containerSelector + ".Visible", true);
            } else {
                // Always clear text and hide when not in use
                builder.set(selector + ".Text", "");
                builder.set(containerSelector + ".Visible", false);
            }
        }
    }

    /**
     * Get list of unlocked passive abilities for display (green color).
     * Returns formatted strings like "[*] Battle Hardened"
     * Uses caching - only rebuilds on level-up (via AbilitySystem cache invalidation).
     */
    private List<String> getUnlockedPassiveAbilities(UUID playerId) {
        // Check string cache first
        List<String> cached = passivesStringCache.get(playerId);
        if (cached != null) {
            return cached;
        }

        // Cache miss - rebuild
        List<String> passives = new ArrayList<>();

        if (abilitySystem == null) {
            return passives;
        }

        // Use AbilitySystem's cached unlocked abilities set (O(1) per ability check)
        java.util.Set<AbilityType> unlockedSet = abilitySystem.getUnlockedAbilities(playerId);

        // Iterate using static cached array
        for (AbilityType ability : AbilityType.getAllAbilities()) {
            if (unlockedSet.contains(ability)) {
                AbilityConfig abilityConfig = abilitySystem.getAbilityConfig(ability);

                // Format based on whether it's permanent or chance-based
                if (abilityConfig != null && abilityConfig.isPermanent) {
                    // Permanent abilities: "[*] Ability Name"
                    passives.add("[*] " + ability.getDisplayName());
                } else {
                    // Chance-based abilities: "[>] Ability Name"
                    float chance = abilitySystem.getTriggerChance(playerId, ability);
                    passives.add(String.format("[>] %s (%.0f%%)", ability.getDisplayName(), chance * 100));
                }
            }
        }

        // Limit to max passives to display
        if (passives.size() > MAX_PASSIVES) {
            passives = new ArrayList<>(passives.subList(0, MAX_PASSIVES));
        }

        // Cache the result
        passivesStringCache.put(playerId, passives);

        return passives;
    }

    /**
     * Update the passive abilities display labels (green).
     * Also controls container visibility - hidden when empty.
     */
    private void updatePassivesDisplay(UICommandBuilder builder, List<String> passives) {
        for (int i = 1; i <= MAX_PASSIVES; i++) {
            String selector = "#Passive" + i;
            String containerSelector = selector + "Container";

            if (i <= passives.size()) {
                builder.set(selector + ".Text", passives.get(i - 1));
                builder.set(containerSelector + ".Visible", true);
            } else {
                // Always clear text and hide when not in use
                builder.set(selector + ".Text", "");
                builder.set(containerSelector + ".Visible", false);
            }
        }
    }
}
