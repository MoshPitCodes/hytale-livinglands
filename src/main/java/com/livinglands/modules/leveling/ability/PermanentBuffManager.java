package com.livinglands.modules.leveling.ability;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.PlayerSession;
import com.livinglands.core.notifications.NotificationModule;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;
import com.livinglands.core.util.SpeedManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Manages permanent passive ability buffs (Tier 3 abilities).
 * These are always-active stat bonuses that apply once unlocked at level 60.
 *
 * Permanent buffs:
 * - Battle Hardened (Combat): +10% max health
 * - Iron Constitution (Mining): +15% max stamina
 * - Nature's Endurance (Logging): +10% movement speed
 * - Master Builder (Building): +10% max stamina
 * - Survivalist (Gathering): -15% hunger/thirst depletion rate
 *
 * Features:
 * - Applied on player login if unlocked
 * - Applied when ability is newly unlocked (level up)
 * - Removed on player logout
 * - Stacks additively with metabolism buffs/debuffs
 */
public class PermanentBuffManager {

    // Modifier keys for ECS stat system
    private static final String HEALTH_MODIFIER_KEY = "livinglands_ability_health";
    private static final String STAMINA_MODIFIER_KEY = "livinglands_ability_stamina";
    private static final String SPEED_MODIFIER_KEY = "livinglands_ability_speed";

    private final PlayerRegistry playerRegistry;
    private final LevelingSystem levelingSystem;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;

    @Nullable
    private SpeedManager speedManager;
    @Nullable
    private NotificationModule notificationModule;

    // Track which permanent buffs are applied per player
    private final Map<UUID, Set<PermanentBuffType>> appliedBuffs = new ConcurrentHashMap<>();

    // Operation version numbers to handle race conditions with async ECS operations
    // Key: playerId:buffType -> version number. Higher version = more recent operation.
    // When a remove is queued, its version is stored. When apply runs, it checks if a newer remove exists.
    private final Map<String, AtomicLong> operationVersions = new ConcurrentHashMap<>();

    public PermanentBuffManager(@Nonnull PlayerRegistry playerRegistry,
                                @Nonnull LevelingSystem levelingSystem,
                                @Nonnull LevelingModuleConfig config,
                                @Nonnull HytaleLogger logger) {
        this.playerRegistry = playerRegistry;
        this.levelingSystem = levelingSystem;
        this.config = config;
        this.logger = logger;
    }

    public void setSpeedManager(@Nullable SpeedManager speedManager) {
        this.speedManager = speedManager;
    }

    public void setNotificationModule(@Nullable NotificationModule notificationModule) {
        this.notificationModule = notificationModule;
    }

