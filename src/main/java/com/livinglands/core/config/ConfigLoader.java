package com.livinglands.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Handles loading and saving configuration from JSON files.
 */
public final class ConfigLoader {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create();

    private ConfigLoader() {
        // Utility class
    }

    /**
     * Loads configuration from file, creating default if not exists.
     *
     * @param configPath Path to the config file
     * @param logger Logger for status messages
     * @return Loaded or default configuration
     */
    public static ModConfig loadOrCreate(@Nonnull Path configPath, @Nonnull HytaleLogger logger) {
        try {
            if (Files.exists(configPath)) {
                return load(configPath, logger);
            } else {
                logger.at(Level.INFO).log("Config file not found, creating default: %s", configPath);
                var defaultConfig = ModConfig.defaultConfig();
                save(configPath, defaultConfig, logger);
                return defaultConfig;
            }
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to load config, using defaults");
            return ModConfig.defaultConfig();
        }
    }

    /**
     * Loads configuration from an existing file.
     */
    public static ModConfig load(@Nonnull Path configPath, @Nonnull HytaleLogger logger) throws IOException {
        logger.at(Level.INFO).log("Loading config from: %s", configPath);
        var json = Files.readString(configPath);
        var jsonConfig = GSON.fromJson(json, JsonConfig.class);
        return jsonConfig.toModConfig();
    }

    /**
     * Saves configuration to file.
     */
    public static void save(@Nonnull Path configPath, @Nonnull ModConfig config, @Nonnull HytaleLogger logger) {
        try {
            Files.createDirectories(configPath.getParent());
            var jsonConfig = JsonConfig.fromModConfig(config);
            var json = GSON.toJson(jsonConfig);
            Files.writeString(configPath, json);
            logger.at(Level.INFO).log("Saved config to: %s", configPath);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to save config");
        }
    }

    /**
     * JSON-friendly configuration structure.
     * Uses simple types that Gson can easily serialize.
     */
    public static class JsonConfig {
        public MetabolismSection metabolism = new MetabolismSection();
        public ConsumablesSection consumables = new ConsumablesSection();
        public SleepSection sleep = new SleepSection();
        public DebuffsSection debuffs = new DebuffsSection();

        // ========================================
        // METABOLISM SECTION
        // ========================================
        public static class MetabolismSection {
            public boolean enableHunger = true;
            public boolean enableThirst = true;
            public boolean enableEnergy = true;

            public double hungerDepletionRate = 60.0;
            public double thirstDepletionRate = 45.0;
            public double energyDepletionRate = 90.0;

            public double starvationThreshold = 20.0;
            public double dehydrationThreshold = 20.0;
            public double exhaustionThreshold = 20.0;

            public double initialHunger = 100.0;
            public double initialThirst = 100.0;
            public double initialEnergy = 100.0;

            public ActivitySection activityMultipliers = new ActivitySection();
        }

        public static class ActivitySection {
            public double idle = 1.0;
            public double walking = 1.0;
            public double sprinting = 2.0;
            public double swimming = 1.5;
            public double combat = 1.5;
            public double jumping = 1.2;
        }

        // ========================================
        // CONSUMABLES SECTION
        // ========================================
        public static class ConsumablesSection {
            public ConsumableListSection foods = new ConsumableListSection();
            public ConsumableListSection drinks = new ConsumableListSection();
            public ConsumableListSection energy = new ConsumableListSection();
            public CombinedConsumableListSection combined = new CombinedConsumableListSection();
        }

        public static class ConsumableListSection {
            public List<ConsumableEntryJson> vanilla = new ArrayList<>();
            public List<ConsumableEntryJson> modded = new ArrayList<>();
        }

        public static class CombinedConsumableListSection {
            public List<CombinedConsumableEntryJson> vanilla = new ArrayList<>();
            public List<CombinedConsumableEntryJson> modded = new ArrayList<>();
        }

        public static class ConsumableEntryJson {
            public String itemId;
            public double restoreAmount;

            public ConsumableEntryJson() {}

            public ConsumableEntryJson(String itemId, double restoreAmount) {
                this.itemId = itemId;
                this.restoreAmount = restoreAmount;
            }
        }

