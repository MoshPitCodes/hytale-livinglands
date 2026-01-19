package com.livinglands.modules.leveling.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for XP values awarded from various activities.
 * Supports pattern-based matching for block/mob names.
 */
public class XpSourceConfig {

    // Combat XP
    public Map<String, Integer> mobXpValues = new HashMap<>();
    public int damageXpPerPoint = 1;
    public int defaultMobKillXp = 25;

    // Mining XP
    public Map<String, Integer> oreXpValues = new HashMap<>();
    public int defaultOreXp = 15;
    public int defaultBlockXp = 1;

    // Building XP
    public int blockPlaceXp = 1;

    // Logging XP
    public Map<String, Integer> woodXpValues = new HashMap<>();
    public int defaultWoodXp = 10;

    // Gathering XP
    public Map<String, Integer> gatherXpValues = new HashMap<>();
    public int defaultPickupXp = 2;

    public XpSourceConfig() {}

    /**
     * Create default XP source configuration with sensible values.
     */
    public static XpSourceConfig defaults() {
        var config = new XpSourceConfig();

        // Combat - Mob XP values (using partial name matching)
        config.mobXpValues.put("Slime", 15);
        config.mobXpValues.put("Frog", 10);
        config.mobXpValues.put("Chicken", 5);
        config.mobXpValues.put("Pig", 10);
        config.mobXpValues.put("Cow", 15);
        config.mobXpValues.put("Sheep", 10);
        config.mobXpValues.put("Wolf", 30);
        config.mobXpValues.put("Bear", 50);
        config.mobXpValues.put("Zombie", 40);
        config.mobXpValues.put("Skeleton", 45);
        config.mobXpValues.put("Spider", 35);
        config.mobXpValues.put("Creeper", 50);
        config.mobXpValues.put("Void", 80);
        config.mobXpValues.put("Boss", 500);
        config.mobXpValues.put("Dragon", 1000);

        // Mining - Ore XP values
        config.oreXpValues.put("Coal_Ore", 10);
        config.oreXpValues.put("Copper_Ore", 15);
        config.oreXpValues.put("Iron_Ore", 25);
        config.oreXpValues.put("Silver_Ore", 35);
        config.oreXpValues.put("Gold_Ore", 45);
        config.oreXpValues.put("Cobalt_Ore", 60);
        config.oreXpValues.put("Mythril_Ore", 80);
        config.oreXpValues.put("Diamond_Ore", 100);

        // Logging - Wood XP values
        config.woodXpValues.put("Oak_Log", 10);
        config.woodXpValues.put("Birch_Log", 10);
        config.woodXpValues.put("Spruce_Log", 12);
        config.woodXpValues.put("Pine_Log", 12);
        config.woodXpValues.put("Jungle_Log", 15);
        config.woodXpValues.put("Dark_Oak_Log", 15);
        config.woodXpValues.put("Acacia_Log", 15);

        // Gathering - Item XP values
        config.gatherXpValues.put("Berry", 5);
        config.gatherXpValues.put("Apple", 8);
        config.gatherXpValues.put("Herb", 10);
        config.gatherXpValues.put("Mushroom", 8);
        config.gatherXpValues.put("Flower", 3);
        config.gatherXpValues.put("Seed", 2);
        config.gatherXpValues.put("Fiber", 5);
        config.gatherXpValues.put("Essence", 25);

        return config;
    }

    /**
     * Get XP for killing a mob by name (supports partial matching).
     */
    public int getMobXp(String mobName) {
        if (mobName == null) return defaultMobKillXp;

        // Try exact match first
        if (mobXpValues.containsKey(mobName)) {
            return mobXpValues.get(mobName);
        }

        // Try partial matching
        String lowerName = mobName.toLowerCase();
        for (var entry : mobXpValues.entrySet()) {
            if (lowerName.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        return defaultMobKillXp;
    }

    /**
     * Get XP for mining a block by ID (supports partial matching).
     */
    public int getOreXp(String blockId) {
        if (blockId == null) return defaultBlockXp;

        // Check if it's an ore
        if (!blockId.toLowerCase().contains("ore")) {
            return defaultBlockXp;
        }

        // Try exact match first
        if (oreXpValues.containsKey(blockId)) {
            return oreXpValues.get(blockId);
        }

        // Try partial matching
        for (var entry : oreXpValues.entrySet()) {
            if (blockId.contains(entry.getKey()) ||
                entry.getKey().contains(blockId)) {
                return entry.getValue();
            }
        }

        return defaultOreXp;
    }

    /**
     * Get XP for chopping wood by block ID.
     */
    public int getWoodXp(String blockId) {
        if (blockId == null) return defaultWoodXp;

        // Try exact match first
        if (woodXpValues.containsKey(blockId)) {
            return woodXpValues.get(blockId);
        }

        // Try partial matching
        for (var entry : woodXpValues.entrySet()) {
            if (blockId.contains(entry.getKey()) ||
                entry.getKey().contains(blockId)) {
                return entry.getValue();
            }
        }

        return defaultWoodXp;
    }

    /**
     * Get XP for picking up an item by ID.
     */
    public int getGatherXp(String itemId) {
        if (itemId == null) return defaultPickupXp;

        // Try exact match first
        if (gatherXpValues.containsKey(itemId)) {
            return gatherXpValues.get(itemId);
        }

        // Try partial matching
        String lowerName = itemId.toLowerCase();
        for (var entry : gatherXpValues.entrySet()) {
            if (lowerName.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        return defaultPickupXp;
    }
}
