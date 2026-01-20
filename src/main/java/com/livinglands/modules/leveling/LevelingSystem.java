package com.livinglands.modules.leveling;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionData;
import com.livinglands.modules.leveling.profession.ProfessionType;
import com.livinglands.modules.leveling.profession.XpCalculator;
import com.livinglands.modules.leveling.ability.AbilitySystem;
import com.livinglands.modules.metabolism.MetabolismModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * Core system for managing player XP, levels, and profession progression.
 * Handles XP awards, level-ups, and integrates with the metabolism module for bonuses.
 */
public class LevelingSystem {

    private static final long SAVE_INTERVAL_MS = 60_000L; // Auto-save every 60 seconds

    private final LevelingModuleConfig config;
    private final HytaleLogger logger;
    private final PlayerRegistry playerRegistry;
    private final XpCalculator xpCalculator;
    private final LevelingDataPersistence persistence;

    // In-memory player data cache
    private final Map<UUID, PlayerLevelingData> playerData = new ConcurrentHashMap<>();

    // Optional metabolism module for XP bonuses
    @Nullable
    private MetabolismModule metabolismModule;

    // Optional ability system for XP boost abilities
    @Nullable
    private AbilitySystem abilitySystem;

    // Background executor for auto-save
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> saveTask;
    private volatile boolean running = false;

    // Level-up callback for UI notifications
    @Nullable
    private BiConsumer<UUID, LevelUpEvent> levelUpCallback;

    // XP gain callback for UI notifications
    @Nullable
    private BiConsumer<UUID, XpGainEvent> xpGainCallback;

    // Death penalty callback for chat notifications
    @Nullable
    private BiConsumer<UUID, DeathPenaltyEvent> deathPenaltyCallback;

