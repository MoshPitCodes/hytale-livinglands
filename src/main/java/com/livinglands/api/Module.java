package com.livinglands.api;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Defines the contract for a Living Lands module.
 *
 * Modules are self-contained features that can be enabled/disabled
 * by server administrators. Each module manages its own:
 * - Configuration files
 * - Commands
 * - Event listeners
 * - Background tasks
 * - Data persistence
 *
 * Lifecycle:
 * 1. Module is registered with ModuleManager
 * 2. If enabled in modules.json, setup() is called
 * 3. After all modules are set up, start() is called
 * 4. On server shutdown, shutdown() is called
 *
 * This is a sealed interface - all modules must extend AbstractModule.
 */
public sealed interface Module permits AbstractModule {

    /**
     * Gets the unique identifier for this module.
     * Used in modules.json and for dependency resolution.
     *
     * @return Module ID (e.g., "metabolism", "claims", "economy")
     */
    @Nonnull
    String getId();

    /**
     * Gets the human-readable name of this module.
     *
     * @return Display name (e.g., "Metabolism System")
     */
    @Nonnull
    String getName();

    /**
     * Gets the version of this module.
     *
     * @return Version string (e.g., "1.0.0")
     */
    @Nonnull
    String getVersion();

    /**
     * Gets the IDs of modules this module depends on.
     * Dependencies will be auto-enabled and set up before this module.
     *
     * @return Set of module IDs (empty if no dependencies)
     */
    @Nonnull
    Set<String> getDependencies();

    /**
     * Setup phase - called when module is enabled.
     *
     * In this phase, modules should:
     * - Load configuration from their config directory
     * - Register commands with the command registry
     * - Register event listeners with the event registry
     * - Initialize stats, components, and other static resources
     * - Create system instances (but not start background tasks)
     *
     * @param context The module context providing access to shared services
     */
    void setup(@Nonnull ModuleContext context);

    /**
     * Start phase - called after all modules are set up.
     *
     * In this phase, modules should:
     * - Start tick loops and scheduled tasks
     * - Begin background processing
     * - Activate any runtime systems
     */
    void start();

    /**
     * Shutdown phase - called when server is stopping.
     *
     * In this phase, modules should:
     * - Save all pending data to disk
     * - Stop background tasks gracefully
     * - Release any held resources
     * - Clean up listeners (automatic, but can be explicit)
     */
    void shutdown();

    /**
     * Checks if this module is currently enabled.
     *
     * @return true if module is enabled in configuration
     */
    boolean isEnabled();

    /**
     * Gets the current lifecycle state of this module.
     *
     * @return The module's state
     */
    @Nonnull
    ModuleState getState();
}
