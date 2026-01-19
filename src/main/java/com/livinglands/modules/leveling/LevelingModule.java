package com.livinglands.modules.leveling;

import com.livinglands.api.AbstractModule;

import java.util.Set;

/**
 * Leveling module for Living Lands.
 *
 * Provides XP and level progression features:
 * - Experience points from activities
 * - Level progression with milestones
 * - Skills or abilities unlocked at levels
 * - Level-based bonuses
 *
 * This is a placeholder for future implementation.
 */
public final class LevelingModule extends AbstractModule {

    public static final String ID = "leveling";
    public static final String NAME = "Leveling System";
    public static final String VERSION = "1.0.0";

    public LevelingModule() {
        super(ID, NAME, VERSION, Set.of()); // No dependencies
    }

    @Override
    protected void onSetup() {
        logger.at(java.util.logging.Level.INFO).log("[%s] Module not yet implemented", name);
    }

    @Override
    protected void onStart() {
        // Not yet implemented
    }

    @Override
    protected void onShutdown() {
        // Not yet implemented
    }
}