    public LevelingSystem(@Nonnull LevelingModuleConfig config,
                          @Nonnull HytaleLogger logger,
                          @Nonnull PlayerRegistry playerRegistry,
                          @Nonnull LevelingDataPersistence persistence) {
        this.config = config;
        this.logger = logger;
        this.playerRegistry = playerRegistry;
        this.persistence = persistence;
        this.xpCalculator = new XpCalculator(config.maxLevel, config.baseXp, config.xpExponent);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LivingLands-Leveling");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the leveling system background tasks.
     */
    public void start() {
        if (running) return;

        // Schedule auto-save
        saveTask = executor.scheduleAtFixedRate(
            this::saveAllDirty,
            SAVE_INTERVAL_MS,
            SAVE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        running = true;
        logger.at(Level.FINE).log("Leveling system started");
    }

    /**
     * Stop the leveling system and save all data.
     */
    public void stop() {
        if (!running) return;

        if (saveTask != null) {
            saveTask.cancel(false);
        }

        // Save all player data
        saveAll();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        running = false;
        logger.at(Level.FINE).log("Leveling system stopped");
    }

    /**
     * Set the metabolism module for XP bonus integration.
     */
    public void setMetabolismModule(@Nullable MetabolismModule metabolismModule) {
        this.metabolismModule = metabolismModule;
        if (metabolismModule != null) {
            logger.at(Level.FINE).log("Metabolism integration enabled for leveling");
        }
    }

    /**
     * Set the ability system for XP boost abilities.
     */
    public void setAbilitySystem(@Nullable AbilitySystem abilitySystem) {
        this.abilitySystem = abilitySystem;
        if (abilitySystem != null) {
            logger.at(Level.FINE).log("Ability XP boost integration enabled for leveling");
        }
    }

    /**
     * Set the level-up callback for UI notifications.
     */
    public void setLevelUpCallback(@Nullable BiConsumer<UUID, LevelUpEvent> callback) {
        this.levelUpCallback = callback;
    }

    /**
     * Set the XP gain callback for UI notifications.
     */
    public void setXpGainCallback(@Nullable BiConsumer<UUID, XpGainEvent> callback) {
        this.xpGainCallback = callback;
    }

    /**
     * Set the death penalty callback for chat notifications.
     */
    public void setDeathPenaltyCallback(@Nullable BiConsumer<UUID, DeathPenaltyEvent> callback) {
        this.deathPenaltyCallback = callback;
    }

    /**
     * Initialize data for a new player.
     */
    public void initializePlayer(@Nonnull UUID playerId) {
        if (playerData.containsKey(playerId)) {
            return; // Already initialized
        }

        var data = persistence.load(playerId);
        playerData.put(playerId, data);
        logger.at(Level.FINE).log("Initialized leveling data for player %s: %s", playerId, data);
    }

    /**
     * Remove player data from memory and save to disk.
     */
    public void removePlayer(@Nonnull UUID playerId) {
        var data = playerData.remove(playerId);
        if (data != null) {
            persistence.save(data);
            logger.at(Level.FINE).log("Saved and removed leveling data for player %s", playerId);
        }
    }

    /**
     * Get player leveling data.
     */
    public Optional<PlayerLevelingData> getPlayerData(@Nonnull UUID playerId) {
        return Optional.ofNullable(playerData.get(playerId));
    }

    /**
     * Award XP to a player for a profession.
     * XP is not awarded in Creative mode.
     *
     * @param playerId The player's UUID
     * @param profession The profession to award XP to
     * @param baseXp The base XP amount (before multipliers)
     */
    public void awardXp(@Nonnull UUID playerId, @Nonnull ProfessionType profession, long baseXp) {
        // Skip XP awards for Creative mode players
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isPresent() && sessionOpt.get().isCreativeMode()) {
            return; // XP paused in Creative mode
        }

        var data = playerData.get(playerId);
        if (data == null) {
            logger.at(Level.FINE).log("Cannot award XP - player %s not initialized", playerId);
            return;
        }

        // Check if profession is enabled
        if (!config.isProfessionEnabled(profession)) {
            return;
        }

        // Apply multipliers
        float professionMultiplier = config.getProfessionXpMultiplier(profession);
        float metabolismMultiplier = getMetabolismMultiplier(playerId);
        float abilityMultiplier = getAbilityXpMultiplier(playerId, profession);
        long finalXp = Math.round(baseXp * professionMultiplier * metabolismMultiplier * abilityMultiplier);

        if (finalXp <= 0) return;

        // Capture values for callbacks outside synchronized block
        int finalLevel;
        long finalCurrentXp;
        long finalXpToNext;
        int levelsGained;

        // Synchronize the read-modify-write sequence to prevent race conditions
        // when multiple threads award XP to the same player/profession concurrently
        synchronized (data) {
            // Get current profession data
            var profData = data.getProfession(profession);
            if (profData == null) {
                profData = ProfessionData.initial(xpCalculator.getXpToNextLevel(1));
                data.setProfession(profession, profData);
            }

            // Track total XP earned
            data.addTotalXpEarned(finalXp);

            // Add XP
            profData.addXp(finalXp);

            // Check for level ups
            levelsGained = 0;
            int startLevel = profData.getLevel();

            while (profData.canLevelUp() && profData.getLevel() < config.maxLevel) {
                long nextLevelXp = xpCalculator.getXpToNextLevel(profData.getLevel() + 1);
                profData.levelUp(nextLevelXp);
                levelsGained++;

                // Fire level-up callback
                if (levelUpCallback != null) {
                    levelUpCallback.accept(playerId, new LevelUpEvent(
                        profession, profData.getLevel(), startLevel
                    ));
                }

                logger.at(Level.FINE).log("Player %s leveled up %s to %d!",
                    playerId, profession.getDisplayName(), profData.getLevel());
            }

            // Update the profession data
            data.setProfession(profession, profData);
            data.markDirty();

            // Capture final values for callbacks
            finalLevel = profData.getLevel();
            finalCurrentXp = profData.getCurrentXp();
            finalXpToNext = profData.getXpToNextLevel();
        }

        // Fire XP gain callback
        if (xpGainCallback != null && config.showXpNotifications) {
            xpGainCallback.accept(playerId, new XpGainEvent(
                profession, finalXp, finalLevel, finalCurrentXp,
                finalXpToNext, levelsGained > 0
            ));
        }

        logger.at(Level.FINE).log("Awarded %d XP to player %s for %s (total: %d/%d)",
            finalXp, playerId, profession.getDisplayName(),
            finalCurrentXp, finalXpToNext);
    }

    /**
     * Get the ability-based XP multiplier for a player and profession.
     * Checks if Tier 1 XP boost ability triggers.
     */
    private float getAbilityXpMultiplier(@Nonnull UUID playerId, @Nonnull ProfessionType profession) {
        if (abilitySystem == null) {
            return 1.0f;
        }
        return abilitySystem.checkXpBoost(playerId, profession);
    }

    /**
     * Get the metabolism-based XP multiplier for a player.
     */
    private float getMetabolismMultiplier(@Nonnull UUID playerId) {
        if (!config.metabolismIntegration || metabolismModule == null) {
            return 1.0f;
        }

        var metaDataOpt = metabolismModule.getSystem().getPlayerData(playerId);
        if (metaDataOpt.isEmpty()) {
            return 1.0f;
        }

        var metaData = metaDataOpt.get();
        double hunger = metaData.getHunger();
        double thirst = metaData.getThirst();
        double energy = metaData.getEnergy();

        // Check for starving penalty (any stat below threshold)
        if (hunger < config.starvingThreshold ||
            thirst < config.starvingThreshold ||
            energy < config.starvingThreshold) {
            return config.starvingXpMultiplier;
        }

        // Check for well-fed bonus (all stats above threshold)
        if (hunger > config.wellFedThreshold &&
            thirst > config.wellFedThreshold &&
            energy > config.wellFedThreshold) {
            return config.wellFedXpMultiplier;
        }

        return 1.0f;
    }

    /**
     * Get a player's level in a profession.
     */
    public int getLevel(@Nonnull UUID playerId, @Nonnull ProfessionType profession) {
        var data = playerData.get(playerId);
        if (data == null) return 1;
        return data.getLevel(profession);
    }

    /**
     * Set a player's level in a profession (admin command).
     */
    public void setLevel(@Nonnull UUID playerId, @Nonnull ProfessionType profession, int level) {
        var data = playerData.get(playerId);
        if (data == null) return;

        level = Math.max(1, Math.min(level, config.maxLevel));

        var profData = new ProfessionData(
            level,
            0,
            xpCalculator.getXpToNextLevel(level)
        );
        data.setProfession(profession, profData);
        data.markDirty();

        // Notify ability system to check for new unlocks (including permanent buffs)
        if (abilitySystem != null) {
            abilitySystem.onLevelUp(playerId, profession, level);
        }

        logger.at(Level.FINE).log("Set player %s %s level to %d",
            playerId, profession.getDisplayName(), level);
    }

    /**
     * Save all dirty player data.
     */
    private void saveAllDirty() {
        for (var entry : playerData.entrySet()) {
            var data = entry.getValue();
            if (data.isDirty()) {
                persistence.save(data);
            }
        }
    }

    /**
     * Save all player data.
     */
    public void saveAll() {
        for (var data : playerData.values()) {
            persistence.save(data);
        }
        logger.at(Level.FINE).log("Saved all leveling data (%d players)", playerData.size());
    }

    /**
     * Get the XP calculator.
     */
    public XpCalculator getXpCalculator() {
        return xpCalculator;
    }

    /**
     * Get the configuration.
     */
    public LevelingModuleConfig getConfig() {
        return config;
    }

    /**
     * Event record for level-up notifications.
     */
    public record LevelUpEvent(
        ProfessionType profession,
        int newLevel,
        int previousLevel
    ) {}

    /**
     * Event record for XP gain notifications.
     */
    public record XpGainEvent(
        ProfessionType profession,
        long xpGained,
        int currentLevel,
        long currentXp,
        long xpToNextLevel,
        boolean leveledUp
    ) {}

    /**
     * Event record for death penalty notifications.
     */
    public record DeathPenaltyEvent(
        List<ProfessionPenalty> penalties
    ) {
        public record ProfessionPenalty(
            ProfessionType profession,
            int level,
            long xpLost,
            long xpBefore,
            long xpAfter
        ) {}
    }

    /**
     * Apply death penalty to a player.
     * Reduces current XP in 2 random professions by 85% of current level progress.
     * The penalty will not cause a level down (XP stays at minimum 0).
     *
     * @param playerId The player's UUID
     */
    public void applyDeathPenalty(@Nonnull UUID playerId) {
        var data = playerData.get(playerId);
        if (data == null) {
            logger.at(Level.FINE).log("Cannot apply death penalty - player %s not initialized", playerId);
            return;
        }

        List<DeathPenaltyEvent.ProfessionPenalty> penalties;

        // Synchronize the read-modify-write sequence to prevent race conditions
        // when death penalty is applied concurrently with XP awards
        synchronized (data) {
            // Get all professions with XP to lose (level > 1 or has current XP)
            List<ProfessionType> eligibleProfessions = new ArrayList<>();
            for (ProfessionType profession : ProfessionType.values()) {
                if (!config.isProfessionEnabled(profession)) {
                    continue;
                }
                var profData = data.getProfession(profession);
                if (profData != null && profData.getCurrentXp() > 0) {
                    eligibleProfessions.add(profession);
                }
            }

            // If no professions have XP to lose, no penalty
            if (eligibleProfessions.isEmpty()) {
                logger.at(Level.FINE).log("Player %s has no XP to lose from death penalty", playerId);
                return;
            }

            // Shuffle and pick up to 2 random professions using ThreadLocalRandom
            Collections.shuffle(eligibleProfessions, ThreadLocalRandom.current());
            int count = Math.min(2, eligibleProfessions.size());

            penalties = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                ProfessionType profession = eligibleProfessions.get(i);
                var profData = data.getProfession(profession);
                if (profData == null) continue;

                long xpBefore = profData.getCurrentXp();

                // Calculate 85% reduction of current XP
                // This ensures we don't reduce below 0 (no level down)
                long xpToLose = Math.round(xpBefore * 0.85);

                if (xpToLose <= 0) {
                    continue; // No significant XP to lose
                }

                // Apply the penalty
                long xpAfter = xpBefore - xpToLose;
                profData.setCurrentXp(xpAfter);
                data.setProfession(profession, profData);

                penalties.add(new DeathPenaltyEvent.ProfessionPenalty(
                    profession,
                    profData.getLevel(),
                    xpToLose,
                    xpBefore,
                    xpAfter
                ));

                logger.at(Level.FINE).log("Death penalty: Player %s lost %d XP in %s (level %d: %d -> %d)",
                    playerId, xpToLose, profession.getDisplayName(), profData.getLevel(), xpBefore, xpAfter);
            }

            // Mark data as dirty for saving
            if (!penalties.isEmpty()) {
                data.markDirty();
            }
        }

        // Fire callback for chat notification outside synchronized block
        if (!penalties.isEmpty() && deathPenaltyCallback != null) {
            deathPenaltyCallback.accept(playerId, new DeathPenaltyEvent(penalties));
        }
    }
}
