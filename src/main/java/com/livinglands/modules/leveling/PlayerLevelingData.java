package com.livinglands.modules.leveling;

import com.livinglands.modules.leveling.profession.ProfessionData;
import com.livinglands.modules.leveling.profession.ProfessionType;
import com.livinglands.modules.leveling.profession.XpCalculator;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Player-specific leveling data containing all profession levels and XP.
 * This is the main data object that gets persisted per player.
 */
public class PlayerLevelingData {

    private UUID playerId;
    private Map<String, ProfessionData> professions;
    private boolean hudEnabled;
    private long lastSaveTime;
    private long totalXpEarned;

    // Transient - not serialized
    private transient boolean dirty;

    public PlayerLevelingData() {
        this.professions = new java.util.HashMap<>();
        this.hudEnabled = true;
        this.lastSaveTime = 0;
        this.totalXpEarned = 0;
        this.dirty = false;
    }

    /**
     * Create new player data with initial values for all professions.
     */
    public static PlayerLevelingData createNew(UUID playerId, XpCalculator xpCalculator) {
        var data = new PlayerLevelingData();
        data.playerId = playerId;
        data.hudEnabled = true;
        data.lastSaveTime = System.currentTimeMillis();
        data.totalXpEarned = 0;

        // Initialize all professions at level 1
        for (var type : ProfessionType.values()) {
            long xpForLevel2 = xpCalculator.getXpToNextLevel(1);
            data.professions.put(type.name(), ProfessionData.initial(xpForLevel2));
        }

        return data;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    /**
     * Get profession data by type.
     */
    public ProfessionData getProfession(ProfessionType type) {
        return professions.get(type.name());
    }

    /**
     * Set profession data for a type.
     */
    public void setProfession(ProfessionType type, ProfessionData data) {
        professions.put(type.name(), data);
        markDirty();
    }

    /**
     * Get all profession data.
     */
    public Map<ProfessionType, ProfessionData> getAllProfessions() {
        var result = new EnumMap<ProfessionType, ProfessionData>(ProfessionType.class);
        for (var type : ProfessionType.values()) {
            var data = professions.get(type.name());
            if (data != null) {
                result.put(type, data);
            }
        }
        return result;
    }

    /**
     * Get the level of a specific profession.
     */
    public int getLevel(ProfessionType type) {
        var data = getProfession(type);
        return data != null ? data.getLevel() : 1;
    }

    /**
     * Get the total level across all professions.
     */
    public int getTotalLevel() {
        int total = 0;
        for (var type : ProfessionType.values()) {
            total += getLevel(type);
        }
        return total;
    }

    /**
     * Get the highest profession level.
     */
    public int getHighestLevel() {
        int highest = 1;
        for (var type : ProfessionType.values()) {
            highest = Math.max(highest, getLevel(type));
        }
        return highest;
    }

    public boolean isHudEnabled() {
        return hudEnabled;
    }

    public void setHudEnabled(boolean hudEnabled) {
        this.hudEnabled = hudEnabled;
        markDirty();
    }

    public void toggleHud() {
        this.hudEnabled = !this.hudEnabled;
        markDirty();
    }

    public long getLastSaveTime() {
        return lastSaveTime;
    }

    public void setLastSaveTime(long lastSaveTime) {
        this.lastSaveTime = lastSaveTime;
    }

    public long getTotalXpEarned() {
        return totalXpEarned;
    }

    public void addTotalXpEarned(long xp) {
        this.totalXpEarned += xp;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    /**
     * Validate and repair data after loading from disk.
     */
    public void validateAndRepair(XpCalculator xpCalculator) {
        if (professions == null) {
            professions = new java.util.HashMap<>();
        }

        // Ensure all profession types exist
        for (var type : ProfessionType.values()) {
            if (!professions.containsKey(type.name())) {
                long xpForLevel2 = xpCalculator.getXpToNextLevel(1);
                professions.put(type.name(), ProfessionData.initial(xpForLevel2));
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PlayerLevelingData{playerId=").append(playerId);
        sb.append(", totalLevel=").append(getTotalLevel());
        sb.append(", professions=[");
        for (var type : ProfessionType.values()) {
            var data = getProfession(type);
            if (data != null) {
                sb.append(type.getShortCode()).append(":").append(data.getLevel()).append(" ");
            }
        }
        sb.append("]}");
        return sb.toString();
    }
}
