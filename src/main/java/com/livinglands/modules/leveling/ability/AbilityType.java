package com.livinglands.modules.leveling.ability;

import com.livinglands.modules.leveling.profession.ProfessionType;

/**
 * Enum defining all passive abilities that can be unlocked through profession leveling.
 * Each ability is tied to a specific profession and has a chance to trigger based on level.
 */
public enum AbilityType {

    // Combat abilities
    CRITICAL_STRIKE(
        "criticalStrike",
        "Critical Strike",
        "Chance to deal 1.5x damage on hit",
        ProfessionType.COMBAT
    ),
    LIFESTEAL(
        "lifesteal",
        "Lifesteal",
        "Chance to restore health equal to 10% of damage dealt",
        ProfessionType.COMBAT
    ),

    // Mining abilities
    DOUBLE_ORE(
        "doubleOre",
        "Double Ore",
        "Chance to get double drops from ores",
        ProfessionType.MINING
    ),
    LUCKY_STRIKE(
        "luckyStrike",
        "Lucky Strike",
        "Chance to find rare gems when mining",
        ProfessionType.MINING
    ),

    // Logging abilities
    EFFICIENT_CHOPPING(
        "efficientChopping",
        "Efficient Chopping",
        "Chance to instantly break an entire tree",
        ProfessionType.LOGGING
    ),
    BARK_COLLECTOR(
        "barkCollector",
        "Bark Collector",
        "Chance to get bonus bark/planks when logging",
        ProfessionType.LOGGING
    ),

    // Building abilities
    MATERIAL_SAVER(
        "materialSaver",
        "Material Saver",
        "Chance to not consume materials when placing blocks",
        ProfessionType.BUILDING
    ),

    // Gathering abilities
    DOUBLE_HARVEST(
        "doubleHarvest",
        "Double Harvest",
        "Chance to double gathered resources",
        ProfessionType.GATHERING
    ),
    RARE_FIND(
        "rareFind",
        "Rare Find",
        "Chance to find rare items while gathering",
        ProfessionType.GATHERING
    );

    private final String configKey;
    private final String displayName;
    private final String description;
    private final ProfessionType profession;

    AbilityType(String configKey, String displayName, String description, ProfessionType profession) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.description = description;
        this.profession = profession;
    }

    /**
     * Get the config key used in JSON configuration.
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Get the display name for UI.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the ability description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the profession this ability belongs to.
     */
    public ProfessionType getProfession() {
        return profession;
    }

    /**
     * Get an ability type by its config key.
     */
    public static AbilityType fromConfigKey(String key) {
        for (AbilityType type : values()) {
            if (type.configKey.equals(key)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Get all abilities for a specific profession.
     */
    public static AbilityType[] getAbilitiesForProfession(ProfessionType profession) {
        return java.util.Arrays.stream(values())
            .filter(a -> a.profession == profession)
            .toArray(AbilityType[]::new);
    }
}