    /**
     * Apply all unlocked permanent buffs for a player.
     * Called on player login/ready.
     *
     * This method first cleans up any stale modifiers that may have been persisted
     * by Hytale's save system from previous sessions where abilities were unlocked
     * but are no longer valid (e.g., mod data was reset).
     */
    public void applyUnlockedBuffs(@Nonnull UUID playerId) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty() || !sessionOpt.get().isEcsReady()) {
            return;
        }

        var session = sessionOpt.get();

        // First, clean up any stale ability modifiers that Hytale may have persisted
        // This handles the case where mod data was deleted but player save wasn't
        cleanupStaleModifiers(playerId, session);

        // Now check each profession's Tier 3 ability and apply if unlocked
        for (ProfessionType profession : ProfessionType.values()) {
            AbilityType tier3Ability = AbilityType.getAbilityForProfessionAndTier(
                profession, AbilityType.AbilityTier.TIER_3);

            if (tier3Ability == null) continue;

            // Check if unlocked
            var abilityConfig = config.getAbilityConfig(tier3Ability);
            if (abilityConfig == null || !abilityConfig.enabled) continue;

            int level = levelingSystem.getLevel(playerId, profession);
            if (abilityConfig.isUnlocked(level)) {
                applyPermanentBuff(playerId, session, tier3Ability, abilityConfig.effectStrength, false);
            }
        }
    }

    /**
     * Clean up any stale ability modifiers that may have been persisted by Hytale.
     * This removes modifiers for abilities that the player hasn't actually unlocked.
     */
    private void cleanupStaleModifiers(@Nonnull UUID playerId, @Nonnull PlayerSession session) {
        Ref<EntityStore> ref = session.getEntityRef();
        Store<EntityStore> store = session.getStore();
        World world = session.getWorld();

        if (ref == null || store == null || world == null) {
            return;
        }

        // Determine which ability modifiers should NOT exist based on current levels
        boolean shouldHaveHealthMod = false;
        boolean shouldHaveStaminaMod = false;

        // Check Combat (Battle Hardened = health)
        var combatConfig = config.getAbilityConfig(AbilityType.BATTLE_HARDENED);
        if (combatConfig != null && combatConfig.enabled) {
            int combatLevel = levelingSystem.getLevel(playerId, ProfessionType.COMBAT);
            if (combatConfig.isUnlocked(combatLevel)) {
                shouldHaveHealthMod = true;
            }
        }

        // Check Mining (Iron Constitution = stamina)
        var miningConfig = config.getAbilityConfig(AbilityType.IRON_CONSTITUTION);
        if (miningConfig != null && miningConfig.enabled) {
            int miningLevel = levelingSystem.getLevel(playerId, ProfessionType.MINING);
            if (miningConfig.isUnlocked(miningLevel)) {
                shouldHaveStaminaMod = true;
            }
        }

        // Check Building (Master Builder = stamina)
        var buildingConfig = config.getAbilityConfig(AbilityType.MASTER_BUILDER);
        if (buildingConfig != null && buildingConfig.enabled) {
            int buildingLevel = levelingSystem.getLevel(playerId, ProfessionType.BUILDING);
            if (buildingConfig.isUnlocked(buildingLevel)) {
                shouldHaveStaminaMod = true;
            }
        }

        // Note: Logging (Nature's Endurance = speed) is handled by SpeedManager, not ECS modifiers
        // Note: Gathering (Survivalist = metabolism) is handled by multiplier check, not ECS modifiers

        final boolean removeHealthMod = !shouldHaveHealthMod;
        final boolean removeStaminaMod = !shouldHaveStaminaMod;

        if (removeHealthMod || removeStaminaMod) {
            world.execute(() -> {
                try {
                    var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                    if (statMap == null) return;

                    if (removeHealthMod) {
                        var healthStatId = DefaultEntityStatTypes.getHealth();
                        // Try to remove - this is a no-op if modifier doesn't exist
                        statMap.removeModifier(EntityStatMap.Predictable.SELF, healthStatId, HEALTH_MODIFIER_KEY);
                        logger.at(Level.FINE).log("Cleaned up stale health ability modifier for %s", playerId);
                    }

                    if (removeStaminaMod) {
                        var staminaStatId = DefaultEntityStatTypes.getStamina();
                        statMap.removeModifier(EntityStatMap.Predictable.SELF, staminaStatId, STAMINA_MODIFIER_KEY);
                        logger.at(Level.FINE).log("Cleaned up stale stamina ability modifier for %s", playerId);
                    }
                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log("Failed to clean up stale modifiers for %s", playerId);
                }
            });
        }
    }

    /**
     * Check and apply/remove permanent buffs after level change.
     * Called after XP is awarded or level is set via admin command.
     * Handles both unlocking (level up to unlock level) and locking (level set below unlock level).
     *
     * Uses versioned operations to handle race conditions when level changes rapidly
     * (e.g., admin commands: 60 -> 59 -> 60). Each operation increments a version counter,
     * and async ECS operations check if they're still the latest before executing.
     */
    public void checkNewUnlocks(@Nonnull UUID playerId, @Nonnull ProfessionType profession, int newLevel) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty() || !sessionOpt.get().isEcsReady()) {
            return;
        }

        var session = sessionOpt.get();

        AbilityType tier3Ability = AbilityType.getAbilityForProfessionAndTier(
            profession, AbilityType.AbilityTier.TIER_3);

        if (tier3Ability == null) return;

        var abilityConfig = config.getAbilityConfig(tier3Ability);
        if (abilityConfig == null || !abilityConfig.enabled) return;

        PermanentBuffType buffType = getPermanentBuffType(tier3Ability);
        if (buffType == null) return;

        // Get or create version counter for this player+buff combination
        String versionKey = playerId.toString() + ":" + buffType.name();
        AtomicLong versionCounter = operationVersions.computeIfAbsent(versionKey, k -> new AtomicLong(0));

        // Check if unlocked (level >= unlock level)
        if (newLevel >= abilityConfig.unlockLevel) {
            // Increment version for this apply operation
            long applyVersion = versionCounter.incrementAndGet();

            // Apply buff - the version ensures this apply won't be undone by a stale remove
            applyPermanentBuffVersioned(playerId, session, tier3Ability, abilityConfig.effectStrength,
                newLevel == abilityConfig.unlockLevel, buffType, versionKey, applyVersion);
        } else {
            // Increment version for this remove operation
            long removeVersion = versionCounter.incrementAndGet();

            // Remove buff if level dropped below unlock level (e.g., admin command)
            Set<PermanentBuffType> playerBuffs = appliedBuffs.get(playerId);
            boolean wasTracked = playerBuffs != null && playerBuffs.contains(buffType);

            if (wasTracked) {
                removePermanentBuffEffectVersioned(playerId, session, buffType, versionKey, removeVersion);
                playerBuffs.remove(buffType);
                logger.at(Level.FINE).log("Removed permanent buff %s from %s (level dropped to %d)",
                    buffType, playerId, newLevel);
            }
        }
    }

    /**
     * Remove all permanent buffs for a player.
     * Called on player logout.
     */
    public void removeAllBuffs(@Nonnull UUID playerId) {
        var sessionOpt = playerRegistry.getSession(playerId);
        var session = sessionOpt.orElse(null);

        Set<PermanentBuffType> playerBuffs = appliedBuffs.remove(playerId);
        if (playerBuffs == null || playerBuffs.isEmpty()) return;

        for (PermanentBuffType buffType : playerBuffs) {
            removePermanentBuffEffect(playerId, session, buffType);
        }

        logger.at(Level.FINE).log("Removed all permanent buffs for %s", playerId);
    }

    /**
     * Check if a permanent buff is currently applied.
     */
    public boolean hasPermaBuff(@Nonnull UUID playerId, @Nonnull PermanentBuffType buffType) {
        Set<PermanentBuffType> playerBuffs = appliedBuffs.get(playerId);
        return playerBuffs != null && playerBuffs.contains(buffType);
    }

    /**
     * Clean up on player disconnect.
     */
    public void removePlayer(@Nonnull UUID playerId) {
        removeAllBuffs(playerId);

        // Clean up version counters for this player
        String playerIdPrefix = playerId.toString() + ":";
        operationVersions.keySet().removeIf(key -> key.startsWith(playerIdPrefix));
    }

    /**
     * Get the metabolism depletion multiplier for a player (for Survivalist ability).
     * Returns 1.0 if no reduction, or 0.85 for 15% reduction.
     */
    public float getMetabolismDepletionMultiplier(@Nonnull UUID playerId) {
        if (hasPermaBuff(playerId, PermanentBuffType.METABOLISM_REDUCTION)) {
            // Get the configured reduction amount
            var abilityConfig = config.getAbilityConfig(AbilityType.SURVIVALIST);
            if (abilityConfig != null) {
                return 1.0f - abilityConfig.effectStrength;  // e.g., 1.0 - 0.15 = 0.85
            }
            return 0.85f;  // Default 15% reduction
        }
        return 1.0f;
    }

    // ========== Private Methods ==========

    /**
     * Apply permanent buff without versioning (used for initial login).
     */
    private void applyPermanentBuff(UUID playerId, PlayerSession session,
                                    AbilityType ability, float strength, boolean showMessage) {
        PermanentBuffType buffType = getPermanentBuffType(ability);
        if (buffType == null) return;

        // Check if already applied
        Set<PermanentBuffType> playerBuffs = appliedBuffs.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        if (playerBuffs.contains(buffType)) {
            logger.at(Level.FINE).log("Permanent buff %s already applied to %s, skipping",
                buffType, playerId);
            return;  // Already applied
        }

        // Apply the effect
        logger.at(Level.FINE).log("[PermanentBuff] Applying buff %s to %s (ability=%s, strength=%.0f%%)",
            buffType, playerId, ability.getDisplayName(), strength * 100);
        applyPermanentBuffEffect(playerId, session, buffType, strength);
        playerBuffs.add(buffType);

        // Send unlock message if newly unlocked
        if (showMessage) {
            sendUnlockMessage(session, ability);
        }
    }

    /**
     * Apply permanent buff with version checking to handle race conditions.
     * The version is checked inside the async ECS operation to ensure stale applies don't execute.
     */
    private void applyPermanentBuffVersioned(UUID playerId, PlayerSession session,
                                              AbilityType ability, float strength, boolean showMessage,
                                              PermanentBuffType buffType, String versionKey, long operationVersion) {
        // Track in appliedBuffs immediately (optimistic)
        Set<PermanentBuffType> playerBuffs = appliedBuffs.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        // Even if already tracked, we need to re-apply the ECS effect if version is newer
        // This handles: apply(v1) -> remove(v2) -> apply(v3)
        // The v3 apply needs to actually re-apply the ECS modifier even though tracking says it's applied
        boolean wasAlreadyTracked = playerBuffs.contains(buffType);
        playerBuffs.add(buffType);

        // Apply the versioned effect
        logger.at(Level.FINE).log("[PermanentBuff] Applying versioned buff %s to %s (version=%d, wasTracked=%s)",
            buffType, playerId, operationVersion, wasAlreadyTracked);
        applyPermanentBuffEffectVersioned(playerId, session, buffType, strength, versionKey, operationVersion);

        // Send unlock message if newly unlocked (and not already tracked)
        if (showMessage && !wasAlreadyTracked) {
            sendUnlockMessage(session, ability);
        }
    }

    private void applyPermanentBuffEffect(UUID playerId, PlayerSession session,
                                          PermanentBuffType buffType, float strength) {
        switch (buffType) {
            case HEALTH_BONUS -> applyHealthModifier(session, strength, null, 0);
            case STAMINA_BONUS -> applyStaminaModifier(session, strength, null, 0);
            case SPEED_BONUS -> {
                if (speedManager != null) {
                    // Permanent speed bonus stacks with other buffs
                    float currentBuff = speedManager.getBuffMultiplier(playerId);
                    speedManager.setBuffMultiplier(playerId, currentBuff + strength);
                }
            }
            case METABOLISM_REDUCTION -> {
                // Handled via getMetabolismDepletionMultiplier() in metabolism system
                logger.at(Level.FINE).log("Survivalist ability applied - metabolism depletion reduced by %.0f%%",
                    strength * 100);
            }
        }
    }

    /**
     * Apply permanent buff effect with version checking.
     * Only executes if this operation is still the latest (highest version).
     */
    private void applyPermanentBuffEffectVersioned(UUID playerId, PlayerSession session,
                                                    PermanentBuffType buffType, float strength,
                                                    String versionKey, long operationVersion) {
        switch (buffType) {
            case HEALTH_BONUS -> applyHealthModifier(session, strength, versionKey, operationVersion);
            case STAMINA_BONUS -> applyStaminaModifier(session, strength, versionKey, operationVersion);
            case SPEED_BONUS -> {
                if (speedManager != null) {
                    // Check version before applying
                    AtomicLong versionCounter = operationVersions.get(versionKey);
                    if (versionCounter != null && versionCounter.get() != operationVersion) {
                        logger.at(Level.FINE).log("Skipping stale speed buff apply (version %d, current %d)",
                            operationVersion, versionCounter.get());
                        return;
                    }
                    // Permanent speed bonus stacks with other buffs
                    float currentBuff = speedManager.getBuffMultiplier(playerId);
                    speedManager.setBuffMultiplier(playerId, currentBuff + strength);
                }
            }
            case METABOLISM_REDUCTION -> {
                // Handled via getMetabolismDepletionMultiplier() in metabolism system
                logger.at(Level.FINE).log("Survivalist ability applied - metabolism depletion reduced by %.0f%%",
                    strength * 100);
            }
        }
    }

    private void removePermanentBuffEffect(UUID playerId, @Nullable PlayerSession session,
                                           PermanentBuffType buffType) {
        switch (buffType) {
            case HEALTH_BONUS -> {
                if (session != null && session.isEcsReady()) {
                    removeHealthModifier(session, null, 0);
                }
            }
            case STAMINA_BONUS -> {
                if (session != null && session.isEcsReady()) {
                    removeStaminaModifier(session, null, 0);
                }
            }
            case SPEED_BONUS -> {
                if (speedManager != null) {
                    // Get the configured strength to remove
                    var abilityConfig = config.getAbilityConfig(AbilityType.NATURES_ENDURANCE);
                    float strength = abilityConfig != null ? abilityConfig.effectStrength : 0.10f;
                    float currentBuff = speedManager.getBuffMultiplier(playerId);
                    speedManager.setBuffMultiplier(playerId, Math.max(0f, currentBuff - strength));
                }
            }
            case METABOLISM_REDUCTION -> {
                // No cleanup needed - handled via multiplier check
            }
        }
    }

    /**
     * Remove permanent buff effect with version checking.
     * Only executes if this operation is still the latest (highest version).
     */
    private void removePermanentBuffEffectVersioned(UUID playerId, @Nullable PlayerSession session,
                                                     PermanentBuffType buffType,
                                                     String versionKey, long operationVersion) {
        switch (buffType) {
            case HEALTH_BONUS -> {
                if (session != null && session.isEcsReady()) {
                    removeHealthModifier(session, versionKey, operationVersion);
                }
            }
            case STAMINA_BONUS -> {
                if (session != null && session.isEcsReady()) {
                    removeStaminaModifier(session, versionKey, operationVersion);
                }
            }
            case SPEED_BONUS -> {
                if (speedManager != null) {
                    // Check version before removing
                    AtomicLong versionCounter = operationVersions.get(versionKey);
                    if (versionCounter != null && versionCounter.get() != operationVersion) {
                        logger.at(Level.FINE).log("Skipping stale speed buff remove (version %d, current %d)",
                            operationVersion, versionCounter.get());
                        return;
                    }
                    // Get the configured strength to remove
                    var abilityConfig = config.getAbilityConfig(AbilityType.NATURES_ENDURANCE);
                    float strength = abilityConfig != null ? abilityConfig.effectStrength : 0.10f;
                    float currentBuff = speedManager.getBuffMultiplier(playerId);
                    speedManager.setBuffMultiplier(playerId, Math.max(0f, currentBuff - strength));
                }
            }
            case METABOLISM_REDUCTION -> {
                // No cleanup needed - handled via multiplier check
            }
        }
    }

    /**
     * Apply health modifier with optional version checking.
     * @param versionKey If non-null, check version before applying. If stale, skip.
     * @param operationVersion The version of this operation.
     */
    private void applyHealthModifier(PlayerSession session, float strength,
                                      @Nullable String versionKey, long operationVersion) {
        Ref<EntityStore> ref = session.getEntityRef();
        Store<EntityStore> store = session.getStore();
        World world = session.getWorld();

        if (ref == null || store == null || world == null) {
            return;
        }

        world.execute(() -> {
            try {
                // Version check inside async block - this is the key to fixing the race condition
                if (versionKey != null) {
                    AtomicLong versionCounter = operationVersions.get(versionKey);
                    if (versionCounter != null && versionCounter.get() != operationVersion) {
                        logger.at(Level.FINE).log("Skipping stale health apply (version %d, current %d)",
                            operationVersion, versionCounter.get());
                        return;
                    }
                }

                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var healthStatId = DefaultEntityStatTypes.getHealth();

                    // First, remove any existing modifier with this key to ensure clean state
                    // This handles cases where the modifier persisted from a previous session
                    statMap.removeModifier(healthStatId, HEALTH_MODIFIER_KEY);

                    // Get current health values AFTER removing old modifier (if any)
                    var healthStat = statMap.get(healthStatId);
                    float currentHealth = healthStat != null ? healthStat.get() : 0;
                    float maxHealthBefore = healthStat != null ? healthStat.getMax() : 0;

                    // Calculate the absolute bonus based on BASE health (100)
                    // Using ADDITIVE so it stacks with MULTIPLICATIVE buffs (Well Fed)
                    float baseHealth = 100.0f;  // Hytale default player health
                    float absoluteBonus = baseHealth * strength;  // e.g., 100 * 0.10 = 10

                    var modifier = new StaticModifier(
                        Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.ADDITIVE,
                        absoluteBonus  // e.g., +10 health
                    );
                    // Use SELF predictable to ensure client receives the stat update
                    statMap.putModifier(EntityStatMap.Predictable.SELF, healthStatId, HEALTH_MODIFIER_KEY, modifier);

                    // Re-fetch health stat after modifier applied
                    healthStat = statMap.get(healthStatId);
                    float maxHealthAfter = healthStat != null ? healthStat.getMax() : 0;

                    // Scale current health proportionally to the new max
                    // This ensures the player "gains" the bonus health immediately
                    if (maxHealthBefore > 0 && maxHealthAfter > maxHealthBefore) {
                        float healthRatio = currentHealth / maxHealthBefore;
                        float newCurrentHealth = healthRatio * maxHealthAfter;
                        statMap.setStatValue(healthStatId, newCurrentHealth);
                    }

                    logger.at(Level.FINE).log("[PermanentBuff] Health modifier applied: +%.0f (%.0f%%) -> max %.0f -> %.0f",
                        absoluteBonus, strength * 100, maxHealthBefore, maxHealthAfter);
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Failed to apply health modifier: " + e.getMessage());
            }
        });
    }

    /**
     * Remove health modifier with optional version checking.
     * @param versionKey If non-null, check version before removing. If stale, skip.
     * @param operationVersion The version of this operation.
     */
    private void removeHealthModifier(PlayerSession session,
                                       @Nullable String versionKey, long operationVersion) {
        Ref<EntityStore> ref = session.getEntityRef();
        Store<EntityStore> store = session.getStore();
        World world = session.getWorld();

        if (ref == null || store == null || world == null) {
            return;
        }

        world.execute(() -> {
            try {
                // Version check inside async block
                if (versionKey != null) {
                    AtomicLong versionCounter = operationVersions.get(versionKey);
                    if (versionCounter != null && versionCounter.get() != operationVersion) {
                        logger.at(Level.FINE).log("Skipping stale health remove (version %d, current %d)",
                            operationVersion, versionCounter.get());
                        return;
                    }
                }

                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var healthStatId = DefaultEntityStatTypes.getHealth();
                    // Use SELF predictable to ensure client receives the stat update
                    statMap.removeModifier(EntityStatMap.Predictable.SELF, healthStatId, HEALTH_MODIFIER_KEY);
                    logger.at(Level.FINE).log("Health modifier removed");
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Failed to remove health modifier: " + e.getMessage());
            }
        });
    }

    /**
     * Apply stamina modifier with optional version checking.
     * @param versionKey If non-null, check version before applying. If stale, skip.
     * @param operationVersion The version of this operation.
     */
    private void applyStaminaModifier(PlayerSession session, float strength,
                                       @Nullable String versionKey, long operationVersion) {
        Ref<EntityStore> ref = session.getEntityRef();
        Store<EntityStore> store = session.getStore();
        World world = session.getWorld();

        if (ref == null || store == null || world == null) return;

        world.execute(() -> {
            try {
                // Version check inside async block
                if (versionKey != null) {
                    AtomicLong versionCounter = operationVersions.get(versionKey);
                    if (versionCounter != null && versionCounter.get() != operationVersion) {
                        logger.at(Level.FINE).log("Skipping stale stamina apply (version %d, current %d)",
                            operationVersion, versionCounter.get());
                        return;
                    }
                }

                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var staminaStatId = DefaultEntityStatTypes.getStamina();

                    // First, remove any existing modifier with this key to ensure clean state
                    statMap.removeModifier(staminaStatId, STAMINA_MODIFIER_KEY);

                    // Get current stamina values AFTER removing old modifier (if any)
                    var staminaStat = statMap.get(staminaStatId);
                    float currentStamina = staminaStat != null ? staminaStat.get() : 0;
                    float maxStaminaBefore = staminaStat != null ? staminaStat.getMax() : 0;

                    // Calculate the absolute bonus based on BASE stamina (10)
                    // Using ADDITIVE so it stacks with MULTIPLICATIVE buffs (Hydrated)
                    float baseStamina = 10.0f;  // Hytale default player stamina
                    float absoluteBonus = baseStamina * strength;  // e.g., 10 * 0.15 = 1.5

                    var modifier = new StaticModifier(
                        Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.ADDITIVE,
                        absoluteBonus  // e.g., +1.5 stamina
                    );
                    // Use SELF predictable to ensure client receives the stat update
                    statMap.putModifier(EntityStatMap.Predictable.SELF, staminaStatId, STAMINA_MODIFIER_KEY, modifier);

                    // Re-fetch stamina stat after modifier applied
                    staminaStat = statMap.get(staminaStatId);
                    float maxStaminaAfter = staminaStat != null ? staminaStat.getMax() : 0;

                    // Scale current stamina proportionally to the new max
                    if (maxStaminaBefore > 0 && maxStaminaAfter > maxStaminaBefore) {
                        float staminaRatio = currentStamina / maxStaminaBefore;
                        float newCurrentStamina = staminaRatio * maxStaminaAfter;
                        statMap.setStatValue(staminaStatId, newCurrentStamina);
                    }

                    logger.at(Level.FINE).log("[PermanentBuff] Stamina modifier applied: +%.1f (%.0f%% of base) -> max %.1f -> %.1f (version %d)",
                        absoluteBonus, strength * 100, maxStaminaBefore, maxStaminaAfter, operationVersion);
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Failed to apply stamina modifier: " + e.getMessage());
            }
        });
    }

    /**
     * Remove stamina modifier with optional version checking.
     * @param versionKey If non-null, check version before removing. If stale, skip.
     * @param operationVersion The version of this operation.
     */
    private void removeStaminaModifier(PlayerSession session,
                                        @Nullable String versionKey, long operationVersion) {
        Ref<EntityStore> ref = session.getEntityRef();
        Store<EntityStore> store = session.getStore();
        World world = session.getWorld();

        if (ref == null || store == null || world == null) return;

        world.execute(() -> {
            try {
                // Version check inside async block
                if (versionKey != null) {
                    AtomicLong versionCounter = operationVersions.get(versionKey);
                    if (versionCounter != null && versionCounter.get() != operationVersion) {
                        logger.at(Level.FINE).log("Skipping stale stamina remove (version %d, current %d)",
                            operationVersion, versionCounter.get());
                        return;
                    }
                }

                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var staminaStatId = DefaultEntityStatTypes.getStamina();
                    // Use SELF predictable to ensure client receives the stat update
                    statMap.removeModifier(EntityStatMap.Predictable.SELF, staminaStatId, STAMINA_MODIFIER_KEY);
                    logger.at(Level.FINE).log("Stamina modifier removed (version %d)", operationVersion);
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Failed to remove stamina modifier: " + e.getMessage());
            }
        });
    }

    private void sendUnlockMessage(PlayerSession session, AbilityType ability) {
        if (notificationModule == null) return;

        String message = String.format("[Ability Unlocked] %s - %s",
            ability.getDisplayName(), ability.getDescription());
        notificationModule.sendChatWarning(session.getPlayerId(), message);
    }

    @Nullable
    private PermanentBuffType getPermanentBuffType(AbilityType ability) {
        return switch (ability) {
            case BATTLE_HARDENED -> PermanentBuffType.HEALTH_BONUS;
            case IRON_CONSTITUTION, MASTER_BUILDER -> PermanentBuffType.STAMINA_BONUS;
            case NATURES_ENDURANCE -> PermanentBuffType.SPEED_BONUS;
            case SURVIVALIST -> PermanentBuffType.METABOLISM_REDUCTION;
            default -> null;
        };
    }

    /**
     * Types of permanent buffs.
     */
    public enum PermanentBuffType {
        HEALTH_BONUS,          // Battle Hardened
        STAMINA_BONUS,         // Iron Constitution, Master Builder
        SPEED_BONUS,           // Nature's Endurance
        METABOLISM_REDUCTION   // Survivalist
    }
}
