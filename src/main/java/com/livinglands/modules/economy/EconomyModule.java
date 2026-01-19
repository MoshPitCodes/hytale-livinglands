package com.livinglands.modules.economy;

import com.livinglands.api.AbstractModule;

import java.util.Set;

/**
 * Economy module for Living Lands.
 *
 * Provides currency and transaction features:
 * - Multi-tier currency (Copper, Silver, Gold)
 * - Player wallets and bank accounts
 * - Interest accumulation
 * - Transaction logging and history
 *
 * This is a placeholder for future implementation.
 */
public final class EconomyModule extends AbstractModule {

    public static final String ID = "economy";
    public static final String NAME = "Economy System";
    public static final String VERSION = "1.0.0";

    public EconomyModule() {
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
