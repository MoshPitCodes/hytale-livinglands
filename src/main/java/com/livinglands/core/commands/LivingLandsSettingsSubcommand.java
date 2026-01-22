package com.livinglands.core.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.ModuleManager;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.ui.CoreSettingsPage;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * /ll settings - Opens the core settings panel with dynamic module tabs.
 */
public class LivingLandsSettingsSubcommand extends AbstractPlayerCommand {

    @Nullable
    private ModuleManager moduleManager;

    @Nullable
    private HytaleLogger logger;

    @Nullable
    private PlayerRegistry playerRegistry;

    public LivingLandsSettingsSubcommand() {
        super("settings", "Open the Living Lands settings panel", false);
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
    }

    /**
     * Sets the module manager for accessing enabled modules.
     * Called by HudModule during setup.
     */
    public void setModuleManager(@Nullable ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    /**
     * Sets the logger for this command.
     */
    public void setLogger(@Nullable HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Sets the player registry for accessing player entities.
     */
    public void setPlayerRegistry(@Nullable PlayerRegistry playerRegistry) {
        this.playerRegistry = playerRegistry;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        if (moduleManager == null || logger == null || playerRegistry == null) {
            ctx.sendMessage(Message.raw("Settings panel is not available (not initialized)")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        // Get the player entity from the player registry
        var sessionOpt = playerRegistry.getSession(playerRef.getUuid());
        if (sessionOpt.isEmpty()) {
            ctx.sendMessage(Message.raw("Player session not found")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        var session = sessionOpt.get();
        var player = session.getPlayer();
        if (player == null) {
            ctx.sendMessage(Message.raw("Player entity not available")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        // Get the player's current position for the claims grid
        var transform = playerRef.getTransform();
        if (transform == null) {
            ctx.sendMessage(Message.raw("Unable to determine player position")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        var position = transform.getPosition();
        String worldId = world.getName();
        // Hytale chunks are 32 blocks wide (CHUNK_SIZE = 32, CHUNK_SHIFT = 5)
        int playerChunkX = (int) Math.floor(position.getX() / 32.0);
        int playerChunkZ = (int) Math.floor(position.getZ() / 32.0);

        // Open the core settings page (must be on world thread)
        world.execute(() -> {
            try {
                CoreSettingsPage settingsPage = new CoreSettingsPage(
                    playerRef, moduleManager, logger, worldId, playerChunkX, playerChunkZ);
                player.getPageManager().openCustomPage(ref, store, settingsPage);

                logger.at(Level.FINE).log("[Settings] Opened core settings panel for %s at chunk [%d, %d]",
                    playerRef.getUsername(), playerChunkX, playerChunkZ);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log(
                    "Failed to open settings page for player %s", playerRef.getUsername());
            }
        });
    }
}
