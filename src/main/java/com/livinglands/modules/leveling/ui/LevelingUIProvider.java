package com.livinglands.modules.leveling.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.livinglands.api.ModuleUIProvider;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.config.LevelingModuleConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * UI provider for the Leveling module.
 *
 * Displays profession levels and abilities in the core settings panel.
 */
public class LevelingUIProvider implements ModuleUIProvider {

    private final LevelingSystem system;
    private final LevelingModuleConfig config;
    private final HytaleLogger logger;

    public LevelingUIProvider(@Nonnull LevelingSystem system,
                             @Nonnull LevelingModuleConfig config,
                             @Nonnull HytaleLogger logger) {
        this.system = system;
        this.config = config;
        this.logger = logger;
    }

    @Override
    @Nonnull
    public String getTabName() {
        return "Leveling";
    }

    @Override
    public int getTabOrder() {
        return 75; // Between Metabolism (50) and Claims (100)
    }

    @Override
    public void buildTabContent(@Nonnull UICommandBuilder cmd,
                               @Nonnull UIEventBuilder events,
                               @Nonnull PlayerRef player) {
        // This is a placeholder - the actual content would need UI elements defined
        // For now, this just provides the tab with basic info displayed via existing elements
        // Future: Add LevelingContent elements to CoreSettingsPage.ui
    }

    @Override
    public void handleTabEvent(@Nonnull String action,
                              @Nullable String value,
                              @Nonnull PlayerRef player,
                              @Nonnull Runnable rebuild) {
        // No events to handle currently
    }
}
