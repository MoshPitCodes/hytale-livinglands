package com.livinglands.core.config;

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.ModVersion;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Manages automatic backups of configuration files when the mod version changes.
 *
 * On startup, compares the current mod version with the last recorded version.
 * If they differ (indicating a new release), all existing configs are backed up
 * to a timestamped backup directory before the new configs are loaded.
 *
 * Backup structure:
 * LivingLands/
 * ├── backups/
 * │   ├── v2.4.0-beta_2024-01-15_10-30-45/
 * │   │   ├── modules.json
 * │   │   ├── metabolism/
 * │   │   │   └── config.json
 * │   │   └── ...
 * │   └── v2.3.0-beta_2024-01-01_08-00-00/
 * │       └── ...
 * └── .version  (tracks last loaded version)
 */
public final class ConfigBackupManager {

    private static final String VERSION_FILE = ".version";
    private static final String BACKUPS_DIR = "backups";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private ConfigBackupManager() {
        // Utility class
    }

    /**
     * Checks if a backup is needed and performs it if version changed.
     *
     * Should be called early in plugin startup, before configs are loaded.
     *
     * @param pluginDirectory Plugin data directory (LivingLands/)
     * @param logger          Logger for status messages
     * @return true if backup was performed, false if no backup needed
     */
    public static boolean backupIfVersionChanged(@Nonnull Path pluginDirectory,
                                                  @Nonnull HytaleLogger logger) {
        String currentVersion = ModVersion.get();
        String lastVersion = readLastVersion(pluginDirectory, logger);

        // First run or version file missing - just record current version
        if (lastVersion == null) {
            logger.at(Level.FINE).log("First run detected, recording version: %s", currentVersion);
            writeCurrentVersion(pluginDirectory, currentVersion, logger);
            return false;
        }

        // Same version - no backup needed
        if (currentVersion.equals(lastVersion)) {
            logger.at(Level.FINE).log("Version unchanged (%s), no backup needed", currentVersion);
            return false;
        }

        // Version changed - perform backup
        logger.at(Level.FINE).log("========================================");
        logger.at(Level.FINE).log("Version change detected: %s -> %s", lastVersion, currentVersion);
        logger.at(Level.FINE).log("Backing up existing configuration...");
        logger.at(Level.FINE).log("========================================");

        boolean backupSuccess = performBackup(pluginDirectory, lastVersion, logger);

        if (backupSuccess) {
            writeCurrentVersion(pluginDirectory, currentVersion, logger);
            logger.at(Level.FINE).log("Configuration backup completed successfully");
        } else {
            logger.at(Level.WARNING).log("Configuration backup failed, proceeding without backup");
            // Still update version to avoid repeated backup attempts
            writeCurrentVersion(pluginDirectory, currentVersion, logger);
        }

        return backupSuccess;
    }

    /**
     * Reads the last recorded version from the version file.
     *
     * @return Last version string, or null if file doesn't exist
     */
    private static String readLastVersion(@Nonnull Path pluginDirectory,
                                          @Nonnull HytaleLogger logger) {
        Path versionFile = pluginDirectory.resolve(VERSION_FILE);

        if (!Files.exists(versionFile)) {
            return null;
        }

        try {
            String content = Files.readString(versionFile).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to read version file");
            return null;
        }
    }

    /**
     * Writes the current version to the version file.
     */
    private static void writeCurrentVersion(@Nonnull Path pluginDirectory,
                                            @Nonnull String version,
                                            @Nonnull HytaleLogger logger) {
        Path versionFile = pluginDirectory.resolve(VERSION_FILE);

        try {
            Files.createDirectories(pluginDirectory);
            Files.writeString(versionFile, version);
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to write version file");
        }
    }

    /**
     * Performs the actual backup of all configuration files.
     *
     * @param pluginDirectory Plugin data directory
     * @param version         Version being backed up
     * @param logger          Logger for status messages
     * @return true if backup succeeded
     */
    private static boolean performBackup(@Nonnull Path pluginDirectory,
                                         @Nonnull String version,
                                         @Nonnull HytaleLogger logger) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String backupDirName = "v" + version + "_" + timestamp;
        Path backupsDir = pluginDirectory.resolve(BACKUPS_DIR);
        Path backupDir = backupsDir.resolve(backupDirName);

        try {
            // Create backup directory
            Files.createDirectories(backupDir);
            logger.at(Level.FINE).log("Created backup directory: %s", backupDir);

            int filesBackedUp = 0;

            // Copy all files and directories from plugin directory
            try (Stream<Path> paths = Files.list(pluginDirectory)) {
                for (Path source : paths.toList()) {
                    String fileName = source.getFileName().toString();

                    // Skip backups directory, version file, and hidden files (except .version)
                    if (fileName.equals(BACKUPS_DIR) ||
                        fileName.equals(VERSION_FILE) ||
                        (fileName.startsWith(".") && !fileName.equals(VERSION_FILE))) {
                        continue;
                    }

                    Path target = backupDir.resolve(fileName);

                    if (Files.isDirectory(source)) {
                        // Recursively copy directory
                        filesBackedUp += copyDirectory(source, target, logger);
                    } else if (Files.isRegularFile(source)) {
                        // Copy file
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        filesBackedUp++;
                        logger.at(Level.FINE).log("  Backed up: %s", fileName);
                    }
                }
            }

            if (filesBackedUp == 0) {
                // Nothing to backup - remove empty backup directory
                Files.deleteIfExists(backupDir);
                logger.at(Level.FINE).log("No configuration files to backup");
                return true;
            }

            logger.at(Level.FINE).log("Backed up %d file(s) to: %s", filesBackedUp, backupDirName);
            return true;

        } catch (IOException e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to create backup");
            return false;
        }
    }

    /**
     * Recursively copies a directory.
     *
     * @return Number of files copied
     */
    private static int copyDirectory(@Nonnull Path source, @Nonnull Path target,
                                     @Nonnull HytaleLogger logger) throws IOException {
        final int[] count = {0};

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                count[0]++;
                return FileVisitResult.CONTINUE;
            }
        });

        logger.at(Level.FINE).log("  Backed up directory: %s (%d files)",
                source.getFileName(), count[0]);

        return count[0];
    }

    /**
     * Gets the path to the backups directory.
     */
    @Nonnull
    public static Path getBackupsDirectory(@Nonnull Path pluginDirectory) {
        return pluginDirectory.resolve(BACKUPS_DIR);
    }

    /**
     * Checks if any backups exist.
     */
    public static boolean hasBackups(@Nonnull Path pluginDirectory) {
        Path backupsDir = getBackupsDirectory(pluginDirectory);
        if (!Files.exists(backupsDir)) {
            return false;
        }

        try (Stream<Path> paths = Files.list(backupsDir)) {
            return paths.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }
}
