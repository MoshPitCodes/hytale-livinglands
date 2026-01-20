package com.livinglands.modules.metabolism;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Centralized manager for player movement speed modifications.
 *
 * This class solves the race condition where multiple systems (DebuffsSystem, BuffsSystem)
 * were independently modifying baseSpeed, causing flickering speed changes.
 *
 * Now all speed modifiers are tracked here and combined into a single multiplier
 * that is applied once per tick.
 */
public class SpeedManager {

    private final HytaleLogger logger;

    // Track the true original base speed for each player (captured on first modification)
    private final Map<UUID, Float> originalBaseSpeeds = new ConcurrentHashMap<>();

    // Track current multipliers from different sources
    private final Map<UUID, Float> energyDebuffMultipliers = new ConcurrentHashMap<>();
    private final Map<UUID, Float> thirstDebuffMultipliers = new ConcurrentHashMap<>();
    private final Map<UUID, Float> buffMultipliers = new ConcurrentHashMap<>();

    // Track last applied combined multiplier to avoid redundant updates
    private final Map<UUID, Float> lastAppliedMultipliers = new ConcurrentHashMap<>();

    public SpeedManager(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Sets the energy debuff speed multiplier for a player.
     *
     * @param playerId The player's UUID
     * @param multiplier The multiplier (1.0 = no change, 0.6 = 60% speed)
     */
    public void setEnergyDebuffMultiplier(UUID playerId, float multiplier) {
        if (multiplier >= 1.0f) {
            energyDebuffMultipliers.remove(playerId);
        } else {
            energyDebuffMultipliers.put(playerId, multiplier);
        }
    }

    /**
     * Sets the thirst debuff speed multiplier for a player.
     *
     * @param playerId The player's UUID
     * @param multiplier The multiplier (1.0 = no change, 0.45 = 45% speed)
     */
    public void setThirstDebuffMultiplier(UUID playerId, float multiplier) {
        if (multiplier >= 1.0f) {
            thirstDebuffMultipliers.remove(playerId);
        } else {
            thirstDebuffMultipliers.put(playerId, multiplier);
        }
    }

    /**
     * Sets the buff speed multiplier for a player.
     *
     * @param playerId The player's UUID
     * @param multiplier The multiplier (1.0 = no change, 1.2 = 120% speed)
     */
    public void setBuffMultiplier(UUID playerId, float multiplier) {
        if (multiplier <= 1.0f) {
            buffMultipliers.remove(playerId);
        } else {
            buffMultipliers.put(playerId, multiplier);
        }
    }

    /**
     * Clears all speed modifiers for a player.
     * Called when buffs are suppressed by debuffs.
     */
    public void clearBuffMultiplier(UUID playerId) {
        buffMultipliers.remove(playerId);
    }

    /**
     * Clears the energy debuff multiplier for a player.
     */
    public void clearEnergyDebuffMultiplier(UUID playerId) {
        energyDebuffMultipliers.remove(playerId);
    }

    /**
     * Clears the thirst debuff multiplier for a player.
     */
    public void clearThirstDebuffMultiplier(UUID playerId) {
        thirstDebuffMultipliers.remove(playerId);
    }

    /**
     * Calculates the combined speed multiplier for a player.
     * Debuffs and buffs are multiplicative.
     *
     * @param playerId The player's UUID
     * @return The combined multiplier
     */
    public float getCombinedMultiplier(UUID playerId) {
        float multiplier = 1.0f;

        // Apply debuffs (reduce speed)
        Float energyMult = energyDebuffMultipliers.get(playerId);
        if (energyMult != null) {
            multiplier *= energyMult;
        }

        Float thirstMult = thirstDebuffMultipliers.get(playerId);
        if (thirstMult != null) {
            multiplier *= thirstMult;
        }

        // Apply buffs (increase speed) - only if no debuffs are reducing speed
        Float buffMult = buffMultipliers.get(playerId);
        if (buffMult != null && multiplier >= 1.0f) {
            multiplier *= buffMult;
        }

        return multiplier;
    }

    /**
     * Checks if a player has any active speed modifiers.
     */
    public boolean hasActiveModifiers(UUID playerId) {
        return energyDebuffMultipliers.containsKey(playerId) ||
               thirstDebuffMultipliers.containsKey(playerId) ||
               buffMultipliers.containsKey(playerId);
    }

    /**
     * Applies the combined speed modifier to the player.
     * Should be called once per tick after all systems have set their multipliers.
     *
     * @param playerId The player's UUID
     * @param ref The entity reference
     * @param store The entity store
     * @param world The world for thread-safe execution
     */
    public void applySpeedModifier(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        float combinedMultiplier = getCombinedMultiplier(playerId);

        // Check if multiplier changed significantly (avoid spamming updates)
        Float lastApplied = lastAppliedMultipliers.get(playerId);
        if (lastApplied != null && Math.abs(lastApplied - combinedMultiplier) < 0.01f) {
            return; // No significant change, skip update
        }

        world.execute(() -> {
            try {
                var movementManager = store.getComponent(ref, MovementManager.getComponentType());
                if (movementManager == null) {
                    return;
                }

                var settings = movementManager.getSettings();
                if (settings == null) {
                    return;
                }

                // Store original base speed if not already stored
                if (!originalBaseSpeeds.containsKey(playerId)) {
                    originalBaseSpeeds.put(playerId, settings.baseSpeed);
                    logger.at(Level.FINE).log("SpeedManager: Stored original base speed for player %s: %.2f",
                        playerId, settings.baseSpeed);
                }

                float originalSpeed = originalBaseSpeeds.get(playerId);
                float newSpeed = originalSpeed * combinedMultiplier;

                // Apply the modified speed
                settings.baseSpeed = newSpeed;
                lastAppliedMultipliers.put(playerId, combinedMultiplier);

                // Sync the movement settings to the client
                var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef != null) {
                    movementManager.update(playerRef.getPacketHandler());
                }

                logger.at(Level.FINE).log("SpeedManager: Applied speed to player %s: %.2f -> %.2f (%.0f%% combined)",
                    playerId, originalSpeed, newSpeed, combinedMultiplier * 100);

            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("SpeedManager: Error applying speed modifier for player %s", playerId);
            }
        });
    }

