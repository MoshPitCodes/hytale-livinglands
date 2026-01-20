package com.livinglands.core;

import com.livinglands.api.AbstractModule;
import com.livinglands.core.events.PlayerDeathBroadcaster;
import com.livinglands.core.util.SpeedManager;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.logging.Level;

/**
 * Core infrastructure module for Living Lands.
 *
 * This module provides shared utilities used by multiple other modules:
 * - SpeedManager: Centralized player speed modifications
 * - PlayerDeathBroadcaster: Centralized death event handling
 *
 * This module:
 * - Has no dependencies (loads first)
 * - Should always be enabled
 * - Other modules depend on it for shared functionality
 */
public final class CoreModule extends AbstractModule {

    public static final String ID = "core";
    public static final String NAME = "Core Utilities";
    public static final String VERSION = "1.0.0";

    // Shared utilities
    private SpeedManager speedManager;
    private PlayerDeathBroadcaster deathBroadcaster;

    public CoreModule() {
        super(ID, NAME, VERSION, Set.of());  // No dependencies - loads first
    }

    @Override
    protected void onSetup() {
        logger.at(Level.FINE).log("[%s] Initializing core utilities...", name);

        // Initialize SpeedManager
        speedManager = new SpeedManager(logger);

        // Initialize and register PlayerDeathBroadcaster
        deathBroadcaster = new PlayerDeathBroadcaster(logger);
        context.entityStoreRegistry().registerSystem(deathBroadcaster);

        logger.at(Level.FINE).log("[%s] Core utilities initialized", name);
    }

    @Override
    protected void onStart() {
        logger.at(Level.FINE).log("[%s] Core module started", name);
    }

    @Override
    protected void onShutdown() {
        // Clean up death broadcaster listeners
        if (deathBroadcaster != null) {
            deathBroadcaster.clearListeners();
        }

        logger.at(Level.FINE).log("[%s] Core module shutdown", name);
    }

    /**
     * Gets the centralized speed manager.
     * Used by metabolism (buffs/debuffs) and leveling (abilities) modules.
     *
     * @return The speed manager instance
     */
    @Nonnull
    public SpeedManager getSpeedManager() {
        if (speedManager == null) {
            throw new IllegalStateException("CoreModule not initialized - SpeedManager is null");
        }
        return speedManager;
    }

    /**
     * Gets the centralized player death broadcaster.
     * Used by modules that need to respond to player death events.
     *
     * @return The death broadcaster instance
     */
    @Nonnull
    public PlayerDeathBroadcaster getDeathBroadcaster() {
        if (deathBroadcaster == null) {
            throw new IllegalStateException("CoreModule not initialized - PlayerDeathBroadcaster is null");
        }
        return deathBroadcaster;
    }
}
