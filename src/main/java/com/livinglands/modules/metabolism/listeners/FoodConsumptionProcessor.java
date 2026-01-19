package com.livinglands.modules.metabolism.listeners;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.metabolism.MetabolismSystem;
import com.livinglands.modules.metabolism.consumables.FoodEffectDetector;
import com.livinglands.modules.metabolism.consumables.FoodEffectDetector.DetectedFoodConsumption;
import com.livinglands.modules.metabolism.consumables.FoodEffectDetector.FoodType;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Processes food consumption by detecting food buff effects applied to players.
 *
 * This is called from the metabolism tick loop to check if any food effects
 * have been newly applied to players. When detected, it restores the appropriate
 * metabolism stats (hunger, thirst, energy).
 *
 * This approach is more reliable than inventory-based detection because:
 * 1. Food effects only apply when food is actually consumed (not dropped)
 * 2. We detect the RESULT of consumption, not inventory changes
 * 3. No false positives from item drops or moves
 */
public class FoodConsumptionProcessor {

    private final HytaleLogger logger;
    private final MetabolismSystem metabolismSystem;
    private final PlayerRegistry playerRegistry;
    private final FoodEffectDetector foodEffectDetector;

    // Base restoration values per tier (can be configured)
    private static final double[] HUNGER_BY_TIER = {0, 8.0, 15.0, 25.0};   // Tier 0 unused, 1-3
    private static final double[] THIRST_BY_TIER = {0, 3.0, 6.0, 10.0};
    private static final double[] ENERGY_BY_TIER = {0, 5.0, 10.0, 15.0};

    public FoodConsumptionProcessor(
            @Nonnull HytaleLogger logger,
            @Nonnull MetabolismSystem metabolismSystem,
            @Nonnull PlayerRegistry playerRegistry) {
        this.logger = logger;
        this.metabolismSystem = metabolismSystem;
        this.playerRegistry = playerRegistry;
        this.foodEffectDetector = new FoodEffectDetector(logger);
    }

    /**
     * Processes food consumption for a single player.
     * Called from the metabolism tick loop.
     *
     * Uses the effect change log to detect ALL food consumption, including
     * instant heals that expire before the next tick.
     *
     * IMPORTANT: ECS access must happen on the WorldThread, so we schedule the
     * detection to run on the world thread.
     *
     * @param playerId The player's UUID
     */
    public void processPlayer(@Nonnull UUID playerId) {
        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) {
            return;
        }

        var session = sessionOpt.get();

        // Skip if in Creative mode
        if (session.isCreativeMode()) {
            return;
        }

        // Skip if world not ready
        var world = session.getWorld();
        if (world == null) {
            return;
        }

