package com.livinglands.core.config;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for module enable/disable states.
 *
 * This is the model for modules.json which controls which modules
 * are loaded on server startup.
 *
 * Example modules.json:
 * {
 *   "enabled": {
 *     "metabolism": true,
 *     "claims": false,
 *     "economy": false,
 *     "leveling": false,
 *     "groups": false,
 *     "traders": false
 *   }
 * }
 */
public class ModulesConfig {

    /**
     * Map of module ID to enabled state.
     */
    public Map<String, Boolean> enabled = new HashMap<>();

    /**
     * Creates an empty config.
     */
    public ModulesConfig() {
    }

    /**
     * Creates a config with the given enabled map.
     */
    public ModulesConfig(@Nonnull Map<String, Boolean> enabled) {
        this.enabled = new HashMap<>(enabled);
    }

    /**
     * Checks if a module is enabled.
     *
     * @param moduleId Module ID to check
     * @return true if explicitly enabled, false if disabled or not listed
     */
    public boolean isEnabled(@Nonnull String moduleId) {
        return enabled.getOrDefault(moduleId, false);
    }

    /**
     * Sets whether a module is enabled.
     *
     * @param moduleId Module ID
     * @param value    true to enable, false to disable
     */
    public void setEnabled(@Nonnull String moduleId, boolean value) {
        enabled.put(moduleId, value);
    }

    /**
     * Gets all module enabled states.
     */
    @Nonnull
    public Map<String, Boolean> getEnabledMap() {
        return new HashMap<>(enabled);
    }

    /**
     * Creates default configuration with metabolism enabled.
     */
    @Nonnull
    public static ModulesConfig defaults() {
        var config = new ModulesConfig();
        config.enabled.put("metabolism", true);
        config.enabled.put("claims", false);
        config.enabled.put("economy", false);
        config.enabled.put("leveling", false);
        config.enabled.put("groups", false);
        config.enabled.put("traders", false);
        return config;
    }
}
