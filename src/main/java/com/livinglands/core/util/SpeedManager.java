package com.livinglands.core.util;

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
 *
 * Located in CoreModule for shared access by:
 * - MetabolismModule (buffs/debuffs)
 * - LevelingModule (ability speed buffs)
 */
public class SpeedManager {

    private final HytaleLogger logger;

    // Track the true original base speed for each player (captured on first modification)
    private final Map<UUID, Float> originalBaseSpeeds = new ConcurrentHashMap<>();

    // Track current multipliers from different sources
    private final Map<UUID, Float> energyDebuffMultipliers = new ConcurrentHashMap<>();
    private final Map<UUID, Float> thirstDebuffMultipliers = new ConcurrentHashMap<>();
    private final Map<UUID, Float> buffMultipliers = new ConcurrentHashMap<>();

    // Track ability-based speed multipliers (from leveling abilities)
    private final Map<UUID, Float> abilitySpeedMultipliers = new ConcurrentHashMap<>();

    // Track permanent speed multipliers (from Tier 3 abilities)
    private final Map<UUID, Float> permanentSpeedMultipliers = new ConcurrentHashMap<>();

    // Track last applied combined multiplier to avoid redundant updates
    private final Map<UUID, Float> lastAppliedMultipliers = new ConcurrentHashMap<>();