    /**
     * Restores original speed for a player if no modifiers are active.
     *
     * @param playerId The player's UUID
     * @param ref The entity reference
     * @param store The entity store
     * @param world The world for thread-safe execution
     */
    public void restoreOriginalSpeedIfNoModifiers(UUID playerId, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        if (hasActiveModifiers(playerId)) {
            return; // Still have modifiers, don't restore
        }

        if (!originalBaseSpeeds.containsKey(playerId)) {
            return; // No original speed stored
        }

        world.execute(() -> {
            try {
                var movementManager = store.getComponent(ref, MovementManager.getComponentType());
                if (movementManager == null) {
                    return;
                }

                var settings = movementManager.getSettings();
                if (settings == null) {
                    return;
                }

                float originalSpeed = originalBaseSpeeds.get(playerId);
                settings.baseSpeed = originalSpeed;

                // Clean up tracking
                originalBaseSpeeds.remove(playerId);
                lastAppliedMultipliers.remove(playerId);

                // Sync the movement settings to the client
                var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef != null) {
                    movementManager.update(playerRef.getPacketHandler());
                }

                logger.at(Level.FINE).log("SpeedManager: Restored original speed for player %s: %.2f",
                    playerId, originalSpeed);

            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("SpeedManager: Error restoring speed for player %s", playerId);
            }
        });
    }

    /**
     * Cleans up all tracking data for a player (on disconnect).
     */
    public void removePlayer(UUID playerId) {
        originalBaseSpeeds.remove(playerId);
        energyDebuffMultipliers.remove(playerId);
        thirstDebuffMultipliers.remove(playerId);
        buffMultipliers.remove(playerId);
        lastAppliedMultipliers.remove(playerId);
    }
}
