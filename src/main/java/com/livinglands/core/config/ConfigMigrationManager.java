package com.livinglands.core.config;

import com.google.gson.*;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.logging.Level;

/**
 * Handles migration of configuration values from old configs to new configs.
 *
 * When loading a config file:
 * 1. Load the existing config JSON (if exists)
 * 2. Create a new default config instance
 * 3. Merge existing values into the new config (preserving user customizations)
 * 4. New fields get default values, removed fields are dropped
 *
 * This allows configs to evolve across versions while preserving user settings.
 */
public final class ConfigMigrationManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private ConfigMigrationManager() {
        // Utility class
    }

    /**
     * Migrates an existing config JSON to a new config type.
     *
     * The migration process:
     * 1. Parse existing JSON into a JsonObject
     * 2. Create default config and serialize to JsonObject
     * 3. Deep merge existing values into default (existing wins for matching keys)
     * 4. Deserialize merged result to target type
     *
     * @param existingJson  The existing config JSON string (may be from older version)
     * @param defaultConfig The default config instance with all current fields
     * @param type          The config class type
     * @param logger        Logger for migration messages
     * @param configName    Name of config for logging
     * @param <T>           Config type
     * @return Migrated config with existing values preserved and new fields defaulted
     */
    @Nonnull
    public static <T> T migrateConfig(@Nonnull String existingJson,
                                       @Nonnull T defaultConfig,
                                       @Nonnull Class<T> type,
                                       @Nonnull HytaleLogger logger,
                                       @Nonnull String configName) {
        try {
            // Parse existing config
            JsonObject existingObj = JsonParser.parseString(existingJson).getAsJsonObject();

            // Serialize default config to JSON
            JsonObject defaultObj = GSON.toJsonTree(defaultConfig).getAsJsonObject();

            // Track migration stats
            MigrationStats stats = new MigrationStats();

            // Deep merge: existing values into default structure
            JsonObject mergedObj = deepMerge(defaultObj, existingObj, "", stats);

            // Log migration summary
            if (stats.preserved > 0 || stats.added > 0 || stats.removed > 0) {
                logger.at(Level.FINE).log("[ConfigMigration] %s: preserved %d, added %d new, dropped %d obsolete",
                        configName, stats.preserved, stats.added, stats.removed);
            }

            // Deserialize merged result
            return GSON.fromJson(mergedObj, type);

        } catch (JsonSyntaxException e) {
            logger.at(Level.WARNING).withCause(e).log(
                    "[ConfigMigration] Failed to parse existing config %s, using defaults", configName);
            return defaultConfig;
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log(
                    "[ConfigMigration] Migration failed for %s, using defaults", configName);
            return defaultConfig;
        }
    }

    /**
     * Deep merges source values into target structure.
     *
     * Rules:
     * - If key exists in both and both are objects: recurse
     * - If key exists in both and types match: use source value (preserve user setting)
     * - If key exists in both but types differ: use target value (schema changed)
     * - If key only in target: keep target value (new field with default)
     * - If key only in source: drop it (obsolete field)
     *
     * @param target The target (default) structure
     * @param source The source (existing) values
     * @param path   Current path for logging
     * @param stats  Migration statistics
     * @return Merged JsonObject
     */
    private static JsonObject deepMerge(@Nonnull JsonObject target,
                                         @Nonnull JsonObject source,
                                         @Nonnull String path,
                                         @Nonnull MigrationStats stats) {
        JsonObject result = target.deepCopy();

        // Process all keys in source (existing config)
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = entry.getKey();
            JsonElement sourceValue = entry.getValue();
            String currentPath = path.isEmpty() ? key : path + "." + key;

            if (!target.has(key)) {
                // Key removed in new version - drop it
                stats.removed++;
                continue;
            }

            JsonElement targetValue = target.get(key);

            if (sourceValue.isJsonObject() && targetValue.isJsonObject()) {
                // Both are objects - recurse
                JsonObject mergedChild = deepMerge(
                        targetValue.getAsJsonObject(),
                        sourceValue.getAsJsonObject(),
                        currentPath,
                        stats);
                result.add(key, mergedChild);
            } else if (isSameType(sourceValue, targetValue)) {
                // Same type - preserve existing value
                result.add(key, sourceValue.deepCopy());
                stats.preserved++;
            } else {
                // Type changed - keep default (already in result)
                stats.added++;
            }
        }

        // Count new fields (in target but not in source)
        for (String key : target.keySet()) {
            if (!source.has(key)) {
                stats.added++;
            }
        }

        return result;
    }

    /**
     * Checks if two JSON elements have compatible types.
     */
    private static boolean isSameType(@Nullable JsonElement a, @Nullable JsonElement b) {
        if (a == null || b == null) return false;
        if (a.isJsonNull() || b.isJsonNull()) return true; // Nulls are compatible with anything
        if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            JsonPrimitive pa = a.getAsJsonPrimitive();
            JsonPrimitive pb = b.getAsJsonPrimitive();
            // Check primitive type compatibility
            if (pa.isBoolean() && pb.isBoolean()) return true;
            if (pa.isNumber() && pb.isNumber()) return true;
            if (pa.isString() && pb.isString()) return true;
            return false;
        }
        if (a.isJsonArray() && b.isJsonArray()) return true;
        if (a.isJsonObject() && b.isJsonObject()) return true;
        return false;
    }

    /**
     * Tracks migration statistics.
     */
    private static class MigrationStats {
        int preserved = 0;  // Existing values kept
        int added = 0;      // New fields with defaults
        int removed = 0;    // Obsolete fields dropped
    }
}
