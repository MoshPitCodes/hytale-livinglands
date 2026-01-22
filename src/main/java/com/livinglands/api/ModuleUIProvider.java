package com.livinglands.api;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface for modules that want to provide content to the core settings UI panel.
 *
 * Modules implementing this interface can contribute a tab to the main settings panel,
 * allowing players to interact with module-specific features through a unified interface.
 *
 * Example implementation:
 * <pre>
 * public class MyModule extends AbstractModule implements ModuleUIProvider {
 *     {@literal @}Override
 *     public String getTabName() {
 *         return "My Tab";
 *     }
 *
 *     {@literal @}Override
 *     public int getTabOrder() {
 *         return 100; // Higher numbers appear to the right
 *     }
 *
 *     {@literal @}Override
 *     public void buildTabContent(UICommandBuilder cmd, UIEventBuilder events, PlayerRef player) {
 *         // Build your tab's UI content
 *         cmd.set("#MyContent.Text", "Hello from my module!");
 *         events.addEventBinding(CustomUIEventBindingType.Activating, "#MyButton",
 *             EventData.of("Action", "my_action"));
 *     }
 *
 *     {@literal @}Override
 *     public void handleTabEvent(String action, String value, PlayerRef player, Runnable rebuild) {
 *         if ("my_action".equals(action)) {
 *             // Handle the action
 *             rebuild.run(); // Rebuild the UI if needed
 *         }
 *     }
 * }
 * </pre>
 */
public interface ModuleUIProvider {

    /**
     * Gets the display name for this module's tab.
     * This text will appear on the tab button in the settings panel.
     *
     * @return Tab display name (e.g., "Claims", "Skills", "Shop")
     */
    @Nonnull
    String getTabName();

    /**
     * Gets an optional icon identifier for the tab.
     * Currently not implemented in the UI system, reserved for future use.
     *
     * @return Icon identifier, or null if no icon
     */
    @Nullable
    default String getTabIcon() {
        return null;
    }

    /**
     * Gets the order priority for this tab in the tab bar.
     * Lower numbers appear first (to the left).
     *
     * Standard ordering:
     * - 0-99: Core/system tabs
     * - 100-199: Gameplay feature tabs (claims, quests, etc.)
     * - 200-299: Social/community tabs
     * - 300+: Utility/admin tabs
     *
     * @return Tab order priority (default: 100)
     */
    default int getTabOrder() {
        return 100;
    }

    /**
     * Builds the UI content for this module's tab.
     * This method is called whenever the tab needs to be rendered or refreshed.
     *
     * Implementation should:
     * - Use cmd to set text, visibility, colors, etc. on UI elements
     * - Use events to bind actions to interactive elements (buttons, text fields, etc.)
     * - Access player-specific data via the playerRef parameter
     *
     * IMPORTANT: All UI element IDs should be prefixed with a module-specific identifier
     * to avoid conflicts with other modules. For example, if your module is "claims",
     * use IDs like "#ClaimsContent", "#ClaimsButton", etc.
     *
     * @param cmd      UI command builder for modifying UI elements
     * @param events   UI event builder for binding actions to elements
     * @param player   The player viewing this tab
     */
    void buildTabContent(@Nonnull UICommandBuilder cmd,
                        @Nonnull UIEventBuilder events,
                        @Nonnull PlayerRef player);

    /**
     * Handles events triggered by user interaction with this module's tab content.
     *
     * This method receives events from interactive elements defined in buildTabContent().
     * The action string should match the "Action" key set in your event bindings.
     *
     * Implementation should:
     * - Parse the action and optional value parameters
     * - Execute the appropriate module logic
     * - Call rebuild.run() if the UI needs to be refreshed after the action
     *
     * Example:
     * <pre>
     * public void handleTabEvent(String action, String value, PlayerRef player, Runnable rebuild) {
     *     switch (action) {
     *         case "toggle_feature" -&gt; {
     *             toggleFeature(player);
     *             rebuild.run(); // Refresh UI to show new state
     *         }
     *         case "select_item" -&gt; {
     *             selectItem(player, value);
     *             rebuild.run();
     *         }
     *     }
     * }
     * </pre>
     *
     * @param action   The action string from the event binding (from "Action" key)
     * @param value    Optional value from the event binding (from "Value" key), may be null
     * @param player   The player who triggered the event
     * @param rebuild  Callback to trigger a full UI rebuild (call if the action changes UI state)
     */
    void handleTabEvent(@Nonnull String action,
                       @Nullable String value,
                       @Nonnull PlayerRef player,
                       @Nonnull Runnable rebuild);
}
