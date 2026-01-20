package com.livinglands.modules.leveling.ability;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.PlayerSession;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;
import com.livinglands.core.util.SpeedManager;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    // Track which permanent buffs are applied per player
    private final Map<UUID, Set<PermanentBuffType>> appliedBuffs = new ConcurrentHashMap<>();

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

    /**
     * Apply all unlocked permanent buffs for a player.
     * Called on player login/ready.
     */
    public void applyUnlockedBuffs(@Nonnull UUID playerId) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty() || !sessionOpt.get().isEcsReady()) {
            return;
        }

        var session = sessionOpt.get();

        // Check each profession's Tier 3 ability
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
     * Check and apply/remove permanent buffs after level change.
     * Called after XP is awarded or level is set via admin command.
     * Handles both unlocking (level up to unlock level) and locking (level set below unlock level).
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

        // Check if unlocked (level >= unlock level)
        if (newLevel >= abilityConfig.unlockLevel) {
            // Apply buff if not already applied
            applyPermanentBuff(playerId, session, tier3Ability, abilityConfig.effectStrength,
                newLevel == abilityConfig.unlockLevel); // Show message only if just unlocked
        } else {
            // Remove buff if level dropped below unlock level (e.g., admin command)
            Set<PermanentBuffType> playerBuffs = appliedBuffs.get(playerId);
            if (playerBuffs != null && playerBuffs.contains(buffType)) {
                removePermanentBuffEffect(playerId, session, buffType);
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
        logger.at(Level.FINE).log("Applying permanent buff %s to %s (ability=%s, strength=%.0f%%)",
            buffType, playerId, ability.getDisplayName(), strength * 100);
        applyPermanentBuffEffect(playerId, session, buffType, strength);
        playerBuffs.add(buffType);

        // Send unlock message if newly unlocked
        if (showMessage) {
            sendUnlockMessage(session, ability);
        }
    }

    private void applyPermanentBuffEffect(UUID playerId, PlayerSession session,
                                          PermanentBuffType buffType, float strength) {
        switch (buffType) {
            case HEALTH_BONUS -> applyHealthModifier(session, strength);
            case STAMINA_BONUS -> applyStaminaModifier(session, strength);
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

    private void removePermanentBuffEffect(UUID playerId, @Nullable PlayerSession session,
                                           PermanentBuffType buffType) {
        switch (buffType) {
            case HEALTH_BONUS -> {
                if (session != null && session.isEcsReady()) {
                    removeHealthModifier(session);
                }
            }
            case STAMINA_BONUS -> {
                if (session != null && session.isEcsReady()) {
                    removeStaminaModifier(session);
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

    private void applyHealthModifier(PlayerSession session, float strength) {
        Ref<EntityStore> ref = session.getEntityRef();
        Store<EntityStore> store = session.getStore();
        World world = session.getWorld();

        if (ref == null || store == null || world == null) return;

        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var healthStatId = DefaultEntityStatTypes.getHealth();

                    // Get current health values before applying modifier
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
                    statMap.putModifier(healthStatId, HEALTH_MODIFIER_KEY, modifier);

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

                    logger.at(Level.FINE).log("Health modifier applied: +%.0f (%.0f%% of base) -> max %.0f -> %.0f",
                        absoluteBonus, strength * 100, maxHealthBefore, maxHealthAfter);
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Failed to apply health modifier: " + e.getMessage());
            }
        });
    }

    private void removeHealthModifier(PlayerSession session) {
        Ref<EntityStore> ref = session.getEntityRef();
        Store<EntityStore> store = session.getStore();
        World world = session.getWorld();

        if (ref == null || store == null || world == null) return;

        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var healthStatId = DefaultEntityStatTypes.getHealth();
                    statMap.removeModifier(healthStatId, HEALTH_MODIFIER_KEY);
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Failed to remove health modifier: " + e.getMessage());
            }
        });
    }

    private void applyStaminaModifier(PlayerSession session, float strength) {
        Ref<EntityStore> ref = session.getEntityRef();
        Store<EntityStore> store = session.getStore();
        World world = session.getWorld();

        if (ref == null || store == null || world == null) return;

        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var staminaStatId = DefaultEntityStatTypes.getStamina();

                    // Get current stamina values before applying modifier
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
                    statMap.putModifier(staminaStatId, STAMINA_MODIFIER_KEY, modifier);

                    // Re-fetch stamina stat after modifier applied
                    staminaStat = statMap.get(staminaStatId);
                    float maxStaminaAfter = staminaStat != null ? staminaStat.getMax() : 0;

                    // Scale current stamina proportionally to the new max
                    if (maxStaminaBefore > 0 && maxStaminaAfter > maxStaminaBefore) {
                        float staminaRatio = currentStamina / maxStaminaBefore;
                        float newCurrentStamina = staminaRatio * maxStaminaAfter;
                        statMap.setStatValue(staminaStatId, newCurrentStamina);
                    }

                    logger.at(Level.FINE).log("Stamina modifier applied: +%.1f (%.0f%% of base) -> max %.1f -> %.1f",
                        absoluteBonus, strength * 100, maxStaminaBefore, maxStaminaAfter);
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Failed to apply stamina modifier: " + e.getMessage());
            }
        });
    }

    private void removeStaminaModifier(PlayerSession session) {
        Ref<EntityStore> ref = session.getEntityRef();
        Store<EntityStore> store = session.getStore();
        World world = session.getWorld();

        if (ref == null || store == null || world == null) return;

        world.execute(() -> {
            try {
                var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    var staminaStatId = DefaultEntityStatTypes.getStamina();
                    statMap.removeModifier(staminaStatId, STAMINA_MODIFIER_KEY);
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).log("Failed to remove stamina modifier: " + e.getMessage());
            }
        });
    }

    private void sendUnlockMessage(PlayerSession session, AbilityType ability) {
        Player player = session.getPlayer();
        if (player == null) return;

        String message = String.format("[Ability Unlocked] %s - %s",
            ability.getDisplayName(), ability.getDescription());
        player.sendMessage(Message.raw(message).color(ColorUtil.getHexColor("gold")));
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