        // Execute ECS access on the WorldThread
        world.execute(() -> {
            try {
                // Check for new food effects (may return multiple if player ate several items)
                var consumptions = foodEffectDetector.checkForNewFoodEffects(playerId, session);
                if (consumptions.isEmpty()) {
                    return;
                }

                // Process each detected consumption
                for (var consumption : consumptions) {
                    processConsumption(playerId, session.getPlayer(), consumption);
                }
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log(
                    "Error processing food consumption for player %s", playerId
                );
            }
        });
    }

    /**
     * Processes detected food consumption, restoring metabolism stats.
     */
    private void processConsumption(UUID playerId, Player player, DetectedFoodConsumption consumption) {
        int tier = Math.max(1, Math.min(3, consumption.tier())); // Clamp to 1-3

        // Calculate restoration amounts based on tier and food type
        double hungerRestore = calculateHungerRestore(tier, consumption.foodType());
        double thirstRestore = calculateThirstRestore(tier, consumption.foodType());
        double energyRestore = calculateEnergyRestore(tier, consumption.foodType());

        // Apply restoration
        double actualHunger = 0;
        double actualThirst = 0;
        double actualEnergy = 0;

        if (hungerRestore > 0) {
            actualHunger = metabolismSystem.restoreHunger(playerId, hungerRestore);
        }
        if (thirstRestore > 0) {
            actualThirst = metabolismSystem.restoreThirst(playerId, thirstRestore);
        }
        if (energyRestore > 0) {
            actualEnergy = metabolismSystem.restoreEnergy(playerId, energyRestore);
        }

        // Send feedback if any stats were restored
        if (actualHunger > 0 || actualThirst > 0 || actualEnergy > 0) {
            sendFeedback(player, actualHunger, actualThirst, actualEnergy);

            logger.at(Level.INFO).log(
                "Player %s consumed food (%s, tier %d): hunger +%.1f, thirst +%.1f, energy +%.1f",
                playerId, consumption.effectId(), tier, actualHunger, actualThirst, actualEnergy
            );
        }
    }

    /**
     * Calculates hunger restoration based on tier and food type.
     */
    private double calculateHungerRestore(int tier, FoodType foodType) {
        double base = HUNGER_BY_TIER[tier];

        // Food type modifiers
        return switch (foodType) {
            case MEAT -> base * 1.3;           // Meat is very filling
            case BREAD -> base * 1.2;          // Bread is filling
            case FRUIT_VEGGIE -> base * 0.9;   // Lighter food
            case INSTANT_HEAL -> base * 0.5;   // Healing foods less filling
            case HEALTH_REGEN -> base * 0.7;
            case HEALTH_BOOST -> base * 0.8;
            case STAMINA_BOOST -> base * 0.6;
            case HEALTH_POTION -> base * 0.3;  // Health potions: slight hunger restore
            case MANA_POTION -> 0.0;           // Mana potions: no hunger restore
            case STAMINA_POTION -> 0.0;        // Stamina potions: no hunger restore
            default -> base;
        };
    }

    /**
     * Calculates thirst restoration based on tier and food type.
     */
    private double calculateThirstRestore(int tier, FoodType foodType) {
        double base = THIRST_BY_TIER[tier];

        // Food type modifiers
        return switch (foodType) {
            case FRUIT_VEGGIE -> base * 1.5;   // Fruits have moisture
            case MEAT -> base * 0.5;           // Meat is dry
            case BREAD -> base * 0.3;          // Bread is dry
            case INSTANT_HEAL -> base * 0.8;
            case HEALTH_REGEN -> base * 0.8;
            case HEALTH_POTION -> base * 2.0;  // Health potions: bigger thirst restore
            case MANA_POTION -> base * 2.0;    // Mana potions: bigger thirst restore
            case STAMINA_POTION -> base * 2.0; // Stamina potions: bigger thirst restore
            default -> base;
        };
    }

    /**
     * Calculates energy restoration based on tier and food type.
     */
    private double calculateEnergyRestore(int tier, FoodType foodType) {
        double base = ENERGY_BY_TIER[tier];

        // Food type modifiers
        return switch (foodType) {
            case MEAT -> base * 1.2;           // Protein gives energy
            case FRUIT_VEGGIE -> base * 1.1;   // Natural energy
            case STAMINA_BOOST -> base * 1.5;  // Stamina food gives energy
            case BREAD -> base * 1.0;
            case HEALTH_POTION -> 0.0;         // Health potions: no energy restore
            case MANA_POTION -> base * 0.3;    // Mana potions: slight energy restore
            case STAMINA_POTION -> base * 0.3; // Stamina potions: slight energy restore
            default -> base;
        };
    }

    /**
     * Sends feedback message to the player.
     */
    private void sendFeedback(Player player, double hunger, double thirst, double energy) {
        if (player == null) {
            return;
        }

        var msg = new StringBuilder();

        if (hunger > 0) {
            msg.append(String.format("Hunger +%.0f", hunger));
        }
        if (thirst > 0) {
            if (!msg.isEmpty()) msg.append(" | ");
            msg.append(String.format("Thirst +%.0f", thirst));
        }
        if (energy > 0) {
            if (!msg.isEmpty()) msg.append(" | ");
            msg.append(String.format("Energy +%.0f", energy));
        }

        if (!msg.isEmpty()) {
            try {
                player.sendMessage(Message.raw(msg.toString()).color(ColorUtil.getHexColor("green")));
            } catch (Exception e) {
                // Silently ignore messaging errors
            }
        }
    }

    /**
     * Removes tracking for a player (on disconnect).
     */
    public void removePlayer(@Nonnull UUID playerId) {
        foodEffectDetector.removePlayer(playerId);
    }
}
