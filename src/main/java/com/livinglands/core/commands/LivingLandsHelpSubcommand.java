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
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;

/**
 * Subcommand: /ll help
 * Shows help information about the mod, how it works, and configuration paths.
 */
public class LivingLandsHelpSubcommand extends AbstractPlayerCommand {

    public LivingLandsHelpSubcommand() {
        super("help", "Show help and configuration info", false);
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        showHelpInfo(ctx);
    }

    private void showHelpInfo(CommandContext ctx) {
        // Header
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));
        ctx.sendMessage(Message.raw("========================================").color(ColorUtil.getHexColor("gold")));
        ctx.sendMessage(Message.raw("       Living Lands - Help Guide").color(ColorUtil.getHexColor("gold")));
        ctx.sendMessage(Message.raw("========================================").color(ColorUtil.getHexColor("gold")));
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));

        // Overview
        ctx.sendMessage(Message.raw("How It Works:").color(ColorUtil.getHexColor("yellow")));
        ctx.sendMessage(Message.raw("  Living Lands adds survival mechanics:").color(ColorUtil.getHexColor("gray")));
        ctx.sendMessage(Message.raw("  - Hunger").color(ColorUtil.getHexColor("green"))
            .insert(Message.raw(" depletes over time, eat food to restore").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("  - Thirst").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" depletes faster, drink potions/water").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("  - Energy").color(ColorUtil.getHexColor("gold"))
            .insert(Message.raw(" depletes slowly, sleep in beds to restore").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));

        // Buffs & Debuffs
        ctx.sendMessage(Message.raw("Buffs (Stats >= 90%):").color(ColorUtil.getHexColor("yellow")));
        ctx.sendMessage(Message.raw("  + Speed Boost").color(ColorUtil.getHexColor("green"))
            .insert(Message.raw(" (Energy high)").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("  + Defense Boost").color(ColorUtil.getHexColor("green"))
            .insert(Message.raw(" (Hunger high)").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("  + Stamina Boost").color(ColorUtil.getHexColor("green"))
            .insert(Message.raw(" (Thirst high)").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));

        ctx.sendMessage(Message.raw("Debuffs (Stats low):").color(ColorUtil.getHexColor("yellow")));
        ctx.sendMessage(Message.raw("  - Starving").color(ColorUtil.getHexColor("red"))
            .insert(Message.raw(" (Hunger = 0) - Takes damage").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("  - Dehydrated").color(ColorUtil.getHexColor("red"))
            .insert(Message.raw(" (Thirst = 0) - Takes damage").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("  - Exhausted").color(ColorUtil.getHexColor("red"))
            .insert(Message.raw(" (Energy = 0) - Stamina drains").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));

        // Configuration
        ctx.sendMessage(Message.raw("Configuration Paths:").color(ColorUtil.getHexColor("yellow")));
        ctx.sendMessage(Message.raw("  LivingLands/modules.json").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Enable/disable modules").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("  LivingLands/metabolism/config.json").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Metabolism settings").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("  LivingLands/leveling/config.json").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Leveling settings").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("  LivingLands/playerdata/").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Per-player saved data").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));

        // Links
        ctx.sendMessage(Message.raw("More Info:").color(ColorUtil.getHexColor("yellow")));
        ctx.sendMessage(Message.raw("  GitHub: github.com/MoshPitCodes/hytale-livinglands").color(ColorUtil.getHexColor("gray")));
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));
    }
}
