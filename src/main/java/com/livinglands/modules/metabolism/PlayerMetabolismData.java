package com.livinglands.modules.metabolism;

import java.util.UUID;

/**
 * Data class holding metabolism state for a player.
 * Uses Java 25 features for clean data management.
 */
public final class PlayerMetabolismData {

    /**
     * Combat timeout in milliseconds.
     * Player is considered "in combat" for this duration after taking damage.
     */
    public static final long COMBAT_TIMEOUT_MS = 10_000L; // 10 seconds

    private final UUID playerUuid;

    // Current stat values
    private double hunger;
    private double thirst;
    private double energy;

    // Last time stats were depleted (server time in ms)
    private long lastHungerDepletion;
    private long lastThirstDepletion;
    private long lastEnergyDepletion;

    // Current activity multiplier
    private double currentActivityMultiplier = 1.0;

    // Activity state tracking
    private boolean isSprinting = false;
    private boolean isSwimming = false;
    private boolean isInCombat = false;
    private long lastCombatTime = 0L;

    // Stat tracking for analytics
    private double totalHungerDepleted = 0.0;
    private double totalThirstDepleted = 0.0;
    private double totalEnergyDepleted = 0.0;
    private double totalHungerRestored = 0.0;
    private double totalThirstRestored = 0.0;
    private double totalEnergyRestored = 0.0;

    /**
     * Creates new metabolism data for a player.
     *
     * @param playerUuid The player's UUID
     * @param initialHunger Initial hunger value
     * @param initialThirst Initial thirst value
     */
    public PlayerMetabolismData(UUID playerUuid, double initialHunger, double initialThirst) {
        this(playerUuid, initialHunger, initialThirst, EnergyStat.DEFAULT_VALUE);
    }

    /**
     * Creates new metabolism data for a player with energy.
     *
     * @param playerUuid The player's UUID
     * @param initialHunger Initial hunger value
     * @param initialThirst Initial thirst value
     * @param initialEnergy Initial energy value
     */
    public PlayerMetabolismData(UUID playerUuid, double initialHunger, double initialThirst, double initialEnergy) {
        this.playerUuid = playerUuid;
        this.hunger = HungerStat.clamp(initialHunger);
        this.thirst = ThirstStat.clamp(initialThirst);
        this.energy = EnergyStat.clamp(initialEnergy);

        var currentTime = System.currentTimeMillis();
        this.lastHungerDepletion = currentTime;
        this.lastThirstDepletion = currentTime;
        this.lastEnergyDepletion = currentTime;
    }

    // Getters
    public UUID getPlayerUuid() { return playerUuid; }
    public double getHunger() { return hunger; }
    public double getThirst() { return thirst; }
    public double getEnergy() { return energy; }
    public long getLastHungerDepletion() { return lastHungerDepletion; }
    public long getLastThirstDepletion() { return lastThirstDepletion; }
    public long getLastEnergyDepletion() { return lastEnergyDepletion; }
    public double getCurrentActivityMultiplier() { return currentActivityMultiplier; }
    public boolean isSprinting() { return isSprinting; }
    public boolean isSwimming() { return isSwimming; }
    public boolean isInCombat() { return isInCombat; }
    public double getTotalHungerDepleted() { return totalHungerDepleted; }
    public double getTotalThirstDepleted() { return totalThirstDepleted; }
    public double getTotalEnergyDepleted() { return totalEnergyDepleted; }
    public double getTotalHungerRestored() { return totalHungerRestored; }
    public double getTotalThirstRestored() { return totalThirstRestored; }
    public double getTotalEnergyRestored() { return totalEnergyRestored; }

    // Setters with validation
    public void setHunger(double value) {
        this.hunger = HungerStat.clamp(value);
    }

    public void setThirst(double value) {
        this.thirst = ThirstStat.clamp(value);
    }

    public void setEnergy(double value) {
        this.energy = EnergyStat.clamp(value);
    }

    public void setLastHungerDepletion(long time) {
        this.lastHungerDepletion = time;
    }

    public void setLastThirstDepletion(long time) {
        this.lastThirstDepletion = time;
    }

    public void setLastEnergyDepletion(long time) {
        this.lastEnergyDepletion = time;
    }

    public void addHungerDepleted(double amount) {
        this.totalHungerDepleted += amount;
    }

    public void addThirstDepleted(double amount) {
        this.totalThirstDepleted += amount;
    }

    public void addEnergyDepleted(double amount) {
        this.totalEnergyDepleted += amount;
    }

