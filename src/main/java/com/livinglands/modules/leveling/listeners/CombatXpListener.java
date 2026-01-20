package com.livinglands.modules.leveling.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;
import com.livinglands.modules.leveling.profession.ProfessionType;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener for combat events to award Combat XP.
 * Awards XP when players deal damage to entities and kill mobs.
 *
 * Note: Direct damage events are not currently available in the Hytale API.
 * Combat detection is handled via movement state detection similar to metabolism.
 * This is a placeholder for integration when damage/kill events are available.
 */
public class CombatXpListener {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;
    private final PlayerRegistry playerRegistry;

    public CombatXpListener(@Nonnull LevelingSystem system,
                            @Nonnull LevelingModuleConfig config,
                            @Nonnull HytaleLogger logger,
                            @Nonnull PlayerRegistry playerRegistry) {
        this.system = system;
        this.config = config;
        this.logger = logger;
        this.playerRegistry = playerRegistry;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Note: Damage events are not available in the current Hytale API.
        // Combat XP can be awarded via movement state detection (combat animations)
        // or when damage/kill events become available.
        //
        // Future integration:
        // - Register for Damage or EntityDeathEvent
        // - Track damage dealers for kill credit
        // - Award XP for damage dealt and kills

        logger.at(Level.FINE).log("Combat XP listener registered (damage events pending)");
    }

    /**
     * Award XP for dealing damage.
     * This method can be called externally when damage events are detected.
     *
     * @param playerId The player who dealt damage
     * @param damageAmount The amount of damage dealt
     */
    public void awardDamageXp(@Nonnull UUID playerId, float damageAmount) {
        int xp = Math.round(damageAmount * config.xpSources.damageXpPerPoint);
        if (xp <= 0) return;

        system.awardXp(playerId, ProfessionType.COMBAT, xp);

        logger.at(Level.FINE).log("Awarded %d Combat XP to player %s for dealing %.1f damage",
            xp, playerId, damageAmount);
    }

    /**
     * Award XP for killing a mob.
     * This method can be called externally when kill events are detected.
     *
     * @param playerId The player who made the kill
     * @param entityTypeId The type of entity that was killed
     */
    public void awardKillXp(@Nonnull UUID playerId, @Nonnull String entityTypeId) {
        int xp = config.xpSources.getMobXp(entityTypeId);
        if (xp <= 0) return;

        system.awardXp(playerId, ProfessionType.COMBAT, xp);

        logger.at(Level.FINE).log("Awarded %d Combat XP to player %s for killing %s",
            xp, playerId, entityTypeId);
    }
}