        public static class CombinedConsumableEntryJson {
            public String itemId;
            public double hunger;
            public double thirst;
            public double energy;

            public CombinedConsumableEntryJson() {}

            public CombinedConsumableEntryJson(String itemId, double hunger, double thirst, double energy) {
                this.itemId = itemId;
                this.hunger = hunger;
                this.thirst = thirst;
                this.energy = energy;
            }
        }

        // ========================================
        // SLEEP SECTION
        // ========================================
        public static class SleepSection {
            public BedBlocksSection bedBlocks = new BedBlocksSection();
            public double energyRestoreAmount = 50.0;
            public long cooldownMs = 5000;
            public boolean respectSleepSchedule = true;
        }

        public static class BedBlocksSection {
            public List<String> vanilla = new ArrayList<>(List.of("bed"));
            public List<String> modded = new ArrayList<>();
        }

        // ========================================
        // DEBUFFS SECTION
        // ========================================
        public static class DebuffsSection {
            public HungerDebuffsJson hunger = new HungerDebuffsJson();
            public ThirstDebuffsJson thirst = new ThirstDebuffsJson();
            public EnergyDebuffsJson energy = new EnergyDebuffsJson();
        }

        public static class HungerDebuffsJson {
            public boolean enabled = true;
            public double damageStartThreshold = 0.0;
            public double recoveryThreshold = 30.0;
            public float damageTickIntervalSeconds = 3.0f;
            public float initialDamage = 1.0f;
            public float damageIncreasePerTick = 0.5f;
            public float maxDamage = 5.0f;
        }

        public static class ThirstDebuffsJson {
            public boolean enabled = true;
            public double damageStartThreshold = 0.0;
            public double recoveryThreshold = 30.0;
            public double blurStartThreshold = 20.0;
            public float damageTickIntervalSeconds = 4.0f;
            public float damageAtZero = 1.5f;
        }

        public static class EnergyDebuffsJson {
            public boolean enabled = true;
            public double slowStartThreshold = 30.0;
            public float minSpeedMultiplier = 0.6f;
            public float maxStaminaMultiplier = 1.5f;
            public double staminaDrainStartThreshold = 0.0;
            public double staminaDrainRecoveryThreshold = 50.0;
            public float staminaDrainPerTick = 5.0f;
            public float staminaDrainTickIntervalSeconds = 1.0f;
        }

        /**
         * Converts JSON config to ModConfig record.
         */
        public ModConfig toModConfig() {
            var m = metabolism;
            var a = m.activityMultipliers;

            var metabolismConfig = new MetabolismConfig(
                m.hungerDepletionRate,
                m.thirstDepletionRate,
                m.energyDepletionRate,
                m.starvationThreshold,
                m.dehydrationThreshold,
                m.exhaustionThreshold,
                new ActivityMultipliers(
                    a.idle,
                    a.walking,
                    a.sprinting,
                    a.swimming,
                    a.combat,
                    a.jumping
                ),
                m.initialHunger,
                m.initialThirst,
                m.initialEnergy,
                m.enableHunger,
                m.enableThirst,
                m.enableEnergy
            );

            var consumablesConfig = toConsumablesConfig();
            var sleepConfig = toSleepConfig();
            var debuffsConfig = toDebuffsConfig();

            return new ModConfig(metabolismConfig, consumablesConfig, sleepConfig, debuffsConfig);
        }

        private DebuffsConfig toDebuffsConfig() {
            var d = debuffs;
            return new DebuffsConfig(
                new DebuffsConfig.HungerDebuffs(
                    d.hunger.enabled,
                    d.hunger.damageStartThreshold,
                    d.hunger.recoveryThreshold,
                    d.hunger.damageTickIntervalSeconds,
                    d.hunger.initialDamage,
                    d.hunger.damageIncreasePerTick,
                    d.hunger.maxDamage
                ),
                new DebuffsConfig.ThirstDebuffs(
                    d.thirst.enabled,
                    d.thirst.damageStartThreshold,
                    d.thirst.recoveryThreshold,
                    d.thirst.blurStartThreshold,
                    d.thirst.damageTickIntervalSeconds,
                    d.thirst.damageAtZero
                ),
                new DebuffsConfig.EnergyDebuffs(
                    d.energy.enabled,
                    d.energy.slowStartThreshold,
                    d.energy.minSpeedMultiplier,
                    d.energy.maxStaminaMultiplier,
                    d.energy.staminaDrainStartThreshold,
                    d.energy.staminaDrainRecoveryThreshold,
                    d.energy.staminaDrainPerTick,
                    d.energy.staminaDrainTickIntervalSeconds
                )
            );
        }

