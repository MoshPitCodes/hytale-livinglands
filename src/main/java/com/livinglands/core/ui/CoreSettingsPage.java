package com.livinglands.core.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.api.AbstractModule;
import com.livinglands.api.Module;
import com.livinglands.api.ModuleUIProvider;
import com.livinglands.core.ModuleManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

/**
 * Core settings panel that dynamically displays tabs for enabled modules.
 *
 * This interactive UI page queries the ModuleManager for modules that implement
 * ModuleUIProvider and creates a tabbed interface where each module can contribute
 * its own content.
 *
 * Features:
 * - Dynamic tab generation based on enabled modules
 * - Automatic tab ordering by module priority
 * - Event routing to the appropriate module handler
 * - Consistent header and theme across all tabs
 * - Support for up to 6 module tabs
 */
public class CoreSettingsPage extends InteractiveCustomUIPage<CoreSettingsPage.EventData> {

    private static final int MAX_TABS = 6;

    // Tab colors for active/inactive states (from ColorUtil)
    private static final String TAB_COLOR_ACTIVE = com.livinglands.util.ColorUtil.getHexColor("ui_tab_active");
    private static final String TAB_COLOR_INACTIVE = com.livinglands.util.ColorUtil.getHexColor("ui_tab_inactive");

    // Event data codec
    private static final KeyedCodec<String> ACTION_CODEC = new KeyedCodec<>("Action", new StringCodec());
    private static final KeyedCodec<String> VALUE_CODEC = new KeyedCodec<>("Value", new StringCodec());
    // TextField value capture - uses "@ElementId" to capture TextField content when events fire
    // The element must have Ref: "@ElementId" attribute in the UI template
    // This matches Hytale's native pattern (e.g., RespawnPointPage uses "@RespawnPointName")
    private static final KeyedCodec<String> CLAIMS_RENAME_INPUT_CODEC = new KeyedCodec<>("@ClaimsRenameInput", new StringCodec());

    public static final BuilderCodec<EventData> EVENT_DATA_CODEC = BuilderCodec.builder(EventData.class, EventData::new)
        .addField(ACTION_CODEC, EventData::setAction, EventData::action)
        .addField(VALUE_CODEC, EventData::setValue, EventData::value)
        .addField(CLAIMS_RENAME_INPUT_CODEC, EventData::setClaimsRenameInput, EventData::claimsRenameInput)
        .build();

    private final ModuleManager moduleManager;
    private final HytaleLogger logger;

    // Player world info for claims grid
    private final String worldId;
    private final int playerChunkX;
    private final int playerChunkZ;

    // Cached list of modules with UI providers
    private final List<ModuleTabInfo> moduleTabs;

    // Current active tab index
    private int activeTabIndex = 0;

    /**
     * Event data class for UI interactions.
     */
    public static class EventData {
        private String action;
        private String value;
        private String claimsRenameInput;

        public EventData() {}

        public String action() { return action; }
        public void setAction(String action) { this.action = action; }

        public String value() { return value; }
        public void setValue(String value) { this.value = value; }

        public String claimsRenameInput() { return claimsRenameInput; }
        public void setClaimsRenameInput(String claimsRenameInput) { this.claimsRenameInput = claimsRenameInput; }
    }

    /**
     * Information about a module tab.
     */
    private record ModuleTabInfo(
        String moduleId,
        String tabName,
        int tabOrder,
        ModuleUIProvider provider
    ) {}

    public CoreSettingsPage(@Nonnull PlayerRef playerRef,
                           @Nonnull ModuleManager moduleManager,
                           @Nonnull HytaleLogger logger,
                           @Nonnull String worldId,
                           int playerChunkX,
                           int playerChunkZ) {
        super(playerRef, CustomPageLifetime.CanDismiss, EVENT_DATA_CODEC);
        this.moduleManager = moduleManager;
        this.logger = logger;
        this.worldId = worldId;
        this.playerChunkX = playerChunkX;
        this.playerChunkZ = playerChunkZ;
        this.moduleTabs = collectModuleTabs();

        // Initialize position for ClaimsUIProvider
        initializeClaimsProvider();
    }

    /**
     * Initializes the ClaimsUIProvider with the player's current position.
     */
    private void initializeClaimsProvider() {
        logger.at(Level.FINE).log("[CoreSettingsPage] initializeClaimsProvider: tabs=%d, worldId='%s', chunk=[%d,%d]",
            moduleTabs.size(), worldId, playerChunkX, playerChunkZ);
        for (ModuleTabInfo tab : moduleTabs) {
            if (tab.provider() instanceof com.livinglands.modules.claims.ui.ClaimsUIProvider claimsProvider) {
                logger.at(Level.FINE).log("[CoreSettingsPage] Found ClaimsUIProvider, calling setPlayerPosition");
                claimsProvider.setPlayerPosition(playerRef.getUuid(), worldId, playerChunkX, playerChunkZ);
            }
        }
    }

