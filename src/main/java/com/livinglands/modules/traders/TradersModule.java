package com.livinglands.modules.traders;

import com.livinglands.api.AbstractModule;

import java.util.Set;

/**
 * Traders module for Living Lands.
 *
 * Provides NPC trader features:
 * - Merchant NPCs based on Klops_Merchant model
 * - Trader types: General, Food, Equipment, Banker
 * - Custom trade UI with item browsing
 * - Stock management with restocking timers
 * - Configurable trade offers and pricing
 *
 * This module depends on the Economy module for currency handling.
 *
 * This is a placeholder for future implementation.
 */
public final class TradersModule extends AbstractModule {

    public static final String ID = "traders";
    public static final String NAME = "Trader NPCs";
    public static final String VERSION = "1.0.0";

    public TradersModule() {
        // Depends on economy for currency
        super(ID, NAME, VERSION, Set.of("economy"));
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
