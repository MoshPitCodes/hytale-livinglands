package com.livinglands.modules.claims;

import com.livinglands.api.AbstractModule;

import java.util.Set;

/**
 * Plot/Land Claims module for Living Lands.
 *
 * Provides land protection and ownership features:
 * - Chunk-based claim system (16x16 blocks per claim)
 * - Permission tiers: Owner, Trusted, Accessor
 * - Per-player configurable claim limits
 * - Protection for blocks, entities, and containers
 * - Claim visualization via particles or borders
 *
 * This is a placeholder for future implementation.
 */
public final class ClaimsModule extends AbstractModule {

    public static final String ID = "claims";
    public static final String NAME = "Land Claims";
    public static final String VERSION = "1.0.0";

    public ClaimsModule() {
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