    /**
     * Collects all modules that provide UI tabs, sorted by tab order.
     */
    private List<ModuleTabInfo> collectModuleTabs() {
        var tabs = new ArrayList<ModuleTabInfo>();

        for (Module module : moduleManager.getAllModules()) {
            if (!module.isEnabled()) continue;

            // Check if module provides a UI provider via getUIProvider()
            ModuleUIProvider provider = null;
            if (module instanceof AbstractModule abstractModule) {
                provider = abstractModule.getUIProvider();
            }

            if (provider != null) {
                tabs.add(new ModuleTabInfo(
                    module.getId(),
                    provider.getTabName(),
                    provider.getTabOrder(),
                    provider
                ));
            }
        }

        // Sort by tab order (lower numbers first)
        tabs.sort(Comparator.comparingInt(ModuleTabInfo::tabOrder));

        // Limit to MAX_TABS
        if (tabs.size() > MAX_TABS) {
            logger.at(Level.WARNING).log(
                "[CoreSettingsPage] Too many module tabs (%d), limiting to %d",
                tabs.size(), MAX_TABS
            );
            tabs = new ArrayList<>(tabs.subList(0, MAX_TABS));
        }

        return tabs;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                     @Nonnull UICommandBuilder cmd,
                     @Nonnull UIEventBuilder events,
                     @Nonnull Store<EntityStore> store) {
        // Load the UI template
        cmd.append("Pages/CoreSettingsPage.ui");

        // Ensure active tab is valid
        if (activeTabIndex >= moduleTabs.size()) {
            activeTabIndex = 0;
        }

        if (moduleTabs.isEmpty()) {
            // Show "no modules" message
            cmd.set("#NoModules.Visible", true);

            // Hide all tabs and content areas
            for (int i = 0; i < MAX_TABS; i++) {
                cmd.set("#Tab" + i + ".Visible", false);
                cmd.set("#ModuleContent" + i + ".Visible", false);
            }
        } else {
            // Hide "no modules" message
            cmd.set("#NoModules.Visible", false);

            // Build tabs
            buildTabs(cmd, events);

            // Build active tab content
            buildActiveTabContent(cmd, events);
        }

        // Bind close button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn",
            com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "close"));
    }

    /**
     * Builds the tab bar with visible tabs for each module.
     */
    private void buildTabs(UICommandBuilder cmd, UIEventBuilder events) {
        for (int i = 0; i < MAX_TABS; i++) {
            if (i < moduleTabs.size()) {
                ModuleTabInfo tabInfo = moduleTabs.get(i);

                // Show tab
                cmd.set("#Tab" + i + ".Visible", true);
                cmd.set("#Tab" + i + ".Text", tabInfo.tabName());

                // Set active/inactive style
                String bgColor = (i == activeTabIndex) ? TAB_COLOR_ACTIVE : TAB_COLOR_INACTIVE;
                cmd.set("#Tab" + i + ".Style.Default.Background", bgColor);

                // Bind click event
                events.addEventBinding(CustomUIEventBindingType.Activating, "#Tab" + i,
                    com.hypixel.hytale.server.core.ui.builder.EventData.of("Action", "tab:" + i));
            } else {
                // Hide unused tabs
                cmd.set("#Tab" + i + ".Visible", false);
            }
        }
    }

    /**
     * Builds the content for the currently active tab.
     */
    private void buildActiveTabContent(UICommandBuilder cmd, UIEventBuilder events) {
        // Hide all content areas first
        for (int i = 0; i < MAX_TABS; i++) {
            cmd.set("#ModuleContent" + i + ".Visible", false);
        }

        // Show active content area
        if (activeTabIndex < moduleTabs.size()) {
            cmd.set("#ModuleContent" + activeTabIndex + ".Visible", true);

            ModuleTabInfo tabInfo = moduleTabs.get(activeTabIndex);

            try {
                // Let the module build its content
                tabInfo.provider().buildTabContent(cmd, events, this.playerRef);
            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log(
                    "[CoreSettingsPage] Error building content for module '%s'",
                    tabInfo.moduleId()
                );

                // Show error message in content area
                cmd.set("#ModuleContent" + activeTabIndex + ".Visible", true);
                // Note: Would need an error label in the UI template to show this properly
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull EventData data) {
        String action = data.action();
        // DEBUG: Log at INFO level to ensure visibility
        logger.at(Level.FINE).log("[CoreSettingsPage] handleDataEvent: action='%s', value='%s', claimsRenameInput='%s'",
            action, data.value(), data.claimsRenameInput());
        if (action == null) return;

        // Handle tab navigation
        if (action.startsWith("tab:")) {
            int tabIndex = Integer.parseInt(action.substring(4));
            if (tabIndex >= 0 && tabIndex < moduleTabs.size()) {
                activeTabIndex = tabIndex;
                rebuild();
            }
            return;
        }

        // Handle close button
        if ("close".equals(action)) {
            close();
            return;
        }

        // Route event to active module
        if (activeTabIndex < moduleTabs.size()) {
            ModuleTabInfo tabInfo = moduleTabs.get(activeTabIndex);

            try {
                // For claims_rename action, pass the TextField value from the codec
                String eventValue = "claims_rename".equals(action) ? data.claimsRenameInput() : data.value();
                tabInfo.provider().handleTabEvent(action, eventValue, this.playerRef, this::rebuild);
            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log(
                    "[CoreSettingsPage] Error handling event for module '%s': action='%s'",
                    tabInfo.moduleId(), action
                );
            }
        }
    }

    /**
     * Gets the currently active tab index.
     */
    public int getActiveTabIndex() {
        return activeTabIndex;
    }

    /**
     * Sets the active tab by index.
     */
    public void setActiveTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < moduleTabs.size()) {
            activeTabIndex = tabIndex;
        }
    }

    /**
     * Gets the list of module tabs.
     */
    public List<ModuleTabInfo> getModuleTabs() {
        return List.copyOf(moduleTabs);
    }
}
