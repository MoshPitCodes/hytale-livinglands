package com.livinglands.core.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.hud.LivingLandsPanelElement;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Main Living Lands command with subcommands.
 *
 * Usage:
 *   /ll main - Toggle the Living Lands stats panel
 *   /ll help - Show available commands
 */
public class LivingLandsCommand extends AbstractPlayerCommand {

    @Nullable
    private LivingLandsPanelElement panelElement;

    private final RequiredArg<String> subCommandArg;

    public LivingLandsCommand() {
        super("ll", "Living Lands main command", false);
        this.subCommandArg = withRequiredArg("subcommand", "main, help", ArgTypes.STRING);
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
        String subCommand = ctx.get(subCommandArg);

        if (subCommand == null || subCommand.isBlank()) {
            showHelp(ctx);
            return;
        }

        switch (subCommand.toLowerCase()) {
            case "main", "panel", "stats" -> toggleMainPanel(ctx, playerId);
            case "help", "?" -> showHelp(ctx);
            default -> {
                ctx.sendMessage(
                    Message.raw("Unknown subcommand: " + subCommand)
                        .color(ColorUtil.getHexColor("red"))
                );
                showHelp(ctx);
            }
        }
    }

    private void toggleMainPanel(CommandContext ctx, java.util.UUID playerId) {
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

    private void showHelp(CommandContext ctx) {
        ctx.sendMessage(Message.raw("--- Living Lands Commands ---").color(ColorUtil.getHexColor("gold")));
        ctx.sendMessage(Message.raw("/ll main  - Toggle the stats panel").color(ColorUtil.getHexColor("white")));
        ctx.sendMessage(Message.raw("/ll help  - Show this help").color(ColorUtil.getHexColor("white")));
    }
}