    /**
     * Restores hunger by the specified amount.
     * The value is clamped to the maximum (100).
     *
     * @param amount Amount of hunger to restore
     * @return The actual amount restored (may be less if already near max)
     */
    public double restoreHunger(double amount) {
        double oldHunger = this.hunger;
        this.hunger = HungerStat.clamp(this.hunger + amount);
        double restored = this.hunger - oldHunger;
        this.totalHungerRestored += restored;
        return restored;
    }

    /**
     * Restores thirst by the specified amount.
     * The value is clamped to the maximum (100).
     *
     * @param amount Amount of thirst to restore
     * @return The actual amount restored (may be less if already near max)
     */
    public double restoreThirst(double amount) {
        double oldThirst = this.thirst;
        this.thirst = ThirstStat.clamp(this.thirst + amount);
        double restored = this.thirst - oldThirst;
        this.totalThirstRestored += restored;
        return restored;
    }

    /**
     * Restores energy by the specified amount.
     * The value is clamped to the maximum (100).
     *
     * @param amount Amount of energy to restore
     * @return The actual amount restored (may be less if already near max)
     */
    public double restoreEnergy(double amount) {
        double oldEnergy = this.energy;
        this.energy = EnergyStat.clamp(this.energy + amount);
        double restored = this.energy - oldEnergy;
        this.totalEnergyRestored += restored;
        return restored;
    }

    /**
     * Updates activity state based on player actions.
     *
     * @param sprinting Whether player is sprinting
     * @param swimming Whether player is swimming
     * @param currentTime Current server time in milliseconds
     */
    public void updateActivity(boolean sprinting, boolean swimming, long currentTime) {
        this.isSprinting = sprinting;
        this.isSwimming = swimming;

        // Update combat state based on timeout
        if (currentTime - lastCombatTime > COMBAT_TIMEOUT_MS) {
            this.isInCombat = false;
        }
    }

    /**
     * Marks player as in combat (called when taking damage).
     *
     * @param currentTime Current server time in milliseconds
     */
    public void enterCombat(long currentTime) {
        this.isInCombat = true;
        this.lastCombatTime = currentTime;
    }

    /**
     * Calculates current activity multiplier based on player state.
     * Uses Java 25 switch expression.
     *
     * @param idleMultiplier Multiplier when idle
     * @param walkingMultiplier Multiplier when walking
     * @param sprintingMultiplier Multiplier when sprinting
     * @param swimmingMultiplier Multiplier when swimming
     * @param combatMultiplier Multiplier when in combat
     * @return The calculated activity multiplier
     */
    public double calculateActivityMultiplier(
        double idleMultiplier,
        double walkingMultiplier,
        double sprintingMultiplier,
        double swimmingMultiplier,
        double combatMultiplier
    ) {
        // Determine multiplier based on activity state (priority order)
        if (isInCombat) {
            this.currentActivityMultiplier = combatMultiplier;
        } else if (isSprinting) {
            this.currentActivityMultiplier = sprintingMultiplier;
        } else if (isSwimming) {
            this.currentActivityMultiplier = swimmingMultiplier;
        } else {
            this.currentActivityMultiplier = walkingMultiplier;
        }

        return this.currentActivityMultiplier;
    }

    /**
     * Resets all tracking data (used on player respawn).
     *
     * @param currentTime Current server time in milliseconds
     * @param initialHunger Initial hunger value after reset
     * @param initialThirst Initial thirst value after reset
     */
    public void reset(long currentTime, double initialHunger, double initialThirst) {
        reset(currentTime, initialHunger, initialThirst, EnergyStat.DEFAULT_VALUE);
    }

    /**
     * Resets all tracking data including energy (used on player respawn).
     *
     * @param currentTime Current server time in milliseconds
     * @param initialHunger Initial hunger value after reset
     * @param initialThirst Initial thirst value after reset
     * @param initialEnergy Initial energy value after reset
     */
    public void reset(long currentTime, double initialHunger, double initialThirst, double initialEnergy) {
        this.hunger = HungerStat.clamp(initialHunger);
        this.thirst = ThirstStat.clamp(initialThirst);
        this.energy = EnergyStat.clamp(initialEnergy);
        this.lastHungerDepletion = currentTime;
        this.lastThirstDepletion = currentTime;
        this.lastEnergyDepletion = currentTime;
        this.currentActivityMultiplier = 1.0;
        this.isSprinting = false;
        this.isSwimming = false;
        this.isInCombat = false;
        this.lastCombatTime = 0L;
    }

    @Override
    public String toString() {
        return "PlayerMetabolismData[uuid=%s, hunger=%.1f, thirst=%.1f, energy=%.1f, activity=%.2f]"
            .formatted(playerUuid, hunger, thirst, energy, currentActivityMultiplier);
    }
}
