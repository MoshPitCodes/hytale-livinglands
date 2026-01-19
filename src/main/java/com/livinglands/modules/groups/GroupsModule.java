package com.livinglands.modules.groups;

import com.livinglands.api.AbstractModule;

import java.util.Set;

/**
 * Groups module for Living Lands.
 *
 * Provides clan/party management features:
 * - Create and manage player groups
 * - Group chat and communication
 * - Shared permissions and access
 * - Group-based gameplay bonuses
 *
 * This is a placeholder for future implementation.
 */
public final class GroupsModule extends AbstractModule {

    public static final String ID = "groups";
    public static final String NAME = "Group Management";
    public static final String VERSION = "1.0.0";

    public GroupsModule() {
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
