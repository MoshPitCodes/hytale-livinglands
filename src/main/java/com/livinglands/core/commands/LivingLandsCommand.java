package com.livinglands.core.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.hud.LivingLandsPanelElement;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Main Living Lands command.
 *
 * Usage:
 *   /ll - Show available commands
 *   /ll main - Toggle the Living Lands stats panel
 *   /ll help - Show help panel with mod information
 */
public class LivingLandsCommand extends AbstractPlayerCommand {

    @Nullable
    private LivingLandsPanelElement panelElement;

    private final LivingLandsMainSubcommand mainSubcommand;
    private final LivingLandsHelpSubcommand helpSubcommand;

    public LivingLandsCommand() {
        super("ll", "Living Lands main command", false);
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());

        // Create and register subcommands
        this.mainSubcommand = new LivingLandsMainSubcommand(this);
        this.helpSubcommand = new LivingLandsHelpSubcommand();

        addSubCommand(mainSubcommand);
        addSubCommand(helpSubcommand);
    }

    public void setPanelElement(@Nullable LivingLandsPanelElement panelElement) {
        this.panelElement = panelElement;
        this.mainSubcommand.setPanelElement(panelElement);
    }

    @Nullable
    public LivingLandsPanelElement getPanelElement() {
        return panelElement;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        // /ll with no subcommand - show available commands
        showCommandList(ctx);
    }

    private void showCommandList(CommandContext ctx) {
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));
        ctx.sendMessage(Message.raw("=== Living Lands Commands ===").color(ColorUtil.getHexColor("gold")));
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));
        ctx.sendMessage(Message.raw("/ll").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Show this command list").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll main").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Toggle the stats panel").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll help").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Show help and configuration info").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));
        ctx.sendMessage(Message.raw("Admin Commands:").color(ColorUtil.getHexColor("red")));
        ctx.sendMessage(Message.raw("/setlevel <profession> <level>").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Set profession level (OP required)").color(ColorUtil.getHexColor("gray"))));
    }
}