    public SpeedManager(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    // ========== Debuff Multipliers (from Metabolism) ==========

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

    // ========== Buff Multipliers (from Metabolism) ==========

    /**
     * Sets the buff speed multiplier for a player (metabolism buffs like "Energized").
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
     * Gets the current buff multiplier for a player.
     *
     * @param playerId The player's UUID
     * @return The current buff multiplier (0.0 if none set)
     */
    public float getBuffMultiplier(UUID playerId) {
        Float multiplier = buffMultipliers.get(playerId);
        return multiplier != null ? multiplier : 0.0f;
    }

    /**
     * Atomically adds a buff multiplier to the player's existing buff multiplier.
     * This prevents race conditions when multiple buffs are being applied simultaneously.
     *
     * @param playerId The player's UUID
     * @param additionalMultiplier The multiplier to add (e.g., 0.2 for +20% speed)
     * @return The new combined buff multiplier
     */
    public float addBuffMultiplier(UUID playerId, float additionalMultiplier) {
        return buffMultipliers.compute(playerId, (key, currentValue) -> {
            float current = (currentValue != null) ? currentValue : 1.0f;
            float newValue = current + additionalMultiplier;
            // If the new value is <= 1.0, return null to remove the entry
            return (newValue <= 1.0f) ? null : newValue;
        });
    }

    /**
     * Clears the metabolism buff multiplier for a player.
     */
    public void clearBuffMultiplier(UUID playerId) {
        buffMultipliers.remove(playerId);
    }

    // ========== Ability Multipliers (from Leveling) ==========

    /**
     * Sets a temporary ability speed multiplier (e.g., Adrenaline Rush).
     *
     * @param playerId The player's UUID
     * @param multiplier The multiplier (e.g., 1.2 for +20% speed)
     */
    public void setAbilitySpeedMultiplier(UUID playerId, float multiplier) {
        if (multiplier <= 1.0f) {
            abilitySpeedMultipliers.remove(playerId);
        } else {
            abilitySpeedMultipliers.put(playerId, multiplier);
        }
    }

    /**
     * Gets the current ability speed multiplier for a player.
     *
     * @param playerId The player's UUID
     * @return The current ability multiplier (1.0 if none set)
     */
    public float getAbilitySpeedMultiplier(UUID playerId) {
        Float multiplier = abilitySpeedMultipliers.get(playerId);
        return multiplier != null ? multiplier : 1.0f;
    }

    /**
     * Clears the ability speed multiplier for a player.
     */
    public void clearAbilitySpeedMultiplier(UUID playerId) {
        abilitySpeedMultipliers.remove(playerId);
    }

    // ========== Permanent Multipliers (from Tier 3 Abilities) ==========

    /**
     * Sets a permanent speed multiplier (e.g., Nature's Endurance +10%).
     * These persist until the player logs out or loses the ability.
     *
     * @param playerId The player's UUID
     * @param multiplier The multiplier (e.g., 1.1 for +10% speed)
     */
    public void setPermanentSpeedMultiplier(UUID playerId, float multiplier) {
        if (multiplier <= 1.0f) {
            permanentSpeedMultipliers.remove(playerId);
        } else {
            permanentSpeedMultipliers.put(playerId, multiplier);
        }
    }

    /**
     * Gets the permanent speed multiplier for a player.
     *
     * @param playerId The player's UUID
     * @return The permanent multiplier (1.0 if none set)
     */
    public float getPermanentSpeedMultiplier(UUID playerId) {
        Float multiplier = permanentSpeedMultipliers.get(playerId);
        return multiplier != null ? multiplier : 1.0f;
    }

    /**
     * Clears the permanent speed multiplier for a player.
     */
    public void clearPermanentSpeedMultiplier(UUID playerId) {
        permanentSpeedMultipliers.remove(playerId);
    }

    // ========== Combined Calculations ==========

    /**
     * Calculates the combined speed multiplier for a player.
     * Order: (debuffs) * (permanent buffs) * (temporary buffs/abilities)
     *
     * @param playerId The player's UUID
     * @return The combined multiplier
     */
    public float getCombinedMultiplier(UUID playerId) {
        float multiplier = 1.0f;

        // Apply debuffs first (reduce speed)
        Float energyMult = energyDebuffMultipliers.get(playerId);
        if (energyMult != null) {
            multiplier *= energyMult;
        }

        Float thirstMult = thirstDebuffMultipliers.get(playerId);
        if (thirstMult != null) {
            multiplier *= thirstMult;
        }

        // Apply permanent speed buffs (Tier 3 abilities - always active)
        Float permanentMult = permanentSpeedMultipliers.get(playerId);
        if (permanentMult != null) {
            multiplier *= permanentMult;
        }

        // Apply temporary buffs only if not heavily debuffed
        if (multiplier >= 0.5f) {  // Only apply buffs if not too slow
            // Metabolism buff (Energized)
            Float buffMult = buffMultipliers.get(playerId);
            if (buffMult != null) {
                multiplier *= buffMult;
            }

            // Ability buff (Adrenaline Rush)
            Float abilityMult = abilitySpeedMultipliers.get(playerId);
            if (abilityMult != null) {
                multiplier *= abilityMult;
            }
        }

        return multiplier;
    }

    /**
     * Checks if a player has any active speed modifiers.
     */
    public boolean hasActiveModifiers(UUID playerId) {
        return energyDebuffMultipliers.containsKey(playerId) ||
               thirstDebuffMultipliers.containsKey(playerId) ||
               buffMultipliers.containsKey(playerId) ||
               abilitySpeedMultipliers.containsKey(playerId) ||
               permanentSpeedMultipliers.containsKey(playerId);
    }

    // ========== Application Methods ==========

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

        // Atomic check-and-update: only proceed if multiplier changed significantly
        // compute() ensures thread-safe read-compare-write operation
        boolean[] shouldUpdate = {false};
        lastAppliedMultipliers.compute(playerId, (key, lastApplied) -> {
            if (lastApplied == null || Math.abs(lastApplied - combinedMultiplier) >= 0.01f) {
                shouldUpdate[0] = true;
                return combinedMultiplier; // Update to new value
            }
            return lastApplied; // Keep existing value
        });

        if (!shouldUpdate[0]) {
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
                // Use putIfAbsent for atomic check-and-set, then get for guaranteed non-null value
                Float storedSpeed = originalBaseSpeeds.putIfAbsent(playerId, settings.baseSpeed);
                if (storedSpeed == null) {
                    logger.at(Level.FINE).log("SpeedManager: Stored original base speed for player %s: %.2f",
                        playerId, settings.baseSpeed);
                    storedSpeed = settings.baseSpeed;
                }

                float originalSpeed = storedSpeed;
                float newSpeed = originalSpeed * combinedMultiplier;

                // Apply the modified speed
                settings.baseSpeed = newSpeed;

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

        // Use single atomic get operation to avoid race condition
        Float originalSpeed = originalBaseSpeeds.get(playerId);
        if (originalSpeed == null) {
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
        abilitySpeedMultipliers.remove(playerId);
        permanentSpeedMultipliers.remove(playerId);
        lastAppliedMultipliers.remove(playerId);
    }
}
