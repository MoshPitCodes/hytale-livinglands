package com.livinglands.listeners;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.systems.world.CanSleepInWorld;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.LivingLandsPlugin;
import com.livinglands.core.config.SleepConfig;
import com.livinglands.metabolism.MetabolismSystem;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Listener for bed interactions to restore energy.
 *
 * Uses UseBlockEvent.Post to detect when a player uses a bed block.
 *
 * To distinguish between sleeping and setting spawn, we check for the PlayerSleep
 * component after a short delay - if the player is sleeping, they'll have this component.
 */
public class BedInteractionListener {

    // Delay before checking if player is actually sleeping (ms)
    private static final long SLEEP_CHECK_DELAY_MS = 500;

    private final LivingLandsPlugin plugin;
    private final MetabolismSystem metabolismSystem;
    private final SleepConfig sleepConfig;

    // Track last bed use per player to prevent spam
    private final Map<UUID, Long> lastBedUse = new ConcurrentHashMap<>();

    // Executor for delayed sleep state checks
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "LivingLands-BedCheck");
        thread.setDaemon(true);
        return thread;
    });

    public BedInteractionListener(@Nonnull LivingLandsPlugin plugin) {
        this.plugin = plugin;
        this.metabolismSystem = plugin.getMetabolismSystem();
        this.sleepConfig = plugin.getModConfig().sleep();
    }

    public void register(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(UseBlockEvent.Post.class, this::onEntityUseBlock);
        plugin.getLogger().at(Level.INFO).log(
            "Registered bed interaction listener (energy: %.0f, cooldown: %dms, schedule: %s)",
            sleepConfig.energyRestoreAmount(),
            sleepConfig.cooldownMs(),
            sleepConfig.respectSleepSchedule() ? "respected" : "ignored"
        );
    }

    private void onEntityUseBlock(@Nonnull UseBlockEvent.Post event) {
        try {
            var blockType = event.getBlockType();
            var context = event.getContext();

            if (blockType == null || context == null) {
                return;
            }

            var entityRef = context.getEntity();
            if (entityRef == null) {
                return;
            }

            // Check if this is a bed block using config patterns
            var blockTypeId = blockType.getId();
            if (!sleepConfig.bedBlocks().isBedBlock(blockTypeId)) {
                return;
            }

            // Get the world from the entity store to check sleep schedule
            var world = getWorldFromRef(entityRef);
            if (world == null) {
                plugin.getLogger().at(Level.FINE).log(
                    "Could not get world from entity ref for bed interaction"
                );
                return;
            }

            // Get the player component from the entity ref
            var player = getPlayerFromRef(entityRef);

            // Check if sleeping is allowed according to the game's sleep schedule (if configured)
            if (sleepConfig.respectSleepSchedule()) {
                var sleepCheck = CanSleepInWorld.check(world);
                if (sleepCheck.isNegative()) {
                    // Sleeping not allowed - this is likely a spawn point set, ignore silently
                    plugin.getLogger().at(Level.FINE).log(
                        "Bed interaction during day - likely spawn point set, ignoring"
                    );
                    return;
                }
            }

            // Get the player UUID from the entity ref
            var playerId = getPlayerUuidFromRef(entityRef);
            if (playerId == null) {
                plugin.getLogger().at(Level.FINE).log(
                    "Could not get player UUID from entity ref for bed interaction"
                );
                return;
            }

            // Schedule a delayed check to see if player is actually sleeping
            // This distinguishes between sleeping (energy restore) and setting spawn (no restore)
            final var finalEntityRef = entityRef;
            final var finalPlayer = player;
            final var finalBlockTypeId = blockTypeId;
            final var finalWorld = world;

            scheduler.schedule(() -> {
                // Use world.execute() to run the check on the correct thread
                finalWorld.execute(() -> {
                    try {
                        checkAndProcessSleep(playerId, finalEntityRef, finalPlayer, finalBlockTypeId);
                    } catch (Exception e) {
                        plugin.getLogger().at(Level.FINE).withCause(e).log(
                            "Error in delayed sleep check for player %s", playerId
                        );
                    }
                });
            }, SLEEP_CHECK_DELAY_MS, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log(
                "Error processing bed use block event"
            );
        }
    }

    /**
     * Checks if player is actually sleeping and processes energy restoration if so.
     * Called after a delay to allow the sleep component to be added.
     */
    private void checkAndProcessSleep(UUID playerId, Ref<EntityStore> entityRef,
                                       Player player, String blockType) {
        // Check if the player has entered a sleep state
        if (!isPlayerSleeping(entityRef)) {
            plugin.getLogger().at(Level.FINE).log(
                "Player %s interacted with bed but is not sleeping - likely spawn point set",
                playerId
            );
            return;
        }

        // Player is actually sleeping, process bed rest
        processBedRest(playerId, player, blockType);
    }

    /**
     * Checks if the player is in a sleep state (not awake).
     * Uses PlayerSomnolence component to determine sleep state.
     */
    private boolean isPlayerSleeping(Ref<EntityStore> entityRef) {
        try {
            if (entityRef == null || !entityRef.isValid()) {
                return false;
            }

            var store = entityRef.getStore();
            if (store == null) {
                return false;
            }

            // Get the PlayerSomnolence component which tracks sleep state
            var somnolence = store.getComponent(entityRef, PlayerSomnolence.getComponentType());
            if (somnolence == null) {
                return false;
            }

            // Check if player is in any sleep state (not AWAKE)
            var sleepState = somnolence.getSleepState();
            return sleepState != null; // null means awake, non-null means sleeping
        } catch (Exception e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log(
                "Error checking player sleep state"
            );
            return false;
        }
    }

    /**
     * Gets the World from an entity ref.
     */
    private World getWorldFromRef(Ref<EntityStore> entityRef) {
        try {
            if (!entityRef.isValid()) {
                return null;
            }

            var store = entityRef.getStore();
            if (store == null) {
                return null;
            }

            // EntityStore implements WorldProvider which has getWorld()
            return store.getExternalData().getWorld();
        } catch (Exception e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log(
                "Error getting world from entity ref"
            );
            return null;
        }
    }

    /**
     * Gets the Player component from an entity ref.
     */
    private Player getPlayerFromRef(Ref<EntityStore> entityRef) {
        try {
            if (!entityRef.isValid()) {
                return null;
            }

            var store = entityRef.getStore();
            if (store == null) {
                return null;
            }

            return store.getComponent(entityRef, Player.getComponentType());
        } catch (Exception e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log(
                "Error getting player from entity ref"
            );
            return null;
        }
    }

    /**
     * Extracts the player UUID from an entity ref using UUIDComponent.
     */
    private UUID getPlayerUuidFromRef(Ref<EntityStore> entityRef) {
        try {
            if (!entityRef.isValid()) {
                return null;
            }

            var store = entityRef.getStore();
            if (store == null) {
                return null;
            }

            var uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
            if (uuidComponent == null) {
                return null;
            }

            return uuidComponent.getUuid();
        } catch (Exception e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log(
                "Error getting UUID from entity ref"
            );
            return null;
        }
    }

    /**
     * Process bed rest for a player by UUID.
     */
    private void processBedRest(UUID playerId, Player player, String blockType) {
        var now = System.currentTimeMillis();
        var cooldownMs = sleepConfig.cooldownMs();
        var energyRestoreAmount = sleepConfig.energyRestoreAmount();

        // Check cooldown
        var lastUse = lastBedUse.getOrDefault(playerId, 0L);
        if (now - lastUse < cooldownMs) {
            var remaining = (cooldownMs - (now - lastUse)) / 1000;
            if (player != null) {
                try {
                    player.sendMessage(
                        Message.raw(String.format("You must wait %d seconds before resting again.", remaining))
                            .color("yellow")
                    );
                } catch (Exception ignored) {}
            }
            plugin.getLogger().at(Level.FINE).log(
                "Player %s bed rest on cooldown: %d seconds remaining",
                playerId, remaining
            );
            return;
        }

        // Restore energy
        var energyRestored = metabolismSystem.restoreEnergy(playerId, energyRestoreAmount);

        if (energyRestored > 0) {
            lastBedUse.put(playerId, now);

            if (player != null) {
                try {
                    player.sendMessage(
                        Message.raw(String.format("You feel rested. Energy +%.0f", energyRestored))
                            .color("green")
                    );
                } catch (Exception ignored) {}
            }

            plugin.getLogger().at(Level.INFO).log(
                "Player %s rested in %s: energy +%.1f",
                playerId, blockType, energyRestored
            );
        } else {
            if (player != null) {
                try {
                    player.sendMessage(
                        Message.raw("You are already fully rested.")
                            .color("gray")
                    );
                } catch (Exception ignored) {}
            }
            plugin.getLogger().at(Level.FINE).log(
                "Player %s used bed but energy already full",
                playerId
            );
        }
    }

    public void removePlayer(UUID playerId) {
        lastBedUse.remove(playerId);
    }

    /**
     * Shuts down the scheduler. Should be called when the plugin is disabled.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
