package com.livinglands.modules.metabolism.listeners;

/**
 * Legacy death handler listener.
 *
 * Death detection and metabolism reset is now handled entirely by
 * PlayerDeathSystem (ECS event system) which resets metabolism immediately
 * when the player dies.
 *
 * This class is kept for backwards compatibility but does nothing.
 * TODO: Remove in future version.
 */
@Deprecated
public class DeathHandlerListener {
    // Intentionally empty - functionality moved to PlayerDeathSystem
}