        private ConsumablesConfig toConsumablesConfig() {
            var c = consumables;

            // Convert foods
            var foodsVanilla = c.foods.vanilla.stream()
                .map(e -> new ConsumablesConfig.ConsumableEntry(e.itemId, e.restoreAmount))
                .toList();
            var foodsModded = c.foods.modded.stream()
                .map(e -> new ConsumablesConfig.ConsumableEntry(e.itemId, e.restoreAmount))
                .toList();

            // Convert drinks
            var drinksVanilla = c.drinks.vanilla.stream()
                .map(e -> new ConsumablesConfig.ConsumableEntry(e.itemId, e.restoreAmount))
                .toList();
            var drinksModded = c.drinks.modded.stream()
                .map(e -> new ConsumablesConfig.ConsumableEntry(e.itemId, e.restoreAmount))
                .toList();

            // Convert energy
            var energyVanilla = c.energy.vanilla.stream()
                .map(e -> new ConsumablesConfig.ConsumableEntry(e.itemId, e.restoreAmount))
                .toList();
            var energyModded = c.energy.modded.stream()
                .map(e -> new ConsumablesConfig.ConsumableEntry(e.itemId, e.restoreAmount))
                .toList();

            // Convert combined
            var combinedVanilla = c.combined.vanilla.stream()
                .map(e -> new ConsumablesConfig.CombinedConsumableEntry(e.itemId, e.hunger, e.thirst, e.energy))
                .toList();
            var combinedModded = c.combined.modded.stream()
                .map(e -> new ConsumablesConfig.CombinedConsumableEntry(e.itemId, e.hunger, e.thirst, e.energy))
                .toList();

            return new ConsumablesConfig(
                new ConsumablesConfig.ConsumableSection(foodsVanilla, foodsModded),
                new ConsumablesConfig.ConsumableSection(drinksVanilla, drinksModded),
                new ConsumablesConfig.ConsumableSection(energyVanilla, energyModded),
                new ConsumablesConfig.CombinedConsumableSection(combinedVanilla, combinedModded)
            );
        }

        private SleepConfig toSleepConfig() {
            var s = sleep;
            return new SleepConfig(
                new SleepConfig.BedBlocksSection(
                    new ArrayList<>(s.bedBlocks.vanilla),
                    new ArrayList<>(s.bedBlocks.modded)
                ),
                s.energyRestoreAmount,
                s.cooldownMs,
                s.respectSleepSchedule
            );
        }

