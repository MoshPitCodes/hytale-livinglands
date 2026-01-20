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
import com.livinglands.modules.metabolism.MetabolismSystem;
import com.livinglands.modules.metabolism.buff.BuffType;
import com.livinglands.modules.metabolism.buff.BuffsSystem;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Main Living Lands command with subcommands.
 *
 * Usage:
 *   /ll main - Toggle the Living Lands stats panel
 *   /ll stats - Show survival stats in chat
 *   /ll help - Show available commands
 */
public class LivingLandsCommand extends AbstractPlayerCommand {

    @Nullable
    private LivingLandsPanelElement panelElement;

    @Nullable
    private MetabolismSystem metabolismSystem;

    @Nullable
    private BuffsSystem buffsSystem;

    private final RequiredArg<String> subCommandArg;

    public LivingLandsCommand() {
        super("ll", "Living Lands main command", false);
        this.subCommandArg = withRequiredArg("subcommand", "main, help", ArgTypes.STRING);
    }

    public void setPanelElement(@Nullable LivingLandsPanelElement panelElement) {
        this.panelElement = panelElement;
    }

    public void setMetabolismSystem(@Nullable MetabolismSystem metabolismSystem) {
        this.metabolismSystem = metabolismSystem;
    }

    public void setBuffsSystem(@Nullable BuffsSystem buffsSystem) {
        this.buffsSystem = buffsSystem;
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
            case "main", "panel" -> toggleMainPanel(ctx, playerId);
            case "stats" -> showStats(ctx, playerId);
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

    private void showStats(CommandContext ctx, java.util.UUID playerId) {
        if (metabolismSystem == null) {
            ctx.sendMessage(
                Message.raw("Metabolism system not available.")
                    .color(ColorUtil.getHexColor("red"))
            );
            return;
        }

        var dataOpt = metabolismSystem.getPlayerData(playerId);
        if (dataOpt.isEmpty()) {
            ctx.sendMessage(
                Message.raw("No metabolism data found.")
                    .color(ColorUtil.getHexColor("red"))
            );
            return;
        }

        var data = dataOpt.get();
        double hunger = data.getHunger();
        double thirst = data.getThirst();
        double energy = data.getEnergy();

        // Header
        ctx.sendMessage(Message.raw("=== Your Survival Stats ===").color(ColorUtil.getHexColor("gold")));

        // Stats with colored indicators
        ctx.sendMessage(Message.raw(String.format("Hunger: %.0f/100 %s",
            hunger, getStatusLabel(hunger, "hunger"))).color(getStatColor(hunger)));
        ctx.sendMessage(Message.raw(String.format("Thirst: %.0f/100 %s",
            thirst, getStatusLabel(thirst, "thirst"))).color(getStatColor(thirst)));
        ctx.sendMessage(Message.raw(String.format("Energy: %.0f/100 %s",
            energy, getStatusLabel(energy, "energy"))).color(getStatColor(energy)));

        // Active buffs
        if (buffsSystem != null) {
            Set<BuffType> activeBuffs = buffsSystem.getActiveBuffs(playerId);
            if (!activeBuffs.isEmpty()) {
                ctx.sendMessage(Message.raw("Active Buffs:").color(ColorUtil.getHexColor("aqua")));
                for (BuffType buff : activeBuffs) {
                    ctx.sendMessage(Message.raw("  + " + formatBuffName(buff))
                        .color(ColorUtil.getHexColor("green")));
                }
            }
        }

        // Warnings for critical stats
        if (hunger < 20) {
            ctx.sendMessage(Message.raw("WARNING: You are starving!")
                .color(ColorUtil.getHexColor("red")));
        }
        if (thirst < 20) {
            ctx.sendMessage(Message.raw("WARNING: You are dehydrated!")
                .color(ColorUtil.getHexColor("red")));
        }
        if (energy < 20) {
            ctx.sendMessage(Message.raw("WARNING: You are exhausted!")
                .color(ColorUtil.getHexColor("red")));
        }
    }

    private String getStatColor(double value) {
        if (value >= 70) return ColorUtil.getHexColor("green");
        if (value >= 40) return ColorUtil.getHexColor("yellow");
        if (value >= 20) return ColorUtil.getHexColor("gold");
        return ColorUtil.getHexColor("red");
    }

    private String getStatusLabel(double value, String type) {
        if (value >= 90) return switch (type) {
            case "hunger" -> "(Satiated)";
            case "thirst" -> "(Hydrated)";
            case "energy" -> "(Energized)";
            default -> "";
        };
        if (value >= 70) return switch (type) {
            case "hunger" -> "(Well Fed)";
            case "thirst" -> "(Quenched)";
            case "energy" -> "(Rested)";
            default -> "";
        };
        if (value >= 50) return switch (type) {
            case "hunger" -> "(Peckish)";
            case "thirst" -> "(Slightly Thirsty)";
            case "energy" -> "(Tired)";
            default -> "";
        };
        if (value >= 30) return switch (type) {
            case "hunger" -> "(Hungry)";
            case "thirst" -> "(Thirsty)";
            case "energy" -> "(Fatigued)";
            default -> "";
        };
        if (value >= 20) return switch (type) {
            case "hunger" -> "(Very Hungry)";
            case "thirst" -> "(Very Thirsty)";
            case "energy" -> "(Very Tired)";
            default -> "";
        };
        if (value > 0) return switch (type) {
            case "hunger" -> "(Starving)";
            case "thirst" -> "(Dehydrated)";
            case "energy" -> "(Exhausted)";
            default -> "";
        };
        return "(Critical!)";
    }

    private String formatBuffName(BuffType buff) {
        return switch (buff) {
            case SPEED -> "Speed Boost";
            case DEFENSE -> "Defense Boost";
            case STAMINA_REGEN -> "Stamina Boost";
            case STRENGTH -> "Strength";
            case VITALITY -> "Vitality";
        };
    }

    private void showHelp(CommandContext ctx) {
        ctx.sendMessage(Message.raw("--- Living Lands Commands ---").color(ColorUtil.getHexColor("gold")));
        ctx.sendMessage(Message.raw("/ll main  - Toggle the stats panel").color(ColorUtil.getHexColor("white")));
        ctx.sendMessage(Message.raw("/ll stats - Show survival stats").color(ColorUtil.getHexColor("white")));
        ctx.sendMessage(Message.raw("/ll help  - Show this help").color(ColorUtil.getHexColor("white")));
    }
}
