package com.livinglands.modules.leveling.ability;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.notifications.NotificationModule;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.config.AbilityConfig;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;
import com.livinglands.modules.metabolism.MetabolismSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * System for managing passive abilities and their triggers.
 * Coordinates between TimedBuffManager, PermanentBuffManager, and ability handlers.
 *
 * Ability Tiers:
 * - Tier 1 (Lv.15): XP boosters and timed buffs
 * - Tier 2 (Lv.35): Resource management (stat restores, depletion pauses)
 * - Tier 3 (Lv.60): Permanent passive bonuses (always active)
 */
public class AbilitySystem {

    private final LevelingSystem levelingSystem;
    private final LevelingModuleConfig config;
    private final PlayerRegistry playerRegistry;
    private final HytaleLogger logger;
    private final Random random;

    @Nullable
    private TimedBuffManager timedBuffManager;
    @Nullable
    private PermanentBuffManager permanentBuffManager;
    @Nullable
    private MetabolismSystem metabolismSystem;
    @Nullable
    private NotificationModule notificationModule;

    // ========== Ability Unlock Cache (O(1) lookups instead of O(n) iterations) ==========

    /** Cache of unlocked abilities per player - invalidated on level-up */
    private final Map<UUID, Set<AbilityType>> unlockedAbilitiesCache = new ConcurrentHashMap<>();

    /** Listeners to notify when ability unlock cache is invalidated */
    private final Set<AbilityUnlockCacheListener> cacheListeners = ConcurrentHashMap.newKeySet();

    /** Interface for components that need to know when ability unlock state changes */
    @FunctionalInterface
    public interface AbilityUnlockCacheListener {
        void onAbilityCacheInvalidated(UUID playerId);
    }

    public AbilitySystem(@Nonnull LevelingSystem levelingSystem,
                         @Nonnull LevelingModuleConfig config,
                         @Nonnull PlayerRegistry playerRegistry,
                         @Nonnull HytaleLogger logger) {
        this.levelingSystem = levelingSystem;
        this.config = config;
        this.playerRegistry = playerRegistry;
        this.logger = logger;
        this.random = new Random();
    }

    public void setTimedBuffManager(@Nullable TimedBuffManager manager) {
        this.timedBuffManager = manager;
    }

    public void setPermanentBuffManager(@Nullable PermanentBuffManager manager) {
        this.permanentBuffManager = manager;
    }

    public void setMetabolismSystem(@Nullable MetabolismSystem system) {
        this.metabolismSystem = system;
    }

    public void setNotificationModule(@Nullable NotificationModule notificationModule) {
        this.notificationModule = notificationModule;
    }

    // ========== Core Trigger Methods ==========

    /**
     * Check if an ability should trigger for a player.
     * For permanent abilities, always returns true if unlocked.
     *
     * @param playerId The player's UUID
     * @param ability The ability to check
     * @return true if the ability triggers
     */
    public boolean shouldTrigger(@Nonnull UUID playerId, @Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null || !abilityConfig.enabled) {
            return false;
        }

        int level = levelingSystem.getLevel(playerId, ability.getProfession());
        float chance = abilityConfig.getChanceAtLevel(level);

        if (chance <= 0) {
            return false;
        }

        // Permanent abilities always "trigger"
        if (abilityConfig.isPermanent) {
            return true;
        }

        // Roll for trigger
        float roll = random.nextFloat();
        boolean triggered = roll < chance;

        if (triggered) {
            logger.at(Level.FINE).log("[Ability] %s TRIGGERED for %s (roll %.2f < %.1f%% chance, level %d)",
                ability.getDisplayName(), playerId, roll, chance * 100, level);
        } else {
            logger.at(Level.FINE).log("[Ability] %s missed for %s (roll %.2f >= %.1f%% chance, level %d)",
                ability.getDisplayName(), playerId, roll, chance * 100, level);
        }