        /**
         * Creates JSON config from ModConfig record.
         */
        public static JsonConfig fromModConfig(ModConfig config) {
            var json = new JsonConfig();

            // Metabolism
            var m = config.metabolism();
            var a = m.activityMultipliers();

            json.metabolism.enableHunger = m.enableHunger();
            json.metabolism.enableThirst = m.enableThirst();
            json.metabolism.enableEnergy = m.enableEnergy();
            json.metabolism.hungerDepletionRate = m.hungerDepletionRate();
            json.metabolism.thirstDepletionRate = m.thirstDepletionRate();
            json.metabolism.energyDepletionRate = m.energyDepletionRate();
            json.metabolism.starvationThreshold = m.starvationThreshold();
            json.metabolism.dehydrationThreshold = m.dehydrationThreshold();
            json.metabolism.exhaustionThreshold = m.exhaustionThreshold();
            json.metabolism.initialHunger = m.initialHunger();
            json.metabolism.initialThirst = m.initialThirst();
            json.metabolism.initialEnergy = m.initialEnergy();

            json.metabolism.activityMultipliers.idle = a.idle();
            json.metabolism.activityMultipliers.walking = a.walking();
            json.metabolism.activityMultipliers.sprinting = a.sprinting();
            json.metabolism.activityMultipliers.swimming = a.swimming();
            json.metabolism.activityMultipliers.combat = a.combat();
            json.metabolism.activityMultipliers.jumping = a.jumping();

            // Consumables
            var c = config.consumables();

            // Foods
            json.consumables.foods.vanilla = c.foods().vanilla().stream()
                .map(e -> new ConsumableEntryJson(e.itemId(), e.restoreAmount()))
                .toList();
            json.consumables.foods.modded = c.foods().modded().stream()
                .map(e -> new ConsumableEntryJson(e.itemId(), e.restoreAmount()))
                .toList();

            // Drinks
            json.consumables.drinks.vanilla = c.drinks().vanilla().stream()
                .map(e -> new ConsumableEntryJson(e.itemId(), e.restoreAmount()))
                .toList();
            json.consumables.drinks.modded = c.drinks().modded().stream()
                .map(e -> new ConsumableEntryJson(e.itemId(), e.restoreAmount()))
                .toList();

            // Energy
            json.consumables.energy.vanilla = c.energy().vanilla().stream()
                .map(e -> new ConsumableEntryJson(e.itemId(), e.restoreAmount()))
                .toList();
            json.consumables.energy.modded = c.energy().modded().stream()
                .map(e -> new ConsumableEntryJson(e.itemId(), e.restoreAmount()))
                .toList();

            // Combined
            json.consumables.combined.vanilla = c.combined().vanilla().stream()
                .map(e -> new CombinedConsumableEntryJson(e.itemId(), e.hunger(), e.thirst(), e.energy()))
                .toList();
            json.consumables.combined.modded = c.combined().modded().stream()
                .map(e -> new CombinedConsumableEntryJson(e.itemId(), e.hunger(), e.thirst(), e.energy()))
                .toList();

            // Sleep
            var s = config.sleep();
            json.sleep.bedBlocks.vanilla = new ArrayList<>(s.bedBlocks().vanilla());
            json.sleep.bedBlocks.modded = new ArrayList<>(s.bedBlocks().modded());
            json.sleep.energyRestoreAmount = s.energyRestoreAmount();
            json.sleep.cooldownMs = s.cooldownMs();
            json.sleep.respectSleepSchedule = s.respectSleepSchedule();

            // Debuffs
            var d = config.debuffs();
            json.debuffs.hunger.enabled = d.hunger().enabled();
            json.debuffs.hunger.damageTickIntervalSeconds = d.hunger().damageTickIntervalSeconds();
            json.debuffs.hunger.initialDamage = d.hunger().initialDamage();
            json.debuffs.hunger.damageIncreasePerTick = d.hunger().damageIncreasePerTick();
            json.debuffs.hunger.maxDamage = d.hunger().maxDamage();

            json.debuffs.thirst.enabled = d.thirst().enabled();
            json.debuffs.thirst.blurStartThreshold = d.thirst().blurStartThreshold();
            json.debuffs.thirst.damageTickIntervalSeconds = d.thirst().damageTickIntervalSeconds();
            json.debuffs.thirst.damageAtZero = d.thirst().damageAtZero();

            json.debuffs.energy.enabled = d.energy().enabled();
            json.debuffs.energy.slowStartThreshold = d.energy().slowStartThreshold();
            json.debuffs.energy.minSpeedMultiplier = d.energy().minSpeedMultiplier();
            json.debuffs.energy.maxStaminaMultiplier = d.energy().maxStaminaMultiplier();
            json.debuffs.energy.staminaDrainStartThreshold = d.energy().staminaDrainStartThreshold();
            json.debuffs.energy.staminaDrainRecoveryThreshold = d.energy().staminaDrainRecoveryThreshold();
            json.debuffs.energy.staminaDrainPerTick = d.energy().staminaDrainPerTick();
            json.debuffs.energy.staminaDrainTickIntervalSeconds = d.energy().staminaDrainTickIntervalSeconds();

            return json;
        }
    }
}
