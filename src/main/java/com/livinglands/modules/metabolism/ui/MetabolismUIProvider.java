package com.livinglands.modules.metabolism.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.livinglands.api.ModuleUIProvider;
import com.livinglands.modules.metabolism.MetabolismSystem;
import com.livinglands.modules.metabolism.config.MetabolismModuleConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * UI provider for the Metabolism module.
 *
 * Displays metabolism stats and configuration information
 * in the core settings panel.
 */
public class MetabolismUIProvider implements ModuleUIProvider {

    private final MetabolismSystem system;
    private final MetabolismModuleConfig config;
    private final HytaleLogger logger;

    public MetabolismUIProvider(@Nonnull MetabolismSystem system,
                               @Nonnull MetabolismModuleConfig config,
                               @Nonnull HytaleLogger logger) {
        this.system = system;
        this.config = config;
        this.logger = logger;
    }

    @Override
    @Nonnull
    public String getTabName() {
        return "Metabolism";
    }

    @Override
    public int getTabOrder() {
        return 50; // Core module, shows before Claims
    }

    @Override
    public void buildTabContent(@Nonnull UICommandBuilder cmd,
                               @Nonnull UIEventBuilder events,
                               @Nonnull PlayerRef player) {
        // This is a placeholder - the actual content would need UI elements defined
        // For now, this just provides the tab with basic info displayed via existing elements
        // Future: Add MetabolismContent elements to CoreSettingsPage.ui
    }

    @Override
    public void handleTabEvent(@Nonnull String action,
                              @Nullable String value,
                              @Nonnull PlayerRef player,
                              @Nonnull Runnable rebuild) {
        // No events to handle currently
    }
}