        return triggered;
    }

    // ========== XP Multiplier Methods ==========

    /**
     * Get XP multiplier for a triggered ability.
     * Returns 1.0 if ability doesn't grant XP bonus.
     */
    public float getXpMultiplier(@Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null) {
            return 1.0f;
        }

        // Tier 1 abilities are XP boosters
        if (ability.getTier() == AbilityType.AbilityTier.TIER_1) {
            return 1.0f + abilityConfig.effectStrength;  // e.g., 1.0 + 0.50 = 1.50 (50% bonus)
        }

        return 1.0f;
    }

    /**
     * Check and apply XP multiplier for a profession.
     * Called by XP systems when awarding XP.
     *
     * @return The XP multiplier to apply (1.0 = no bonus, 1.5 = 50% bonus)
     */
    public float checkXpBoost(@Nonnull UUID playerId, @Nonnull ProfessionType profession) {
        AbilityType tier1Ability = AbilityType.getAbilityForProfessionAndTier(
            profession, AbilityType.AbilityTier.TIER_1);

        if (tier1Ability == null) {
            return 1.0f;
        }

        if (shouldTrigger(playerId, tier1Ability)) {
            float multiplier = getXpMultiplier(tier1Ability);
            sendAbilityTriggerMessage(playerId, tier1Ability);
            return multiplier;
        }

        return 1.0f;
    }

    // ========== Tier 2 Ability Methods ==========

    /**
     * Apply Tier 2 ability effect if triggered.
     * Called by ability handlers after relevant events.
     */
    public void applyTier2Effect(@Nonnull UUID playerId, @Nonnull AbilityType ability) {
        if (ability.getTier() != AbilityType.AbilityTier.TIER_2) {
            return;
        }

        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null) {
            return;
        }

        switch (ability) {
            case WARRIORS_RESILIENCE -> applyHealthRestore(playerId, abilityConfig.effectStrength);
            case EFFICIENT_EXTRACTION -> applyHungerPause(playerId, abilityConfig.effectDuration);
            case FORESTS_BLESSING -> applyEnergyRestore(playerId, abilityConfig.effectStrength);
            case STEADY_HANDS -> applyStaminaPause(playerId, abilityConfig.effectDuration);
            case NATURES_GIFT -> applyHungerThirstRestore(playerId, abilityConfig.effectStrength);
            default -> {}
        }

        sendAbilityTriggerMessage(playerId, ability);
    }

    /**
     * Apply Adrenaline Rush speed buff (Combat Tier 1).
     */
    public void applyAdrenalineRush(@Nonnull UUID playerId) {
        AbilityConfig abilityConfig = config.getAbilityConfig(AbilityType.ADRENALINE_RUSH);
        if (abilityConfig == null || timedBuffManager == null) {
            return;
        }

        timedBuffManager.applyBuff(
            playerId,
            TimedBuffManager.TimedBuffType.SPEED_BOOST,
            abilityConfig.effectStrength,
            abilityConfig.effectDuration,
            abilityConfig.showChatMessage,
            abilityConfig.playSound
        );
    }

    // ========== Stat Restore Methods ==========

    private void applyHealthRestore(UUID playerId, float percentage) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) return;

        var session = sessionOpt.get();
        if (!session.isEcsReady()) return;

        var ref = session.getEntityRef();
        var store = session.getStore();
        var world = session.getWorld();

        if (ref == null || store == null || world == null) return;

        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref,
                    com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
                if (statMap == null) return;

                var healthStatId = com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth();
                var healthStat = statMap.get(healthStatId);
                if (healthStat == null) return;

                float currentHealth = healthStat.get();
                float maxHealth = healthStat.getMax();
                float restoreAmount = maxHealth * percentage;
                float newHealth = Math.min(maxHealth, currentHealth + restoreAmount);

                // Use SELF predictable to sync to client
                statMap.setStatValue(
                    com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.Predictable.SELF,
                    healthStatId,
                    newHealth
                );

                logger.at(Level.FINE).log("Health restored for %s: %.0f -> %.0f (+%.0f, %.0f%% of max %.0f)",
                    playerId, currentHealth, newHealth, restoreAmount, percentage * 100, maxHealth);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Failed to restore health for %s", playerId);
            }
        });
    }

    private void applyEnergyRestore(UUID playerId, float amount) {
        if (metabolismSystem == null) return;

        var dataOpt = metabolismSystem.getPlayerData(playerId);
        if (dataOpt.isEmpty()) return;

        var data = dataOpt.get();
        double currentEnergy = data.getEnergy();
        double newEnergy = Math.min(100.0, currentEnergy + amount);
        data.setEnergy(newEnergy);

        logger.at(Level.FINE).log("Energy restored for %s: %.1f -> %.1f",
            playerId, currentEnergy, newEnergy);
    }

    private void applyHungerThirstRestore(UUID playerId, float amount) {
        if (metabolismSystem == null) return;

        var dataOpt = metabolismSystem.getPlayerData(playerId);
        if (dataOpt.isEmpty()) return;

        var data = dataOpt.get();

        double currentHunger = data.getHunger();
        double newHunger = Math.min(100.0, currentHunger + amount);
        data.setHunger(newHunger);

        double currentThirst = data.getThirst();
        double newThirst = Math.min(100.0, currentThirst + amount);
        data.setThirst(newThirst);

        logger.at(Level.FINE).log("Hunger/Thirst restored for %s: hunger %.1f -> %.1f, thirst %.1f -> %.1f",
            playerId, currentHunger, newHunger, currentThirst, newThirst);
    }

    private void applyHungerPause(UUID playerId, float durationSeconds) {
        if (timedBuffManager == null) return;

        AbilityConfig abilityConfig = config.getAbilityConfig(AbilityType.EFFICIENT_EXTRACTION);
        timedBuffManager.applyBuff(
            playerId,
            TimedBuffManager.TimedBuffType.HUNGER_PAUSE,
            1.0f,  // Not used for pause
            durationSeconds,
            abilityConfig != null && abilityConfig.showChatMessage,
            abilityConfig != null && abilityConfig.playSound
        );
    }

    private void applyStaminaPause(UUID playerId, float durationSeconds) {
        if (timedBuffManager == null) return;

        AbilityConfig abilityConfig = config.getAbilityConfig(AbilityType.STEADY_HANDS);
        timedBuffManager.applyBuff(
            playerId,
            TimedBuffManager.TimedBuffType.STAMINA_PAUSE,
            1.0f,  // Not used for pause
            durationSeconds,
            abilityConfig != null && abilityConfig.showChatMessage,
            abilityConfig != null && abilityConfig.playSound
        );
    }

    // ========== Query Methods ==========

    /**
     * Get the trigger chance for an ability at a player's current level.
     */
    public float getTriggerChance(@Nonnull UUID playerId, @Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);

        int level = levelingSystem.getLevel(playerId, ability.getProfession());
        int unlockLevel = ability.getTier().getDefaultUnlockLevel();

        // If config is missing, use default values based on tier
        if (abilityConfig == null) {
            if (level < unlockLevel) {
                return 0f;
            }
            // Permanent abilities always trigger
            if (ability.getTier() == AbilityType.AbilityTier.TIER_3) {
                return 1.0f;
            }
            // Default chance for tier 1/2: 10% base + 0.5% per level above unlock, max 40%
            int levelsAbove = level - unlockLevel;
            return Math.min(0.10f + (levelsAbove * 0.005f), 0.40f);
        }

        if (!abilityConfig.enabled) {
            return 0f;
        }

        return abilityConfig.getChanceAtLevel(level);
    }

    /**
     * Check if a player has unlocked an ability.
     * Uses cached unlocked abilities set for O(1) lookup.
     */
    public boolean isUnlocked(@Nonnull UUID playerId, @Nonnull AbilityType ability) {
        // Use cache if available
        Set<AbilityType> cached = unlockedAbilitiesCache.get(playerId);
        if (cached != null) {
            return cached.contains(ability);
        }

        // Cache miss - rebuild cache and return result
        Set<AbilityType> unlocked = rebuildUnlockedAbilitiesCache(playerId);
        return unlocked.contains(ability);
    }

    /**
     * Check if ability is unlocked without using cache (for cache rebuilding).
     */
    private boolean isUnlockedUncached(@Nonnull UUID playerId, @Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);

        // If config is missing, use default unlock level from tier
        int unlockLevel;
        if (abilityConfig == null) {
            unlockLevel = ability.getTier().getDefaultUnlockLevel();
        } else if (!abilityConfig.enabled) {
            return false;
        } else {
            unlockLevel = abilityConfig.unlockLevel;
        }

        int level = levelingSystem.getLevel(playerId, ability.getProfession());
        return level >= unlockLevel;
    }

    // ========== Ability Unlock Cache Management ==========

    /**
     * Get the set of unlocked abilities for a player.
     * Returns a cached set - do NOT modify the returned set.
     */
    @Nonnull
    public Set<AbilityType> getUnlockedAbilities(@Nonnull UUID playerId) {
        Set<AbilityType> cached = unlockedAbilitiesCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        return rebuildUnlockedAbilitiesCache(playerId);
    }

    /**
     * Rebuild the unlocked abilities cache for a player.
     * Called on cache miss or after level-up.
     */
    private Set<AbilityType> rebuildUnlockedAbilitiesCache(@Nonnull UUID playerId) {
        Set<AbilityType> unlocked = EnumSet.noneOf(AbilityType.class);

        for (AbilityType ability : AbilityType.getAllAbilities()) {
            if (isUnlockedUncached(playerId, ability)) {
                unlocked.add(ability);
            }
        }

        unlockedAbilitiesCache.put(playerId, unlocked);
        logger.at(Level.FINE).log("Rebuilt ability cache for %s: %d abilities unlocked",
            playerId, unlocked.size());

        return unlocked;
    }

    /**
     * Invalidate the ability unlock cache for a player.
     * Called when player levels up.
     */
    public void invalidateAbilityCache(@Nonnull UUID playerId) {
        unlockedAbilitiesCache.remove(playerId);
        // Notify listeners (e.g., HUD elements)
        for (AbilityUnlockCacheListener listener : cacheListeners) {
            try {
                listener.onAbilityCacheInvalidated(playerId);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Error notifying ability cache listener");
            }
        }
    }

    /**
     * Register a listener to be notified when ability cache is invalidated.
     */
    public void addCacheListener(@Nonnull AbilityUnlockCacheListener listener) {
        cacheListeners.add(listener);
    }

    /**
     * Remove a cache listener.
     */
    public void removeCacheListener(@Nonnull AbilityUnlockCacheListener listener) {
        cacheListeners.remove(listener);
    }

    /**
     * Get the level required to unlock an ability.
     */
    public int getUnlockLevel(@Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null || !abilityConfig.enabled) {
            return -1;
        }
        return abilityConfig.unlockLevel;
    }

    /**
     * Get the max chance for an ability.
     */
    public float getMaxChance(@Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null || !abilityConfig.enabled) {
            return 0f;
        }
        return abilityConfig.maxChance;
    }

    /**
     * Get the ability config for an ability type.
     * Returns a default config based on tier if not found in config file.
     */
    @Nullable
    public AbilityConfig getAbilityConfig(@Nonnull AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig != null) {
            return abilityConfig;
        }

        // Return default config based on tier
        return switch (ability.getTier()) {
            case TIER_1 -> AbilityConfig.tier1XpBoost(0.50f);
            case TIER_2 -> AbilityConfig.tier2StatRestore(5.0f);
            case TIER_3 -> AbilityConfig.tier3Permanent(0.10f);
        };
    }

    // ========== Feedback Methods ==========

    private void sendAbilityTriggerMessage(UUID playerId, AbilityType ability) {
        AbilityConfig abilityConfig = config.getAbilityConfig(ability);
        if (abilityConfig == null || !abilityConfig.showChatMessage) {
            return;
        }

        if (notificationModule == null) {
            return;
        }

        String message = formatTriggerMessage(ability, abilityConfig);
        notificationModule.sendChatAbility(playerId, message);
    }

    private String formatTriggerMessage(AbilityType ability, AbilityConfig config) {
        return switch (ability) {
            case PROSPECTORS_EYE -> String.format("[Ability] Prospector's Eye! +%.0f%% Mining XP", config.effectStrength * 100);
            case LUMBERJACKS_VIGOR -> String.format("[Ability] Lumberjack's Vigor! +%.0f%% Logging XP", config.effectStrength * 100);
            case ARCHITECTS_FOCUS -> String.format("[Ability] Architect's Focus! +%.0f%% Building XP", config.effectStrength * 100);
            case FORAGERS_INTUITION -> String.format("[Ability] Forager's Intuition! +%.0f%% Gathering XP", config.effectStrength * 100);
            case WARRIORS_RESILIENCE -> String.format("[Ability] Warrior's Resilience! Restored %.0f%% health", config.effectStrength * 100);
            case FORESTS_BLESSING -> String.format("[Ability] Forest's Blessing! Restored %.0f energy", config.effectStrength);
            case NATURES_GIFT -> String.format("[Ability] Nature's Gift! Restored %.0f hunger and thirst", config.effectStrength);
            default -> String.format("[Ability] %s triggered!", ability.getDisplayName());
        };
    }

    /**
     * Log ability trigger for debugging/analytics.
     */
    public void logAbilityTrigger(@Nonnull UUID playerId, @Nonnull AbilityType ability, String details) {
        logger.at(Level.FINE).log("[Ability] %s triggered %s: %s",
            playerId, ability.getDisplayName(), details);
    }

    // ========== Player Lifecycle ==========

    /**
     * Called when player connects - apply permanent buffs.
     */
    public void onPlayerReady(@Nonnull UUID playerId) {
        if (permanentBuffManager != null) {
            permanentBuffManager.applyUnlockedBuffs(playerId);
        }
    }

    /**
     * Called when player disconnects - clean up.
     */
    public void onPlayerDisconnect(@Nonnull UUID playerId) {
        // Clean up cache
        unlockedAbilitiesCache.remove(playerId);

        if (timedBuffManager != null) {
            timedBuffManager.removePlayer(playerId);
        }
        if (permanentBuffManager != null) {
            permanentBuffManager.removePlayer(playerId);
        }
    }

    /**
     * Called when player levels up - check for new permanent unlocks.
     */
    public void onLevelUp(@Nonnull UUID playerId, @Nonnull ProfessionType profession, int newLevel) {
        // Get previously unlocked abilities before invalidation
        Set<AbilityType> previouslyUnlocked = unlockedAbilitiesCache.get(playerId);
        if (previouslyUnlocked == null) {
            previouslyUnlocked = EnumSet.noneOf(AbilityType.class);
        }

        // Invalidate cache so new abilities are detected
        invalidateAbilityCache(playerId);

        // Rebuild cache and check for newly unlocked abilities
        Set<AbilityType> nowUnlocked = rebuildUnlockedAbilitiesCache(playerId);

        // Find newly unlocked abilities and show notifications
        for (AbilityType ability : nowUnlocked) {
            if (!previouslyUnlocked.contains(ability)) {
                // This ability was just unlocked!
                showAbilityUnlockNotification(playerId, ability);
            }
        }

        if (permanentBuffManager != null) {
            permanentBuffManager.checkNewUnlocks(playerId, profession, newLevel);
        }
    }

    /**
     * Show a notification when a player unlocks a new ability.
     */
    private void showAbilityUnlockNotification(@Nonnull UUID playerId, @Nonnull AbilityType ability) {
        if (notificationModule == null) {
            // Fallback to logging if notification module not available
            logger.at(Level.FINE).log("[Ability Unlock] Player %s unlocked: %s",
                playerId, ability.getDisplayName());
            return;
        }

        // Format the notification
        String title = "Ability Unlocked!";
        String subtitle = ability.getDisplayName() + " - " + ability.getDescription();

        // Use purple color for unlock notifications
        notificationModule.showUnlock(playerId, title, subtitle);

        logger.at(Level.FINE).log("[Ability Unlock] Player %s unlocked: %s (%s)",
            playerId, ability.getDisplayName(), ability.getProfession().getDisplayName());
    }
}
