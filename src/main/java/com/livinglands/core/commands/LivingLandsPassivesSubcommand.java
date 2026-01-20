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
import com.livinglands.core.hud.HudModule;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Subcommand: /ll passives
 * Toggles the passive abilities display on the left HUD.
 */
public class LivingLandsPassivesSubcommand extends AbstractPlayerCommand {

    @Nullable
    private HudModule hudModule;

    public LivingLandsPassivesSubcommand() {
        super("passives", "Toggle passive abilities display on HUD", false);
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
    }

    public void setHudModule(@Nullable HudModule hudModule) {
        this.hudModule = hudModule;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        var playerId = playerRef.getUuid();

        if (hudModule == null) {
            ctx.sendMessage(
                Message.raw("HUD module not available.")
                    .color(ColorUtil.getHexColor("red"))
            );
            return;
        }

        var metabolismHud = hudModule.getMetabolismHudElement();
        if (metabolismHud == null) {
            ctx.sendMessage(
                Message.raw("Metabolism HUD not available.")
                    .color(ColorUtil.getHexColor("red"))
            );
            return;
        }

        boolean nowVisible = metabolismHud.togglePassivesDisplay(playerId);

        if (nowVisible) {
            ctx.sendMessage(
                Message.raw("Passive abilities display enabled.")
                    .color(ColorUtil.getHexColor("green"))
            );
        } else {
            ctx.sendMessage(
                Message.raw("Passive abilities display disabled.")
                    .color(ColorUtil.getHexColor("gray"))
            );
        }
    }
}
