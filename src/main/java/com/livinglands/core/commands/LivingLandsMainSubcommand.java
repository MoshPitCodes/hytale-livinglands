package com.livinglands.core.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.hud.LivingLandsPanelElement;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Subcommand: /ll main
 * Toggles the Living Lands stats panel.
 */
public class LivingLandsMainSubcommand extends AbstractPlayerCommand {

    private final LivingLandsCommand parentCommand;

    @Nullable
    private LivingLandsPanelElement panelElement;

    public LivingLandsMainSubcommand(LivingLandsCommand parentCommand) {
        super("main", "Toggle the Living Lands stats panel", false);
        this.parentCommand = parentCommand;
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
    }

    public void setPanelElement(@Nullable LivingLandsPanelElement panelElement) {
        this.panelElement = panelElement;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        var playerId = playerRef.getUuid();

        if (panelElement == null) {
            ctx.sendMessage(
                Message.raw("Panel not available.")
                    .color(ColorUtil.getHexColor("red"))
            );
            return;
        }

        boolean nowVisible = panelElement.togglePanel(playerId);

        if (nowVisible) {
            ctx.sendMessage(
                Message.raw("Living Lands panel opened.")
                    .color(ColorUtil.getHexColor("green"))
            );
        } else {
            ctx.sendMessage(
                Message.raw("Living Lands panel closed.")
                    .color(ColorUtil.getHexColor("gray"))
            );
        }
    }
}
