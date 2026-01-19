package com.livinglands.modules.leveling.commands;

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
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.profession.ProfessionType;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;

/**
 * Admin command to set a player's profession level.
 * Usage: /setlevel <profession> <level>
 * Permission: OP/Admin only
 */
public class SetLevelCommand extends AbstractPlayerCommand {

    private final LevelingSystem system;
    private final RequiredArg<String> professionArg;
    private final RequiredArg<Integer> levelArg;

    public SetLevelCommand(LevelingSystem system) {
        super("setlevel", "Set a player's profession level (admin)", true); // requiresOp = true
        this.system = system;

        // Required arguments
        this.professionArg = withRequiredArg("profession",
            "combat, mining, building, logging, gathering", ArgTypes.STRING);
        this.levelArg = withRequiredArg("level", "The level to set (1-99)", ArgTypes.INTEGER);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        String professionName = ctx.get(professionArg);
        Integer level = ctx.get(levelArg);

        // Parse profession
        ProfessionType profession = null;
        for (ProfessionType p : ProfessionType.values()) {
            if (p.name().equalsIgnoreCase(professionName) ||
                p.getDisplayName().equalsIgnoreCase(professionName)) {
                profession = p;
                break;
            }
        }

        if (profession == null) {
            ctx.sendMessage(
                Message.raw("Unknown profession: " + professionName)
                    .color(ColorUtil.getHexColor("red"))
            );
            ctx.sendMessage(
                Message.raw("Available: combat, mining, building, logging, gathering")
                    .color(ColorUtil.getHexColor("gray"))
            );
            return;
        }

        // Validate level range
        int maxLevel = system.getConfig().maxLevel;
        if (level < 1 || level > maxLevel) {
            ctx.sendMessage(
                Message.raw("Level must be between 1 and " + maxLevel)
                    .color(ColorUtil.getHexColor("red"))
            );
            return;
        }

        // Use self as target
        var playerId = playerRef.getUuid();

        // Check if player data is loaded
        if (system.getPlayerData(playerId).isEmpty()) {
            ctx.sendMessage(
                Message.raw("Your leveling data is not loaded!")
                    .color(ColorUtil.getHexColor("red"))
            );
            return;
        }

        // Set the level
        system.setLevel(playerId, profession, level);

        ctx.sendMessage(
            Message.raw("Set your ")
                .color(ColorUtil.getHexColor("green"))
                .insert(
                    Message.raw(profession.getDisplayName())
                        .color(ColorUtil.getHexColor("aqua"))
                )
                .insert(
                    Message.raw(" level to ")
                        .color(ColorUtil.getHexColor("green"))
                )
                .insert(
                    Message.raw(String.valueOf(level))
                        .color(ColorUtil.getHexColor("yellow"))
                )
        );
    }
}
