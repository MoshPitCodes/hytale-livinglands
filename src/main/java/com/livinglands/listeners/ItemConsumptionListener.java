package com.livinglands.listeners;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.livinglands.LivingLandsPlugin;
import com.livinglands.metabolism.MetabolismSystem;
import com.livinglands.metabolism.consumables.ConsumableItem;
import com.livinglands.metabolism.consumables.ConsumableRegistry;
import com.livinglands.metabolism.poison.PoisonItem;
import com.livinglands.metabolism.poison.PoisonRegistry;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Event listener for item consumption.
 *
 * Uses Transaction analysis to distinguish consumption from drops/moves:
 * - MoveTransaction = item moved (drop/transfer) - IGNORE
 * - SlotTransaction with quantity decrease = consumption - PROCESS
 */
public class ItemConsumptionListener {

    private final LivingLandsPlugin plugin;
    private final MetabolismSystem metabolismSystem;

    // Track last consumption time per player to debounce rapid events
    private final Map<UUID, Long> lastConsumptionTime = new ConcurrentHashMap<>();

    // Minimum time between consumption detections (ms)
    private static final long CONSUMPTION_COOLDOWN_MS = 500;

    public ItemConsumptionListener(@Nonnull LivingLandsPlugin plugin) {
        this.plugin = plugin;
        this.metabolismSystem = plugin.getMetabolismSystem();
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        plugin.getLogger().at(Level.INFO).log(
            "Registered item consumption listener (%d consumables, %d poisons)",
            ConsumableRegistry.getRegisteredCount(),
            PoisonRegistry.getRegisteredCount()
        );
    }

    @SuppressWarnings("deprecation")
    private void onInventoryChange(@Nonnull LivingEntityInventoryChangeEvent event) {
        try {
            var entity = event.getEntity();
            if (!(entity instanceof Player player)) {
                return;
            }

            var transaction = event.getTransaction();
            if (transaction == null) {
                return;
            }

            // Check if this is a move/drop operation (ignore these)
            if (isMoveOrDropTransaction(transaction)) {
                return;
            }

            // Process SlotTransaction for consumption
            if (transaction instanceof SlotTransaction slotTx) {
                processSlotTransaction(player, slotTx);
            }

        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log(
                "Error processing inventory change"
            );
        }
    }

    @SuppressWarnings("deprecation") // getPlayerRef() is deprecated but required for UUID access
    private void processSlotTransaction(Player player, SlotTransaction transaction) {
        if (!transaction.succeeded()) {
            return;
        }

        var before = transaction.getSlotBefore();
        var after = transaction.getSlotAfter();

        // Need a "before" item to detect consumption
        if (before == null || before.isEmpty()) {
            return;
        }

        var itemId = before.getItemId();
        if (itemId == null || itemId.isEmpty()) {
            return;
        }

        // Check if item is consumable
        var item = before.getItem();
        if (item == null || !item.isConsumable()) {
            return;
        }

        // Calculate quantity change
        int beforeQty = before.getQuantity();
        int afterQty = (after != null && !after.isEmpty() && itemId.equals(after.getItemId()))
                       ? after.getQuantity()
                       : 0;

        // Only process if quantity decreased by exactly 1 (consumption)
        if (beforeQty - afterQty != 1) {
            return;
        }

        var playerRef = player.getPlayerRef();
        if (playerRef == null) {
            return;
        }
        var playerId = playerRef.getUuid();
        var now = System.currentTimeMillis();

        // Check cooldown
        var lastTime = lastConsumptionTime.getOrDefault(playerId, 0L);
        if (now - lastTime < CONSUMPTION_COOLDOWN_MS) {
            return;
        }

        // Update cooldown
        lastConsumptionTime.put(playerId, now);

        // Check if item is poisonous FIRST (poison takes priority)
        var poisonOpt = PoisonRegistry.getPoisonItem(itemId);
        if (poisonOpt.isPresent()) {
            processPoisonConsumption(player, playerId, poisonOpt.get(), itemId);
            return;
        }

        // Check if registered in our consumable registry
        var consumableOpt = ConsumableRegistry.getConsumable(itemId);
        if (consumableOpt.isEmpty()) {
            plugin.getLogger().at(Level.FINE).log(
                "Consumed unregistered item: '%s'", itemId
            );
            return;
        }

        // Process normal consumable
        processConsumption(player, playerId, consumableOpt.get(), itemId);
    }

