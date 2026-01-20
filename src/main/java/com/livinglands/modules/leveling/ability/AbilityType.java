package com.livinglands.modules.leveling.ability;

import com.livinglands.modules.leveling.profession.ProfessionType;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Enum defining all passive abilities that can be unlocked through profession leveling.
 * Each profession has 3 abilities at tiers: Level 15, Level 35, and Level 60.
 *
 * Tier 1 (Lv.15): XP boosters - chance to gain bonus XP
 * Tier 2 (Lv.35): Resource management - restore stats or pause depletion
 * Tier 3 (Lv.60): Permanent passives - always-active stat bonuses
 */
public enum AbilityType {

    // ========== Combat Abilities ==========

    /** Tier 1: After a kill, gain +20% speed for 10 seconds */
    ADRENALINE_RUSH(
        "adrenalineRush",
        "Adrenaline Rush",
        "After a kill, gain +20% movement speed for 10 seconds",
        ProfessionType.COMBAT,
        AbilityTier.TIER_1
    ),

    /** Tier 2: After a kill, restore 15% of max health */
    WARRIORS_RESILIENCE(
        "warriorsResilience",
        "Warrior's Resilience",
        "After a kill, restore 15% of max health",
        ProfessionType.COMBAT,
        AbilityTier.TIER_2
    ),

    /** Tier 3: Permanent +10% max health */
    BATTLE_HARDENED(
        "battleHardened",
        "Battle Hardened",
        "Permanently increases max health by 10%",
        ProfessionType.COMBAT,
        AbilityTier.TIER_3
    ),

    // ========== Mining Abilities ==========

    /** Tier 1: Mining ores awards +50% XP */
    PROSPECTORS_EYE(
        "prospectorsEye",
        "Prospector's Eye",
        "Mining ores awards +50% bonus XP",
        ProfessionType.MINING,
        AbilityTier.TIER_1
    ),

    /** Tier 2: Mining doesn't deplete hunger for 30 seconds */
    EFFICIENT_EXTRACTION(
        "efficientExtraction",
        "Efficient Extraction",
        "Mining pauses hunger depletion for 30 seconds",
        ProfessionType.MINING,
        AbilityTier.TIER_2
    ),

    /** Tier 3: Permanent +15% max stamina */
    IRON_CONSTITUTION(
        "ironConstitution",
        "Iron Constitution",
        "Permanently increases max stamina by 15%",
        ProfessionType.MINING,
        AbilityTier.TIER_3
    ),

    // ========== Logging Abilities ==========

    /** Tier 1: Chopping wood awards +50% XP */
    LUMBERJACKS_VIGOR(
        "lumberjacksVigor",
        "Lumberjack's Vigor",
        "Chopping wood awards +50% bonus XP",
        ProfessionType.LOGGING,
        AbilityTier.TIER_1
    ),

    /** Tier 2: Chopping wood restores 5 energy */
    FORESTS_BLESSING(
        "forestsBlessing",
        "Forest's Blessing",
        "Chopping wood restores 5 energy",
        ProfessionType.LOGGING,
        AbilityTier.TIER_2
    ),

    /** Tier 3: Permanent +10% movement speed */
    NATURES_ENDURANCE(
        "naturesEndurance",
        "Nature's Endurance",
        "Permanently increases movement speed by 10%",
        ProfessionType.LOGGING,
        AbilityTier.TIER_3
    ),

    // ========== Building Abilities ==========

    /** Tier 1: Building awards +100% XP (double) */
    ARCHITECTS_FOCUS(
        "architectsFocus",
        "Architect's Focus",
        "Building awards +100% bonus XP (double)",
        ProfessionType.BUILDING,
        AbilityTier.TIER_1
    ),

    /** Tier 2: Building doesn't deplete stamina for 30 seconds */
    STEADY_HANDS(
        "steadyHands",
        "Steady Hands",
        "Building pauses stamina depletion for 30 seconds",
        ProfessionType.BUILDING,
        AbilityTier.TIER_2
    ),

    /** Tier 3: Permanent +10% max stamina */
    MASTER_BUILDER(
        "masterBuilder",
        "Master Builder",
        "Permanently increases max stamina by 10%",
        ProfessionType.BUILDING,
        AbilityTier.TIER_3
    ),

    // ========== Gathering Abilities ==========

    /** Tier 1: Gathering awards +50% XP */
    FORAGERS_INTUITION(
        "foragersIntuition",
        "Forager's Intuition",
        "Gathering awards +50% bonus XP",
        ProfessionType.GATHERING,
        AbilityTier.TIER_1
    ),

    /** Tier 2: Gathering restores 3 hunger and 3 thirst */
    NATURES_GIFT(
        "naturesGift",
        "Nature's Gift",
        "Gathering restores 3 hunger and 3 thirst",
        ProfessionType.GATHERING,
        AbilityTier.TIER_2
    ),

    /** Tier 3: Permanent -15% hunger/thirst depletion rate */
    SURVIVALIST(
        "survivalist",
        "Survivalist",
        "Permanently reduces hunger and thirst depletion by 15%",
        ProfessionType.GATHERING,
        AbilityTier.TIER_3
    );

