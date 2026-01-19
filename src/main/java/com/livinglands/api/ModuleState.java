package com.livinglands.api;

/**
 * Represents the lifecycle state of a module.
 *
 * State transitions:
 * DISABLED -> SETUP -> STARTED -> STOPPED
 *          -> ERROR (from any state)
 */
public enum ModuleState {

    /**
     * Module is registered but not enabled in configuration.
     */
    DISABLED,

    /**
     * Module is enabled and setup() has been called successfully.
     * Commands, events, and components are registered.
     */
    SETUP,

    /**
     * Module is fully running - start() has been called.
     * Background tasks and tick loops are active.
     */
    STARTED,

    /**
     * Module has been gracefully stopped via shutdown().
     * Data has been saved and resources released.
     */
    STOPPED,

    /**
     * Module encountered an error during lifecycle.
     * Check logs for details.
     */
    ERROR;

    /**
     * Checks if the module is in an active state (SETUP or STARTED).
     */
    public boolean isActive() {
        return this == SETUP || this == STARTED;
    }

    /**
     * Checks if the module can transition to the given state.
     */
    public boolean canTransitionTo(ModuleState target) {
        return switch (this) {
            case DISABLED -> target == SETUP || target == ERROR;
            case SETUP -> target == STARTED || target == STOPPED || target == ERROR;
            case STARTED -> target == STOPPED || target == ERROR;
            case STOPPED -> target == SETUP || target == ERROR;
            case ERROR -> target == DISABLED;
        };
    }
}
