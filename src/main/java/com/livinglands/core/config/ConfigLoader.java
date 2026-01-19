package com.livinglands.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.metabolism.poison.PoisonEffectType;

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
        public PoisonSection poison = new PoisonSection();
        public NativeDebuffsSection nativeDebuffs = new NativeDebuffsSection();
        public LevelingSection leveling = new LevelingSection();

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

        // ========================================
        // POISON SECTION
        // ========================================
        public static class PoisonSection {
            public boolean enabled = true;
            public MildToxinJson mildToxin = new MildToxinJson();
            public SlowPoisonJson slowPoison = new SlowPoisonJson();
            public PurgeJson purge = new PurgeJson();
            public NativePoisonJson nativePoison = new NativePoisonJson();
            public PoisonItemsJson items = new PoisonItemsJson();
        }

        public static class MildToxinJson {
            public float hungerDrainPerTick = 2.0f;
            public float thirstDrainPerTick = 1.5f;
            public float energyDrainPerTick = 1.0f;
            public float tickIntervalSeconds = 1.0f;
            public float durationSeconds = 8.0f;
        }

        public static class SlowPoisonJson {
            public float hungerDrainPerTick = 1.0f;
            public float thirstDrainPerTick = 1.0f;
            public float energyDrainPerTick = 0.5f;
            public float tickIntervalSeconds = 3.0f;
            public float durationSeconds = 45.0f;
        }

        public static class PurgeJson {
            public float hungerDrainPerTick = 3.0f;
            public float thirstDrainPerTick = 2.5f;
            public float energyDrainPerTick = 2.0f;
            public float drainIntervalSeconds = 0.5f;
            public float drainDurationSeconds = 5.0f;
            public float recoveryDurationSeconds = 20.0f;
        }

        public static class NativePoisonJson {
            public boolean enabled = true;
            public float hungerDrainPerTick = 1.5f;
            public float thirstDrainPerTick = 1.5f;
            public float energyDrainPerTick = 1.0f;
            public float tickIntervalSeconds = 2.0f;
        }

        public static class PoisonItemsJson {
            public List<PoisonItemEntryJson> vanilla = new ArrayList<>();
            public List<PoisonItemEntryJson> modded = new ArrayList<>();
        }

        public static class PoisonItemEntryJson {
            public String itemId;
            public String effectType = "RANDOM";
            public double hungerRestore = 0;
            public double thirstRestore = 0;

            public PoisonItemEntryJson() {}

            public PoisonItemEntryJson(String itemId, String effectType, double hungerRestore, double thirstRestore) {
                this.itemId = itemId;
                this.effectType = effectType;
                this.hungerRestore = hungerRestore;
                this.thirstRestore = thirstRestore;
            }
        }

        // ========================================
        // NATIVE DEBUFFS SECTION
        // ========================================
        public static class NativeDebuffsSection {
            public boolean enabled = true;
            public PoisonDebuffJson poison = new PoisonDebuffJson();
            public BurnDebuffJson burn = new BurnDebuffJson();
            public StunDebuffJson stun = new StunDebuffJson();
            public FreezeDebuffJson freeze = new FreezeDebuffJson();
            public RootDebuffJson root = new RootDebuffJson();
            public SlowDebuffJson slow = new SlowDebuffJson();
        }

        public static class PoisonDebuffJson {
            public boolean enabled = true;
            public float hungerDrainPerTick = 1.5f;
            public float thirstDrainPerTick = 1.5f;
            public float energyDrainPerTick = 1.0f;
            public float tickIntervalSeconds = 2.0f;
        }

        public static class BurnDebuffJson {
            public boolean enabled = true;
            public float hungerDrainPerTick = 0.5f;
            public float thirstDrainPerTick = 3.0f;
            public float energyDrainPerTick = 2.0f;
            public float tickIntervalSeconds = 1.0f;
        }

        public static class StunDebuffJson {
            public boolean enabled = true;
            public float hungerDrainPerTick = 0.5f;
            public float thirstDrainPerTick = 0.5f;
            public float energyDrainPerTick = 3.0f;
            public float tickIntervalSeconds = 1.0f;
        }

        public static class FreezeDebuffJson {
            public boolean enabled = true;
            public float hungerDrainPerTick = 0.5f;
            public float thirstDrainPerTick = 0.5f;
            public float energyDrainPerTick = 2.5f;
            public float tickIntervalSeconds = 1.5f;
        }

        public static class RootDebuffJson {
            public boolean enabled = true;
            public float hungerDrainPerTick = 0.5f;
            public float thirstDrainPerTick = 0.5f;
            public float energyDrainPerTick = 2.0f;
            public float tickIntervalSeconds = 2.0f;
        }

        public static class SlowDebuffJson {
            public boolean enabled = true;
            public float hungerDrainPerTick = 0.5f;
            public float thirstDrainPerTick = 0.5f;
            public float energyDrainPerTick = 1.5f;
            public float tickIntervalSeconds = 2.5f;
        }

        // ========================================
        // LEVELING SECTION
        // ========================================
        public static class LevelingSection {
            public boolean enabled = true;
            public int maxLevel = 100;
            public double baseXpPerLevel = 100.0;
            public double xpScalingFactor = 1.15;
            public double healthPerLevel = 5.0;
            public double staminaPerLevel = 3.0;
            public int skillPointsPerLevel = 1;
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
            var poisonConfig = toPoisonConfig();
            var nativeDebuffsConfig = toNativeDebuffsConfig();
            var levelingConfig = toLevelingConfig();

            return new ModConfig(metabolismConfig, consumablesConfig, sleepConfig, debuffsConfig, poisonConfig, nativeDebuffsConfig, levelingConfig);
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

        private PoisonConfig toPoisonConfig() {
            var p = poison;

            // Convert poison items
            var vanillaItems = p.items.vanilla.stream()
                .map(e -> new PoisonConfig.PoisonItemEntry(
                    e.itemId,
                    PoisonEffectType.valueOf(e.effectType),
                    e.hungerRestore,
                    e.thirstRestore
                ))
                .toList();

            var moddedItems = p.items.modded.stream()
                .map(e -> new PoisonConfig.PoisonItemEntry(
                    e.itemId,
                    PoisonEffectType.valueOf(e.effectType),
                    e.hungerRestore,
                    e.thirstRestore
                ))
                .toList();

            return new PoisonConfig(
                p.enabled,
                new PoisonConfig.MildToxinConfig(
                    p.mildToxin.hungerDrainPerTick,
                    p.mildToxin.thirstDrainPerTick,
                    p.mildToxin.energyDrainPerTick,
                    p.mildToxin.tickIntervalSeconds,
                    p.mildToxin.durationSeconds
                ),
                new PoisonConfig.SlowPoisonConfig(
                    p.slowPoison.hungerDrainPerTick,
                    p.slowPoison.thirstDrainPerTick,
                    p.slowPoison.energyDrainPerTick,
                    p.slowPoison.tickIntervalSeconds,
                    p.slowPoison.durationSeconds
                ),
                new PoisonConfig.PurgeConfig(
                    p.purge.hungerDrainPerTick,
                    p.purge.thirstDrainPerTick,
                    p.purge.energyDrainPerTick,
                    p.purge.drainIntervalSeconds,
                    p.purge.drainDurationSeconds,
                    p.purge.recoveryDurationSeconds
                ),
                new PoisonConfig.NativePoisonConfig(
                    p.nativePoison.enabled,
                    p.nativePoison.hungerDrainPerTick,
                    p.nativePoison.thirstDrainPerTick,
                    p.nativePoison.energyDrainPerTick,
                    p.nativePoison.tickIntervalSeconds
                ),
                new PoisonConfig.PoisonItemsSection(vanillaItems, moddedItems)
            );
        }

        private DebuffConfig toNativeDebuffsConfig() {
            var nd = nativeDebuffs;

            return new DebuffConfig(
                nd.enabled,
                new DebuffConfig.PoisonDebuffConfig(
                    nd.poison.enabled,
                    nd.poison.hungerDrainPerTick,
                    nd.poison.thirstDrainPerTick,
                    nd.poison.energyDrainPerTick,
                    nd.poison.tickIntervalSeconds
                ),
                new DebuffConfig.BurnDebuffConfig(
                    nd.burn.enabled,
                    nd.burn.hungerDrainPerTick,
                    nd.burn.thirstDrainPerTick,
                    nd.burn.energyDrainPerTick,
                    nd.burn.tickIntervalSeconds
                ),
                new DebuffConfig.StunDebuffConfig(
                    nd.stun.enabled,
                    nd.stun.hungerDrainPerTick,
                    nd.stun.thirstDrainPerTick,
                    nd.stun.energyDrainPerTick,
                    nd.stun.tickIntervalSeconds
                ),
                new DebuffConfig.FreezeDebuffConfig(
                    nd.freeze.enabled,
                    nd.freeze.hungerDrainPerTick,
                    nd.freeze.thirstDrainPerTick,
                    nd.freeze.energyDrainPerTick,
                    nd.freeze.tickIntervalSeconds
                ),
                new DebuffConfig.RootDebuffConfig(
                    nd.root.enabled,
                    nd.root.hungerDrainPerTick,
                    nd.root.thirstDrainPerTick,
                    nd.root.energyDrainPerTick,
                    nd.root.tickIntervalSeconds
                ),
                new DebuffConfig.SlowDebuffConfig(
                    nd.slow.enabled,
                    nd.slow.hungerDrainPerTick,
                    nd.slow.thirstDrainPerTick,
                    nd.slow.energyDrainPerTick,
                    nd.slow.tickIntervalSeconds
                )
            );
        }

        private LevelingConfig toLevelingConfig() {
            var l = leveling;
            return new LevelingConfig(
                l.enabled,
                l.maxLevel,
                l.baseXpPerLevel,
                l.xpScalingFactor,
                l.healthPerLevel,
                l.staminaPerLevel,
                l.skillPointsPerLevel
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

            // Poison
            var p = config.poison();
            json.poison.enabled = p.enabled();
            json.poison.mildToxin.hungerDrainPerTick = p.mildToxin().hungerDrainPerTick();
            json.poison.mildToxin.thirstDrainPerTick = p.mildToxin().thirstDrainPerTick();
            json.poison.mildToxin.energyDrainPerTick = p.mildToxin().energyDrainPerTick();
            json.poison.mildToxin.tickIntervalSeconds = p.mildToxin().tickIntervalSeconds();
            json.poison.mildToxin.durationSeconds = p.mildToxin().durationSeconds();
            json.poison.slowPoison.hungerDrainPerTick = p.slowPoison().hungerDrainPerTick();
            json.poison.slowPoison.thirstDrainPerTick = p.slowPoison().thirstDrainPerTick();
            json.poison.slowPoison.energyDrainPerTick = p.slowPoison().energyDrainPerTick();
            json.poison.slowPoison.tickIntervalSeconds = p.slowPoison().tickIntervalSeconds();
            json.poison.slowPoison.durationSeconds = p.slowPoison().durationSeconds();
            json.poison.purge.hungerDrainPerTick = p.purge().hungerDrainPerTick();
            json.poison.purge.thirstDrainPerTick = p.purge().thirstDrainPerTick();
            json.poison.purge.energyDrainPerTick = p.purge().energyDrainPerTick();
            json.poison.purge.drainIntervalSeconds = p.purge().drainIntervalSeconds();
            json.poison.purge.drainDurationSeconds = p.purge().drainDurationSeconds();
            json.poison.purge.recoveryDurationSeconds = p.purge().recoveryDurationSeconds();

            json.poison.nativePoison.enabled = p.nativePoison().enabled();
            json.poison.nativePoison.hungerDrainPerTick = p.nativePoison().hungerDrainPerTick();
            json.poison.nativePoison.thirstDrainPerTick = p.nativePoison().thirstDrainPerTick();
            json.poison.nativePoison.energyDrainPerTick = p.nativePoison().energyDrainPerTick();
            json.poison.nativePoison.tickIntervalSeconds = p.nativePoison().tickIntervalSeconds();

            json.poison.items.vanilla = p.items().vanilla().stream()
                .map(e -> new PoisonItemEntryJson(e.itemId(), e.effectType().name(), e.hungerRestore(), e.thirstRestore()))
                .toList();
            json.poison.items.modded = p.items().modded().stream()
                .map(e -> new PoisonItemEntryJson(e.itemId(), e.effectType().name(), e.hungerRestore(), e.thirstRestore()))
                .toList();

            // Native Debuffs
            var nd = config.nativeDebuffs();
            json.nativeDebuffs.enabled = nd.enabled();
            json.nativeDebuffs.poison.enabled = nd.poison().enabled();
            json.nativeDebuffs.poison.hungerDrainPerTick = nd.poison().hungerDrainPerTick();
            json.nativeDebuffs.poison.thirstDrainPerTick = nd.poison().thirstDrainPerTick();
            json.nativeDebuffs.poison.energyDrainPerTick = nd.poison().energyDrainPerTick();
            json.nativeDebuffs.poison.tickIntervalSeconds = nd.poison().tickIntervalSeconds();

            json.nativeDebuffs.burn.enabled = nd.burn().enabled();
            json.nativeDebuffs.burn.hungerDrainPerTick = nd.burn().hungerDrainPerTick();
            json.nativeDebuffs.burn.thirstDrainPerTick = nd.burn().thirstDrainPerTick();
            json.nativeDebuffs.burn.energyDrainPerTick = nd.burn().energyDrainPerTick();
            json.nativeDebuffs.burn.tickIntervalSeconds = nd.burn().tickIntervalSeconds();

            json.nativeDebuffs.stun.enabled = nd.stun().enabled();
            json.nativeDebuffs.stun.hungerDrainPerTick = nd.stun().hungerDrainPerTick();
            json.nativeDebuffs.stun.thirstDrainPerTick = nd.stun().thirstDrainPerTick();
            json.nativeDebuffs.stun.energyDrainPerTick = nd.stun().energyDrainPerTick();
            json.nativeDebuffs.stun.tickIntervalSeconds = nd.stun().tickIntervalSeconds();

            json.nativeDebuffs.freeze.enabled = nd.freeze().enabled();
            json.nativeDebuffs.freeze.hungerDrainPerTick = nd.freeze().hungerDrainPerTick();
            json.nativeDebuffs.freeze.thirstDrainPerTick = nd.freeze().thirstDrainPerTick();
            json.nativeDebuffs.freeze.energyDrainPerTick = nd.freeze().energyDrainPerTick();
            json.nativeDebuffs.freeze.tickIntervalSeconds = nd.freeze().tickIntervalSeconds();

            json.nativeDebuffs.root.enabled = nd.root().enabled();
            json.nativeDebuffs.root.hungerDrainPerTick = nd.root().hungerDrainPerTick();
            json.nativeDebuffs.root.thirstDrainPerTick = nd.root().thirstDrainPerTick();
            json.nativeDebuffs.root.energyDrainPerTick = nd.root().energyDrainPerTick();
            json.nativeDebuffs.root.tickIntervalSeconds = nd.root().tickIntervalSeconds();

            json.nativeDebuffs.slow.enabled = nd.slow().enabled();
            json.nativeDebuffs.slow.hungerDrainPerTick = nd.slow().hungerDrainPerTick();
            json.nativeDebuffs.slow.thirstDrainPerTick = nd.slow().thirstDrainPerTick();
            json.nativeDebuffs.slow.energyDrainPerTick = nd.slow().energyDrainPerTick();
            json.nativeDebuffs.slow.tickIntervalSeconds = nd.slow().tickIntervalSeconds();

            // Leveling
            var l = config.leveling();
            json.leveling.enabled = l.enabled();
            json.leveling.maxLevel = l.maxLevel();
            json.leveling.baseXpPerLevel = l.baseXpPerLevel();
            json.leveling.xpScalingFactor = l.xpScalingFactor();
            json.leveling.healthPerLevel = l.healthPerLevel();
            json.leveling.staminaPerLevel = l.staminaPerLevel();
            json.leveling.skillPointsPerLevel = l.skillPointsPerLevel();

            return json;
        }
    }
}
