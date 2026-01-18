package com.livinglands.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.metabolism.EnergyStat;
import com.livinglands.metabolism.HungerStat;
import com.livinglands.metabolism.MetabolismSystem;
import com.livinglands.metabolism.ThirstStat;

import javax.annotation.Nonnull;

/**
 * Command to display all player survival stats at once.
 *
 * Usage: /stats
 * Permission: livinglands.stats
 *
 * Shows hunger, thirst, and energy in a compact format.
 */
public class StatsCommand extends AbstractPlayerCommand {

    private final MetabolismSystem metabolismSystem;

    public StatsCommand(@Nonnull MetabolismSystem metabolismSystem) {
        super("stats", "Display your survival stats", false);
        this.metabolismSystem = metabolismSystem;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        try {
            var dataOpt = metabolismSystem.getPlayerData(playerRef.getUuid());

            double hunger = dataOpt.map(d -> d.getHunger()).orElse(HungerStat.DEFAULT_VALUE);
            double thirst = dataOpt.map(d -> d.getThirst()).orElse(ThirstStat.DEFAULT_VALUE);
            double energy = dataOpt.map(d -> d.getEnergy()).orElse(EnergyStat.DEFAULT_VALUE);

            // Header
            ctx.sendMessage(
                Message.raw("===== Survival Stats =====")
                    .color("aqua")
                    .bold(true)
            );

            // Hunger line
            ctx.sendMessage(
                Message.raw("Hunger: ")
                    .color("gray")
                    .insert(
                        Message.raw(String.format("%.0f", hunger))
                            .color(HungerStat.getColorCode(hunger))
                    )
                    .insert(Message.raw(" - ").color("dark_gray"))
                    .insert(
                        Message.raw(HungerStat.getDisplayString(hunger))
                            .color(HungerStat.getColorCode(hunger))
                    )
            );

            // Thirst line
            ctx.sendMessage(
                Message.raw("Thirst: ")
                    .color("gray")
                    .insert(
                        Message.raw(String.format("%.0f", thirst))
                            .color(ThirstStat.getColorCode(thirst))
                    )
                    .insert(Message.raw(" - ").color("dark_gray"))
                    .insert(
                        Message.raw(ThirstStat.getDisplayString(thirst))
                            .color(ThirstStat.getColorCode(thirst))
                    )
            );

            // Energy line
            ctx.sendMessage(
                Message.raw("Energy: ")
                    .color("gray")
                    .insert(
                        Message.raw(String.format("%.0f", energy))
                            .color(EnergyStat.getColorCode(energy))
                    )
                    .insert(Message.raw(" - ").color("dark_gray"))
                    .insert(
                        Message.raw(EnergyStat.getDisplayString(energy))
                            .color(EnergyStat.getColorCode(energy))
                    )
            );

            // Warnings for critical stats
            boolean hasWarning = false;

            if (HungerStat.isStarving(hunger)) {
                ctx.sendMessage(
                    Message.raw("You are starving!")
                        .color("red")
                        .bold(true)
                );
                hasWarning = true;
            }

            if (ThirstStat.isDehydrated(thirst)) {
                ctx.sendMessage(
                    Message.raw("You are dehydrated!")
                        .color("red")
                        .bold(true)
                );
                hasWarning = true;
            }

            if (EnergyStat.isExhausted(energy)) {
                ctx.sendMessage(
                    Message.raw("You are exhausted!")
                        .color("red")
                        .bold(true)
                );
                hasWarning = true;
            }

        } catch (Exception e) {
            ctx.sendMessage(
                Message.raw("Error retrieving stats!")
                    .color("red")
            );
        }
    }
}
