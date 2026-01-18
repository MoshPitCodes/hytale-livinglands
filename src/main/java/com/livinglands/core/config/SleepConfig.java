package com.livinglands.core.config;

import java.util.List;

/**
 * Configuration for sleep/bed mechanics.
 * Allows customization of bed blocks and energy restoration values.
 */
public record SleepConfig(
    BedBlocksSection bedBlocks,
    double energyRestoreAmount,
    long cooldownMs,
    boolean respectSleepSchedule
) {
    /**
     * Creates default sleep configuration.
     */
    public static SleepConfig defaultConfig() {
        return new SleepConfig(
            defaultBedBlocks(),
            35.0,  // Energy restored per sleep (reduced by 30%)
            5000,  // 5 second cooldown
            true   // Respect game's sleep schedule
        );
    }

    /**
     * Section for bed block identifiers.
     */
    public record BedBlocksSection(
        List<String> vanilla,
        List<String> modded
    ) {
        public static BedBlocksSection empty() {
            return new BedBlocksSection(List.of(), List.of());
        }

        /**
         * Checks if a block type is a registered bed.
         * Uses case-insensitive contains matching.
         */
        public boolean isBedBlock(String blockType) {
            if (blockType == null) {
                return false;
            }
            String lower = blockType.toLowerCase();

            // Check vanilla beds
            for (String pattern : vanilla) {
                if (lower.contains(pattern.toLowerCase())) {
                    return true;
                }
            }

            // Check modded beds
            for (String pattern : modded) {
                if (lower.contains(pattern.toLowerCase())) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Default bed block patterns.
     * Using "bed" as a pattern matches any block with "bed" in the name.
     */
    private static BedBlocksSection defaultBedBlocks() {
        return new BedBlocksSection(
            List.of("bed"),  // Matches any block containing "bed"
            List.of()        // Empty modded section
        );
    }
}
