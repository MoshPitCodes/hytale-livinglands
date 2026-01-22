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
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.hud.HudModule;
import com.livinglands.core.hud.LivingLandsPanelElement;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.commands.TrustSubcommand;
import com.livinglands.modules.claims.commands.UnclaimSubcommand;
import com.livinglands.modules.claims.commands.UntrustSubcommand;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Main Living Lands command.
 *
 * Usage:
 *   /ll - Show available commands
 *   /ll main - Toggle the Living Lands stats panel
 *   /ll stats - Toggle metabolism stats (hunger, thirst, energy) on HUD
 *   /ll buffs - Toggle buffs display on HUD
 *   /ll debuffs - Toggle debuffs display on HUD
 *   /ll passives - Toggle passive abilities display on HUD
 *   /ll settings - Open the module settings panel (includes claim UI)
 *   /ll help - Show help panel with mod information
 *   /ll trust <player> - Trust a player in your claim
 *   /ll untrust <player> - Remove trust from a player
 *   /ll unclaim - Remove your claim at current location
 */
public class LivingLandsCommand extends AbstractPlayerCommand {

    @Nullable
    private LivingLandsPanelElement panelElement;

    private final LivingLandsMainSubcommand mainSubcommand;
    private final LivingLandsHelpSubcommand helpSubcommand;
    private final LivingLandsStatsSubcommand statsSubcommand;
    private final LivingLandsBuffsSubcommand buffsSubcommand;
    private final LivingLandsDebuffsSubcommand debuffsSubcommand;
    private final LivingLandsPassivesSubcommand passivesSubcommand;
    private final LivingLandsSettingsSubcommand settingsSubcommand;

    // Claims module subcommands
    private final TrustSubcommand trustSubcommand;
    private final UntrustSubcommand untrustSubcommand;
    private final UnclaimSubcommand unclaimSubcommand;

    // Track if claims module is enabled
    private boolean claimsEnabled = false;

    public LivingLandsCommand() {
        super("ll", "Living Lands main command", false);
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());

        // Create and register core subcommands
        this.mainSubcommand = new LivingLandsMainSubcommand(this);
        this.helpSubcommand = new LivingLandsHelpSubcommand();
        this.statsSubcommand = new LivingLandsStatsSubcommand();
        this.buffsSubcommand = new LivingLandsBuffsSubcommand();
        this.debuffsSubcommand = new LivingLandsDebuffsSubcommand();
        this.passivesSubcommand = new LivingLandsPassivesSubcommand();
        this.settingsSubcommand = new LivingLandsSettingsSubcommand();

        addSubCommand(mainSubcommand);
        addSubCommand(helpSubcommand);
        addSubCommand(statsSubcommand);
        addSubCommand(buffsSubcommand);
        addSubCommand(debuffsSubcommand);
        addSubCommand(passivesSubcommand);
        addSubCommand(settingsSubcommand);

        // Create claims subcommands (registered but not functional until configured)
        // Note: /ll claim is removed - claiming is now done via /ll settings UI
        this.trustSubcommand = new TrustSubcommand();
        this.untrustSubcommand = new UntrustSubcommand();
        this.unclaimSubcommand = new UnclaimSubcommand();

        addSubCommand(trustSubcommand);
        addSubCommand(untrustSubcommand);
        addSubCommand(unclaimSubcommand);
    }

    public void setPanelElement(@Nullable LivingLandsPanelElement panelElement) {
        this.panelElement = panelElement;
        this.mainSubcommand.setPanelElement(panelElement);
    }

    public void setHudModule(@Nullable HudModule hudModule) {
        this.statsSubcommand.setHudModule(hudModule);
        this.buffsSubcommand.setHudModule(hudModule);
        this.debuffsSubcommand.setHudModule(hudModule);
        this.passivesSubcommand.setHudModule(hudModule);
    }

    /**
     * Sets the module manager for the settings subcommand.
     * Called by HudModule during setup.
     */
    public void setModuleManager(@Nullable com.livinglands.core.ModuleManager moduleManager,
                                 @Nullable com.hypixel.hytale.logger.HytaleLogger logger,
                                 @Nullable com.livinglands.core.PlayerRegistry playerRegistry) {
        this.settingsSubcommand.setModuleManager(moduleManager);
        this.settingsSubcommand.setLogger(logger);
        this.settingsSubcommand.setPlayerRegistry(playerRegistry);
    }

    /**
     * Configures the claims subcommands with the claims system.
     * Called by ClaimsModule during setup.
     */
    public void setClaimsModule(@Nullable ClaimSystem claimSystem,
                                @Nullable ClaimsModuleConfig config,
                                @Nullable PlayerRegistry playerRegistry) {
        this.claimsEnabled = claimSystem != null && config != null;

        trustSubcommand.setClaimSystem(claimSystem);
        trustSubcommand.setConfig(config);
        trustSubcommand.setPlayerRegistry(playerRegistry);

        untrustSubcommand.setClaimSystem(claimSystem);
        untrustSubcommand.setPlayerRegistry(playerRegistry);

        unclaimSubcommand.setClaimSystem(claimSystem);
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
        ctx.sendMessage(Message.raw("/ll settings").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Open the module settings panel").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll help").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Show help and configuration info").color(ColorUtil.getHexColor("gray"))));

        // HUD toggles
        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));
        ctx.sendMessage(Message.raw("HUD Toggles:").color(ColorUtil.getHexColor("yellow")));
        ctx.sendMessage(Message.raw("/ll stats").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Toggle stats (hunger/thirst/energy) on HUD").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll buffs").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Toggle buffs display on HUD").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll debuffs").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Toggle debuffs display on HUD").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll passives").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Toggle passive abilities on HUD").color(ColorUtil.getHexColor("gray"))));

        // Claims commands (only show if module is enabled)
        if (claimsEnabled) {
            ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));
            ctx.sendMessage(Message.raw("Claims:").color(ColorUtil.getHexColor("green")));
            ctx.sendMessage(Message.raw("/ll unclaim").color(ColorUtil.getHexColor("aqua"))
                .insert(Message.raw(" - Remove claim at your location").color(ColorUtil.getHexColor("gray"))));
            ctx.sendMessage(Message.raw("/ll trust <player>").color(ColorUtil.getHexColor("aqua"))
                .insert(Message.raw(" - Trust a player in your claim").color(ColorUtil.getHexColor("gray"))));
            ctx.sendMessage(Message.raw("/ll untrust <player>").color(ColorUtil.getHexColor("aqua"))
                .insert(Message.raw(" - Remove trust from a player").color(ColorUtil.getHexColor("gray"))));
            ctx.sendMessage(Message.raw("Use /ll settings").color(ColorUtil.getHexColor("yellow"))
                .insert(Message.raw(" to claim land via the Claims tab").color(ColorUtil.getHexColor("gray"))));
        }

        ctx.sendMessage(Message.raw("").color(ColorUtil.getHexColor("white")));
        ctx.sendMessage(Message.raw("Admin Commands:").color(ColorUtil.getHexColor("red")));
        ctx.sendMessage(Message.raw("/setlevel <profession> <level>").color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Set profession level (OP required)").color(ColorUtil.getHexColor("gray"))));
    }
}
