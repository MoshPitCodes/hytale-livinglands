package com.livinglands.api;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.livinglands.core.ModuleManager;
import com.livinglands.core.PlayerRegistry;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Context object providing modules access to shared services.
 *
 * This record is passed to modules during setup and provides:
 * - Plugin infrastructure (logger, directories)
 * - Hytale registries (commands, events)
 * - Core services (player registry)
 * - Inter-module access (module manager)
 *
 * @param logger          The plugin logger
 * @param pluginDirectory Root plugin data directory (LivingLands/)
 * @param eventRegistry   Hytale event registry for listener registration
 * @param commandRegistry Hytale command registry for command registration
 * @param playerRegistry  Shared player session registry
 * @param moduleManager   Module manager for inter-module access
 */
public record ModuleContext(
        @Nonnull HytaleLogger logger,
        @Nonnull Path pluginDirectory,
        @Nonnull EventRegistry eventRegistry,
        @Nonnull CommandRegistry commandRegistry,
        @Nonnull PlayerRegistry playerRegistry,
        @Nonnull ModuleManager moduleManager
) {

    /**
     * Gets a module by ID and expected type.
     *
     * @param moduleId Module ID to look up
     * @param type     Expected module class
     * @param <T>      Module type
     * @return Optional containing the module if found and enabled
     */
    @Nonnull
    public <T extends Module> Optional<T> getModule(@Nonnull String moduleId,
                                                     @Nonnull Class<T> type) {
        return moduleManager.getModule(moduleId, type);
    }

    /**
     * Checks if a module is enabled.
     *
     * @param moduleId Module ID to check
     * @return true if module is registered and enabled
     */
    public boolean isModuleEnabled(@Nonnull String moduleId) {
        return moduleManager.isModuleEnabled(moduleId);
    }
}
