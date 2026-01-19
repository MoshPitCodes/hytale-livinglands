package com.livinglands.modules.leveling.ability.handlers;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.leveling.ability.AbilitySystem;
import com.livinglands.modules.leveling.ability.AbilityType;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handler for building-related passive abilities.
 * Handles Material Saver ability.
 *
 * Note: Block placement events require ECS event registration.
 * This handler provides methods that can be called when events are available.
 */
public class BuildingAbilityHandler {

    private final AbilitySystem abilitySystem;
    private final PlayerRegistry playerRegistry;
    private final HytaleLogger logger;

    public BuildingAbilityHandler(@Nonnull AbilitySystem abilitySystem,
                                  @Nonnull PlayerRegistry playerRegistry,
                                  @Nonnull HytaleLogger logger) {
        this.abilitySystem = abilitySystem;
        this.playerRegistry = playerRegistry;
        this.logger = logger;
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        // Note: Block placement events require ECS registration.
        // This handler provides trigger methods for when events are available.
        logger.at(Level.INFO).log("Building ability handler registered (ECS events pending)");
    }

    /**
     * Check if Material Saver should trigger.
     *
     * @param playerId The building player
     * @return true if ability triggers
     */
    public boolean checkMaterialSaver(@Nonnull UUID playerId) {
        if (abilitySystem.shouldTrigger(playerId, AbilityType.MATERIAL_SAVER)) {
            abilitySystem.logAbilityTrigger(playerId, AbilityType.MATERIAL_SAVER, "Material refunded");
            return true;
        }
        return false;
    }
}
