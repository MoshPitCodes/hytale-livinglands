package com.livinglands.metabolism;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Detects player activity state from ECS MovementStatesComponent.
 * Uses thread-safe access patterns via world.execute().
 */
public class ActivityDetector {

    private final HytaleLogger logger;

    public ActivityDetector(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Activity state snapshot from ECS.
     */
    public record ActivityState(
        boolean idle,
        boolean walking,
        boolean sprinting,
        boolean swimming,
        boolean jumping,
        boolean falling,
        boolean climbing,
        boolean onGround
    ) {
        public static ActivityState UNKNOWN = new ActivityState(
            true, false, false, false, false, false, false, true
        );
    }

    /**
     * Detects current activity state from player's ECS components.
     * Must be called from metabolism tick (not on WorldThread).
     *
     * @param ref Player entity reference
     * @param store Entity store
     * @param world World for thread-safe execution
     * @return ActivityState snapshot, or UNKNOWN if detection fails
     */
    public ActivityState detectActivity(@Nonnull Ref<EntityStore> ref,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull World world) {
        var result = new AtomicReference<>(ActivityState.UNKNOWN);

        try {
            // Execute on WorldThread to safely access ECS
            world.execute(() -> {
                try {
                    var movementComponent = store.getComponent(ref, MovementStatesComponent.getComponentType());
                    if (movementComponent != null) {
                        var states = movementComponent.getMovementStates();
                        if (states != null) {
                            result.set(new ActivityState(
                                states.idle,
                                states.walking,
                                states.sprinting || states.running,
                                states.swimming || states.inFluid,
                                states.jumping,
                                states.falling,
                                states.climbing,
                                states.onGround
                            ));
                        }
                    }
                } catch (Exception e) {
                    logger.at(Level.FINE).withCause(e).log("Error reading movement states");
                }
            });
        } catch (Exception e) {
            logger.at(Level.FINE).withCause(e).log("Error executing activity detection");
        }

        return result.get();
    }

    /**
     * Updates PlayerMetabolismData with detected activity state.
     *
     * @param data Player metabolism data to update
     * @param state Detected activity state
     * @param currentTime Current time in milliseconds
     */
    public void updatePlayerActivity(@Nonnull PlayerMetabolismData data,
                                      @Nonnull ActivityState state,
                                      long currentTime) {
        data.updateActivity(state.sprinting(), state.swimming(), currentTime);
    }
}