    // ========== Static Cached Arrays (avoid repeated values() calls) ==========

    /** All abilities - cached to avoid allocation on every values() call */
    private static final AbilityType[] ALL_ABILITIES = values();

    /** Count of all abilities */
    public static final int ABILITY_COUNT = ALL_ABILITIES.length;

    /** Tier 1 abilities only (Level 15 XP boosters) */
    private static final AbilityType[] TIER_1_ABILITIES = Arrays.stream(ALL_ABILITIES)
        .filter(a -> a.tier == AbilityTier.TIER_1)
        .toArray(AbilityType[]::new);

    /** Tier 2 abilities only (Level 35 resource management) */
    private static final AbilityType[] TIER_2_ABILITIES = Arrays.stream(ALL_ABILITIES)
        .filter(a -> a.tier == AbilityTier.TIER_2)
        .toArray(AbilityType[]::new);

    /** Tier 3 abilities only (Level 60 permanent passives) */
    private static final AbilityType[] TIER_3_ABILITIES = Arrays.stream(ALL_ABILITIES)
        .filter(a -> a.tier == AbilityTier.TIER_3)
        .toArray(AbilityType[]::new);

    /** Lookup map for profession+tier -> ability (O(1) lookup) */
    private static final Map<ProfessionType, Map<AbilityTier, AbilityType>> PROFESSION_TIER_LOOKUP;

    static {
        // Build the profession+tier lookup map
        PROFESSION_TIER_LOOKUP = new EnumMap<>(ProfessionType.class);
        for (ProfessionType prof : ProfessionType.values()) {
            PROFESSION_TIER_LOOKUP.put(prof, new EnumMap<>(AbilityTier.class));
        }
        for (AbilityType ability : ALL_ABILITIES) {
            PROFESSION_TIER_LOOKUP.get(ability.profession).put(ability.tier, ability);
        }
    }

    private final String configKey;
    private final String displayName;
    private final String description;
    private final ProfessionType profession;
    private final AbilityTier tier;

    AbilityType(String configKey, String displayName, String description,
                ProfessionType profession, AbilityTier tier) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.description = description;
        this.profession = profession;
        this.tier = tier;
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
     * Get the tier of this ability.
     */
    public AbilityTier getTier() {
        return tier;
    }

    /**
     * Check if this is a permanent (always-active) ability.
     */
    public boolean isPermanent() {
        return tier == AbilityTier.TIER_3;
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
     * Get all abilities as a cached array (avoids values() allocation).
     * Do NOT modify the returned array.
     */
    public static AbilityType[] getAllAbilities() {
        return ALL_ABILITIES;
    }

    /**
     * Get all Tier 1 abilities (Level 15 XP boosters).
     * Do NOT modify the returned array.
     */
    public static AbilityType[] getTier1Abilities() {
        return TIER_1_ABILITIES;
    }

    /**
     * Get all Tier 2 abilities (Level 35 resource management).
     * Do NOT modify the returned array.
     */
    public static AbilityType[] getTier2Abilities() {
        return TIER_2_ABILITIES;
    }

    /**
     * Get all Tier 3 abilities (Level 60 permanent passives).
     * Do NOT modify the returned array.
     */
    public static AbilityType[] getTier3Abilities() {
        return TIER_3_ABILITIES;
    }

    /**
     * Get all abilities for a specific profession.
     */
    public static AbilityType[] getAbilitiesForProfession(ProfessionType profession) {
        return Arrays.stream(ALL_ABILITIES)
            .filter(a -> a.profession == profession)
            .toArray(AbilityType[]::new);
    }

    /**
     * Get all abilities for a specific tier.
     * Prefer getTier1Abilities(), getTier2Abilities(), getTier3Abilities() for hot paths.
     */
    public static AbilityType[] getAbilitiesForTier(AbilityTier tier) {
        return switch (tier) {
            case TIER_1 -> TIER_1_ABILITIES;
            case TIER_2 -> TIER_2_ABILITIES;
            case TIER_3 -> TIER_3_ABILITIES;
        };
    }

    /**
     * Get the ability for a profession at a specific tier.
     * O(1) lookup using cached map.
     */
    public static AbilityType getAbilityForProfessionAndTier(ProfessionType profession, AbilityTier tier) {
        var tierMap = PROFESSION_TIER_LOOKUP.get(profession);
        return tierMap != null ? tierMap.get(tier) : null;
    }

    /**
     * Ability tier enum - defines unlock levels and ability types.
     */
    public enum AbilityTier {
        /** Level 15 - XP boosters and timed buffs */
        TIER_1(15, false),
        /** Level 35 - Resource management abilities */
        TIER_2(35, false),
        /** Level 60 - Permanent passive bonuses */
        TIER_3(60, true);

        private final int defaultUnlockLevel;
        private final boolean permanent;

        AbilityTier(int defaultUnlockLevel, boolean permanent) {
            this.defaultUnlockLevel = defaultUnlockLevel;
            this.permanent = permanent;
        }

        public int getDefaultUnlockLevel() {
            return defaultUnlockLevel;
        }

        public boolean isPermanent() {
            return permanent;
        }
    }
}
