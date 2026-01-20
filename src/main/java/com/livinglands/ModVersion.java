package com.livinglands;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the mod version from a single source of truth.
 * Reads from version.properties bundled in the JAR.
 */
public final class ModVersion {

    private static final String VERSION;

    static {
        String version = "unknown";
        try (InputStream is = ModVersion.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                version = props.getProperty("mod.version", "unknown");
            }
        } catch (IOException ignored) {
            // Fall back to unknown
        }
        VERSION = version;
    }

    private ModVersion() {}

    /**
     * Gets the mod version string.
     * @return The version (e.g., "2.4.0-beta")
     */
    public static String get() {
        return VERSION;
    }
}
