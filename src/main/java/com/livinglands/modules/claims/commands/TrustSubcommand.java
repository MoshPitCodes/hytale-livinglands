package com.livinglands.modules.claims.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import com.livinglands.modules.claims.data.ClaimPermission;
import com.livinglands.modules.claims.data.PlotClaim;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Subcommand: /ll trust
 *
 * Handles trust-related commands:
 * - /ll trust <player> - Grant full trust in current claim
 * - /ll trust access <player> - Grant container access only
 * - /ll untrust <player> - Remove trust
 * - /ll trustlist - List trusted players in current claim
 */
public class TrustSubcommand extends AbstractPlayerCommand {

    @Nullable
    private ClaimSystem claimSystem;

    @Nullable
    private ClaimsModuleConfig config;

    @Nullable
    private PlayerRegistry playerRegistry;

    private final OptionalArg<String> argsArg;

    public TrustSubcommand() {
        super("trust", "Manage trusted players in your claims", false);
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());

        // Add argument for subcommand or player name
        this.argsArg = withOptionalArg("args", "<player>|access <player>|list", ArgTypes.STRING);
    }

    public void setClaimSystem(@Nullable ClaimSystem claimSystem) {
        this.claimSystem = claimSystem;
    }

    public void setConfig(@Nullable ClaimsModuleConfig config) {
        this.config = config;
    }

    public void setPlayerRegistry(@Nullable PlayerRegistry playerRegistry) {
        this.playerRegistry = playerRegistry;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        if (claimSystem == null || config == null) {
            ctx.sendMessage(Message.raw("Claims system is not available.")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        var playerId = playerRef.getUuid();

        // Get player transform for position
        Transform transform = playerRef.getTransform();
        if (transform == null) {
            ctx.sendMessage(Message.raw("Unable to determine player position.")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        var position = transform.getPosition();
        int blockX = (int) Math.floor(position.getX());
        int blockZ = (int) Math.floor(position.getZ());
        int chunkX = blockX >> PlotClaim.CHUNK_SHIFT;
        int chunkZ = blockZ >> PlotClaim.CHUNK_SHIFT;
        String worldId = world.getName();

        // Get claim at location
        PlotClaim claim = claimSystem.getClaimAt(worldId, chunkX, chunkZ);

        // Parse arguments
        String args = ctx.get(argsArg);

        if (args == null || args.isEmpty()) {
            showUsage(ctx);
            return;
        }

        String[] parts = args.trim().split("\\s+", 2);
        String firstArg = parts[0].toLowerCase();

        switch (firstArg) {
            case "list" -> handleTrustList(ctx, playerId, claim);
            case "access" -> {
                if (parts.length < 2) {
                    ctx.sendMessage(Message.raw("Usage: /ll trust access <player>")
                        .color(ColorUtil.getHexColor("red")));
                } else {
                    handleTrust(ctx, playerId, claim, parts[1], ClaimPermission.ACCESSOR);
                }
            }
            default -> {
                // Assume it's a player name for full trust
                handleTrust(ctx, playerId, claim, firstArg, ClaimPermission.TRUSTED);
            }
        }
    }

    private void handleTrust(CommandContext ctx, UUID playerId, PlotClaim claim,
                             String targetName, ClaimPermission permission) {
        if (claim == null) {
            ctx.sendMessage(Message.raw("You are not standing in a claim.")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        // Check if player can modify this claim
        if (!claimSystem.canModifyClaim(playerId, claim)) {
            ctx.sendMessage(Message.raw("You don't have permission to modify this claim.")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        // Find target player by name
        UUID targetId = findPlayerByName(targetName);
        if (targetId == null) {
            ctx.sendMessage(Message.raw("Player not found: " + targetName)
                .color(ColorUtil.getHexColor("red")));
            ctx.sendMessage(Message.raw("Note: Player must be online.")
                .color(ColorUtil.getHexColor("gray")));
            return;
        }

        // Don't allow trusting yourself
        if (targetId.equals(claim.getOwner())) {
            ctx.sendMessage(Message.raw("You cannot trust the claim owner.")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        // Check trust limit
        if (claim.getPermissions().size() >= config.maxTrustedPerPlot &&
            !claim.getPermissions().containsKey(targetId)) {
            ctx.sendMessage(Message.raw(String.format("Trust limit reached (%d/%d players).",
                claim.getPermissions().size(), config.maxTrustedPerPlot))
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        // Trust the player
        if (claimSystem.trustPlayer(claim.getId(), targetId, permission)) {
            String permType = permission == ClaimPermission.TRUSTED ? "full trust" : "container access";
            ctx.sendMessage(Message.raw(String.format("Granted %s to %s.", permType, targetName))
                .color(ColorUtil.getHexColor("green")));
        } else {
            ctx.sendMessage(Message.raw("Failed to add trust.")
                .color(ColorUtil.getHexColor("red")));
        }
    }

    private void handleTrustList(CommandContext ctx, UUID playerId, PlotClaim claim) {
        if (claim == null) {
            ctx.sendMessage(Message.raw("You are not standing in a claim.")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        ctx.sendMessage(Message.raw("=== Trusted Players ===")
            .color(ColorUtil.getHexColor("gold")));
        ctx.sendMessage(Message.raw("Owner: " + claim.getOwnerName())
            .color(ColorUtil.getHexColor("aqua")));

        var perms = claim.getPermissions();
        if (perms.isEmpty()) {
            ctx.sendMessage(Message.raw("No trusted players.")
                .color(ColorUtil.getHexColor("gray")));
            return;
        }

        ctx.sendMessage(Message.raw(String.format("Trusted (%d/%d):", perms.size(), config.maxTrustedPerPlot))
            .color(ColorUtil.getHexColor("white")));

        for (var entry : perms.entrySet()) {
            String playerName = getPlayerName(entry.getKey());
            String permLevel = entry.getValue() == ClaimPermission.TRUSTED ? "Full" : "Access";
            ctx.sendMessage(Message.raw(String.format("- %s [%s]", playerName, permLevel))
                .color(ColorUtil.getHexColor("gray")));
        }
    }

    @Nullable
    private UUID findPlayerByName(String name) {
        if (playerRegistry == null) {
            return null;
        }

        // Look through online players
        for (var session : playerRegistry.getAllSessions()) {
            var player = session.getPlayer();
            if (player != null) {
                var playerRef = player.getPlayerRef();
                if (playerRef != null && playerRef.getUsername().equalsIgnoreCase(name)) {
                    return session.getPlayerId();
                }
            }
        }
        return null;
    }

    private String getPlayerName(UUID playerId) {
        if (playerRegistry == null) {
            return playerId.toString().substring(0, 8);
        }

        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isPresent()) {
            var player = sessionOpt.get().getPlayer();
            if (player != null) {
                var playerRef = player.getPlayerRef();
                if (playerRef != null) {
                    return playerRef.getUsername();
                }
            }
        }
        return playerId.toString().substring(0, 8);
    }

    private void showUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Trust Commands ===")
            .color(ColorUtil.getHexColor("gold")));
        ctx.sendMessage(Message.raw("/ll trust <player>")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Grant full trust").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll trust access <player>")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Grant container access only").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll trust list")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - List trusted players").color(ColorUtil.getHexColor("gray"))));
    }
}