    private void processConsumption(Player player, UUID playerId, ConsumableItem consumable, String itemId) {
        double hungerRestored = 0;
        double thirstRestored = 0;
        double energyRestored = 0;

        if (consumable.restoresHunger()) {
            hungerRestored = metabolismSystem.restoreHunger(playerId, consumable.hungerRestore());
        }

        if (consumable.restoresThirst()) {
            thirstRestored = metabolismSystem.restoreThirst(playerId, consumable.thirstRestore());
        }

        if (consumable.restoresEnergy()) {
            energyRestored = metabolismSystem.restoreEnergy(playerId, consumable.energyRestore());
        }

        if (hungerRestored > 0 || thirstRestored > 0 || energyRestored > 0) {
            sendConsumptionFeedback(player, hungerRestored, thirstRestored, energyRestored);

            plugin.getLogger().at(Level.INFO).log(
                "Player %s consumed %s: hunger +%.1f, thirst +%.1f, energy +%.1f",
                playerId, itemId, hungerRestored, thirstRestored, energyRestored
            );
        }
    }

    /**
     * Processes consumption of a poisonous item.
     * May restore some stats (deceptive food), but always applies poison effect.
     */
    private void processPoisonConsumption(Player player, UUID playerId, PoisonItem poison, String itemId) {
        // Apply any stat restoration first (deceptive foods that restore but also poison)
        double hungerRestored = 0;
        double thirstRestored = 0;

        if (poison.hungerRestore() > 0) {
            hungerRestored = metabolismSystem.restoreHunger(playerId, poison.hungerRestore());
        }

        if (poison.thirstRestore() > 0) {
            thirstRestored = metabolismSystem.restoreThirst(playerId, poison.thirstRestore());
        }

        // Always apply the poison effect
        var poisonSystem = metabolismSystem.getPoisonEffectsSystem();
        poisonSystem.applyPoison(playerId, poison.effectType());

        // Send feedback about consumption AND poison
        sendPoisonFeedback(player, hungerRestored, thirstRestored, poison);

        plugin.getLogger().at(Level.INFO).log(
            "Player %s consumed poisonous %s: hunger +%.1f, thirst +%.1f, poison effect: %s",
            playerId, itemId, hungerRestored, thirstRestored, poison.effectType()
        );
    }

    /**
     * Sends feedback message when consuming a poisonous item.
     */
    private void sendPoisonFeedback(Player player, double hungerRestored, double thirstRestored, PoisonItem poison) {
        var msg = new StringBuilder();

        // Show any stat restoration
        if (hungerRestored > 0) {
            msg.append(String.format("Hunger +%.0f", hungerRestored));
        }

        if (thirstRestored > 0) {
            if (!msg.isEmpty()) msg.append(" | ");
            msg.append(String.format("Thirst +%.0f", thirstRestored));
        }

        // Add poison warning
        if (!msg.isEmpty()) msg.append(" | ");
        msg.append("POISONED!");

        try {
            // Red color for poison warning
            player.sendMessage(Message.raw(msg.toString()).color("red"));
        } catch (Exception e) {
            // Silently ignore
        }
    }

    private void sendConsumptionFeedback(Player player, double hungerRestored, double thirstRestored, double energyRestored) {
        var msg = new StringBuilder();

        if (hungerRestored > 0) {
            msg.append(String.format("Hunger +%.0f", hungerRestored));
        }

        if (thirstRestored > 0) {
            if (!msg.isEmpty()) msg.append(" | ");
            msg.append(String.format("Thirst +%.0f", thirstRestored));
        }

        if (energyRestored > 0) {
            if (!msg.isEmpty()) msg.append(" | ");
            msg.append(String.format("Energy +%.0f", energyRestored));
        }

        try {
            player.sendMessage(Message.raw(msg.toString()).color("green"));
        } catch (Exception e) {
            // Silently ignore
        }
    }

    public void removePlayer(UUID playerId) {
        lastConsumptionTime.remove(playerId);
    }

    /**
     * Checks if a transaction represents a move/drop operation (not consumption).
     * Handles both direct MoveTransaction and ListTransaction containing moves.
     */
    private boolean isMoveOrDropTransaction(Transaction transaction) {
        // Direct MoveTransaction
        if (transaction instanceof MoveTransaction<?>) {
            return true;
        }

        // ListTransaction may contain MoveTransactions
        if (transaction instanceof ListTransaction<?> listTx) {
            for (var inner : listTx.getList()) {
                if (inner instanceof MoveTransaction<?>) {
                    return true;
                }
            }
        }

        return false;
    }
}
