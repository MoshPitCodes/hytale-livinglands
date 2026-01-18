package com.livinglands.core.config;

import java.util.List;

/**
 * Configuration for consumable items.
 * Separate sections for vanilla and modded items allow easy extension.
 */
public record ConsumablesConfig(
    ConsumableSection foods,
    ConsumableSection drinks,
    ConsumableSection energy,
    CombinedConsumableSection combined
) {
    /**
     * Creates default configuration with vanilla items.
     */
    public static ConsumablesConfig defaultConfig() {
        return new ConsumablesConfig(
            defaultFoods(),
            defaultDrinks(),
            defaultEnergy(),
            defaultCombined()
        );
    }

    /**
     * Section for single-stat consumables (food, drink, or energy).
     */
    public record ConsumableSection(
        List<ConsumableEntry> vanilla,
        List<ConsumableEntry> modded
    ) {
        public static ConsumableSection empty() {
            return new ConsumableSection(List.of(), List.of());
        }
    }

    /**
     * Section for combined consumables that restore multiple stats.
     */
    public record CombinedConsumableSection(
        List<CombinedConsumableEntry> vanilla,
        List<CombinedConsumableEntry> modded
    ) {
        public static CombinedConsumableSection empty() {
            return new CombinedConsumableSection(List.of(), List.of());
        }
    }

    /**
     * Entry for a single-stat consumable item.
     */
    public record ConsumableEntry(
        String itemId,
        double restoreAmount
    ) {}

    /**
     * Entry for a combined consumable that can restore multiple stats.
     */
    public record CombinedConsumableEntry(
        String itemId,
        double hunger,
        double thirst,
        double energy
    ) {}

    // ========================================
    // DEFAULT VANILLA FOODS (values reduced by 30%)
    // ========================================
    private static ConsumableSection defaultFoods() {
        return new ConsumableSection(
            List.of(
                // Basic Cooked Foods
                new ConsumableEntry("Food_Bread", 21),
                new ConsumableEntry("Food_Cheese", 14),
                new ConsumableEntry("Food_Egg", 10),
                new ConsumableEntry("Food_Popcorn", 7),
                new ConsumableEntry("Food_Candy_Cane", 6),

                // Raw Meats (reduced by 70% - risk of food poisoning)
                new ConsumableEntry("Food_Beef_Raw", 3),
                new ConsumableEntry("Food_Chicken_Raw", 2),
                new ConsumableEntry("Food_Pork_Raw", 3),
                new ConsumableEntry("Food_Fish_Raw", 2),
                new ConsumableEntry("Food_Fish_Raw_Uncommon", 3),
                new ConsumableEntry("Food_Fish_Raw_Rare", 4),
                new ConsumableEntry("Food_Fish_Raw_Epic", 5),
                new ConsumableEntry("Food_Fish_Raw_Legendary", 6),
                new ConsumableEntry("Food_Wildmeat_Raw", 3),

                // Cooked Meats
                new ConsumableEntry("Food_Wildmeat_Cooked", 32),
                new ConsumableEntry("Food_Fish_Grilled", 28),
                new ConsumableEntry("Food_Vegetable_Cooked", 21),

                // Kebabs
                new ConsumableEntry("Food_Kebab_Meat", 35),
                new ConsumableEntry("Food_Kebab_Fruit", 25),
                new ConsumableEntry("Food_Kebab_Mushroom", 28),
                new ConsumableEntry("Food_Kebab_Vegetable", 28),

                // Pies
                new ConsumableEntry("Food_Pie_Apple", 39),
                new ConsumableEntry("Food_Pie_Meat", 46),
                new ConsumableEntry("Food_Pie_Pumpkin", 35),

                // Crops
                new ConsumableEntry("Plant_Crop_Carrot_Item", 13),
                new ConsumableEntry("Plant_Crop_Potato_Item", 10),
                new ConsumableEntry("Plant_Crop_Corn_Item", 13),
                new ConsumableEntry("Plant_Crop_Tomato_Item", 8),
                new ConsumableEntry("Plant_Crop_Lettuce_Item", 7),
                new ConsumableEntry("Plant_Crop_Onion_Item", 6),
                new ConsumableEntry("Plant_Crop_Pumpkin_Item", 14),
                new ConsumableEntry("Plant_Crop_Aubergine_Item", 10),
                new ConsumableEntry("Plant_Crop_Cauliflower_Item", 10),
                new ConsumableEntry("Plant_Crop_Chilli_Item", 6),
                new ConsumableEntry("Plant_Crop_Rice_Item", 8),
                new ConsumableEntry("Plant_Crop_Turnip_Item", 10),

                // Fruits
                new ConsumableEntry("Plant_Fruit_Berries_Red", 8),
                new ConsumableEntry("Plant_Fruit_Berries_Blue", 8),
                new ConsumableEntry("Plant_Fruit_Berries_Yellow", 8),
                new ConsumableEntry("Plant_Fruit_Azure", 10),
                new ConsumableEntry("Plant_Fruit_Apple", 13),
                new ConsumableEntry("Plant_Fruit_Orange", 11),
                new ConsumableEntry("Plant_Fruit_Banana", 11),
                new ConsumableEntry("Plant_Fruit_Pear", 11),
                new ConsumableEntry("Plant_Fruit_Cherry", 7),
                new ConsumableEntry("Plant_Fruit_Grape", 8),
                new ConsumableEntry("Plant_Fruit_Melon", 14),
                new ConsumableEntry("Plant_Fruit_Watermelon", 15),
                new ConsumableEntry("Plant_Fruit_Coconut", 13),
                new ConsumableEntry("Plant_Fruit_Windwillow", 10),
                new ConsumableEntry("Plant_Fruit_Spiral", 10),
                new ConsumableEntry("Plant_Fruit_Pinkberry", 8)
            ),
            List.of() // Empty modded section
        );
    }

    // ========================================
    // DEFAULT VANILLA DRINKS (values reduced by 30%)
    // ========================================
    private static ConsumableSection defaultDrinks() {
        return new ConsumableSection(
            List.of(
                // Health Potions
                new ConsumableEntry("Potion_Health", 21),
                new ConsumableEntry("Potion_Health_Small", 14),
                new ConsumableEntry("Potion_Health_Large", 28),
                new ConsumableEntry("Potion_Health_Lesser", 18),
                new ConsumableEntry("Potion_Health_Greater", 32),

                // Mana Potions
                new ConsumableEntry("Potion_Mana", 21),
                new ConsumableEntry("Potion_Mana_Small", 14),
                new ConsumableEntry("Potion_Mana_Large", 28),

                // Regen Potions (Health/Mana)
                new ConsumableEntry("Potion_Regen_Health", 21),
                new ConsumableEntry("Potion_Regen_Health_Small", 14),
                new ConsumableEntry("Potion_Regen_Health_Large", 28),
                new ConsumableEntry("Potion_Regen_Mana", 21),
                new ConsumableEntry("Potion_Regen_Mana_Small", 14),
                new ConsumableEntry("Potion_Regen_Mana_Large", 28),

                // Other Potions (non-poisonous)
                new ConsumableEntry("Potion_Antidote", 18),
                new ConsumableEntry("Potion_Purify", 21),
                // NOTE: Poison potions removed - handled by PoisonConfig instead

                // Morph Potions
                new ConsumableEntry("Potion_Morph_Dog", 14),
                new ConsumableEntry("Potion_Morph_Frog", 14),
                new ConsumableEntry("Potion_Morph_Mouse", 14),
                new ConsumableEntry("Potion_Morph_Pigeon", 14),

                // Signature Potions
                new ConsumableEntry("Potion_Signature_Lesser", 18),
                new ConsumableEntry("Potion_Signature_Greater", 25),

                // Water containers
                new ConsumableEntry("Container_Bucket_Filled_Water", 42),
                new ConsumableEntry("*Container_Bucket_State_Filled_Water", 42),
                new ConsumableEntry("*Deco_Bucket_State_Filled_Water", 42),
                new ConsumableEntry("Bucket_Water", 42)
            ),
            List.of() // Empty modded section
        );
    }

    // ========================================
    // DEFAULT VANILLA ENERGY ITEMS
    // ========================================
    private static ConsumableSection defaultEnergy() {
        // No vanilla items restore only energy (stamina potions are combined)
        return new ConsumableSection(
            List.of(),
            List.of() // Empty modded section
        );
    }

    // ========================================
    // DEFAULT VANILLA COMBINED ITEMS (values reduced by 30%)
    // ========================================
    private static CombinedConsumableSection defaultCombined() {
        return new CombinedConsumableSection(
            List.of(
                // Salads (hunger + thirst)
                new CombinedConsumableEntry("Food_Salad_Berry", 18, 10, 0),
                new CombinedConsumableEntry("Food_Salad_Caesar", 25, 7, 0),
                new CombinedConsumableEntry("Food_Salad_Mushroom", 21, 7, 0),

                // Stamina Potions (thirst + energy)
                new CombinedConsumableEntry("Potion_Stamina", 0, 25, 18),
                new CombinedConsumableEntry("Potion_Stamina_Small", 0, 18, 10),
                new CombinedConsumableEntry("Potion_Stamina_Large", 0, 32, 25),
                new CombinedConsumableEntry("Potion_Stamina_Lesser", 0, 21, 14),
                new CombinedConsumableEntry("Potion_Stamina_Greater", 0, 35, 28),

                // Stamina Regen Potions
                new CombinedConsumableEntry("Potion_Regen_Stamina", 0, 21, 14),
                new CombinedConsumableEntry("Potion_Regen_Stamina_Small", 0, 14, 8),
                new CombinedConsumableEntry("Potion_Regen_Stamina_Large", 0, 28, 21),

                // Milk (hunger + thirst)
                new CombinedConsumableEntry("Container_Bucket_Filled_Milk", 14, 35, 0),
                new CombinedConsumableEntry("*Container_Bucket_State_Filled_Milk", 14, 35, 0),
                new CombinedConsumableEntry("*Deco_Bucket_State_Filled_Milk", 14, 35, 0),
                new CombinedConsumableEntry("Bucket_Milk", 14, 35, 0)
            ),
            List.of() // Empty modded section
        );
    }
}
