package com.livinglands.modules.leveling.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.PlayerLevelingData;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Command to toggle the skills GUI display.
 * Usage: /skillgui [on|off]
 * Without argument, toggles the current state.
 */
public class SkillsGuiCommand extends AbstractPlayerCommand {

    private final LevelingSystem system;
    private final DefaultArg<String> stateArg;

    public SkillsGuiCommand(LevelingSystem system) {
        super("skillgui", "Toggle the skills GUI display", false);
        this.system = system;

        // Optional argument: state (on/off/toggle)
        this.stateArg = withDefaultArg("state", "on, off, or toggle",
            ArgTypes.STRING, "toggle", "toggle");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        var playerId = playerRef.getUuid();
        Optional<PlayerLevelingData> dataOpt = system.getPlayerData(playerId);

        if (dataOpt.isEmpty()) {
            ctx.sendMessage(
                Message.raw("Your leveling data is not loaded!")
                    .color(ColorUtil.getHexColor("red"))
            );
            return;
        }

        var data = dataOpt.get();
        String stateValue = ctx.get(stateArg).toLowerCase();

        boolean newState;
        switch (stateValue) {
            case "on", "true", "1", "enable" -> newState = true;
            case "off", "false", "0", "disable" -> newState = false;
            case "toggle" -> newState = !data.isHudEnabled();
            default -> {
                ctx.sendMessage(
                    Message.raw("Invalid argument. Use: /skillgui [on|off|toggle]")
                        .color(ColorUtil.getHexColor("red"))
                );
                return;
            }
        }

        data.setHudEnabled(newState);
        data.markDirty();

        if (newState) {
            ctx.sendMessage(
                Message.raw("Skills GUI enabled. XP progress will be displayed on screen.")
                    .color(ColorUtil.getHexColor("green"))
            );
        } else {
            ctx.sendMessage(
                Message.raw("Skills GUI disabled.")
                    .color(ColorUtil.getHexColor("red"))
            );
        }
    }
}
