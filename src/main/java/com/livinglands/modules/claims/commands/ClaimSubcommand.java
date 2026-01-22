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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;
import com.livinglands.modules.claims.data.PlotClaim;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Subcommand: /ll claim
 *
 * Handles all claim-related commands:
 * - /ll claim [WxL] - Preview a claim (default 1x1)
 * - /ll claim confirm - Confirm pending preview
 * - /ll claim cancel - Cancel pending preview
 * - /ll claim info - Show info about claim at current location
 * - /ll claim list - List all owned claims
 * - /ll claim admin [WxL] - Create admin claim
 * - /ll claim admin remove - Force remove claim at location
 * - /ll claim admin bypass - Toggle admin bypass mode
 */
public class ClaimSubcommand extends AbstractPlayerCommand {

    private static final Pattern DIMENSION_PATTERN = Pattern.compile("^(\\d+)x(\\d+)$", Pattern.CASE_INSENSITIVE);

    @Nullable
    private ClaimSystem claimSystem;

    @Nullable
    private ClaimsModuleConfig config;

    private final OptionalArg<String> argsArg;

    public ClaimSubcommand() {
        super("claim", "Create and manage land claims", false);
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());

        // Add optional argument for dimensions or subcommand
        // Use "--args" prefix to make it a proper optional arg
        this.argsArg = withOptionalArg("args", "confirm|cancel|info|list|admin|WxL", ArgTypes.STRING);

        // Allow extra arguments to be passed (for dimension parsing like "3x3")
        setAllowsExtraArguments(true);
    }

    public void setClaimSystem(@Nullable ClaimSystem claimSystem) {
        this.claimSystem = claimSystem;
    }

    public void setConfig(@Nullable ClaimsModuleConfig config) {
        this.config = config;
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

        // Get player name from PlayerRef
        String playerName = playerRef.getUsername();

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

        // Parse arguments - try optional arg first, then fall back to input string
        String args = ctx.get(argsArg);

        // If optional arg is not set, try to get the raw input string
        // This handles cases like "/ll claim 3x3" where "3x3" is passed as extra input
        if (args == null || args.isEmpty()) {
            String inputString = ctx.getInputString();
            if (inputString != null && !inputString.isEmpty()) {
                // The input string contains the full command: "ll claim 3x3"
                // We need to strip "ll " and "claim " prefixes
                args = inputString.trim();

                // Remove "ll " prefix if present
                if (args.toLowerCase().startsWith("ll ")) {
                    args = args.substring(3).trim();
                }

                // Remove "claim " prefix if present (with space)
                if (args.toLowerCase().startsWith("claim ")) {
                    args = args.substring(6).trim();
                }
                // If input is just "claim" (the subcommand name itself), treat as empty
                else if (args.equalsIgnoreCase("claim")) {
                    args = "";
                }
            }
        }

        if (args == null || args.isEmpty()) {
            // Default: /ll claim = open UI selector
            claimSystem.openClaimSelector(playerRef, ref, store, worldId, chunkX, chunkZ);
            return;
        }

        String lowerArgs = args.toLowerCase();

        switch (lowerArgs) {
            case "confirm" -> handleConfirm(ctx, playerId, playerName);
            case "cancel" -> handleCancel(ctx, playerId);
            case "info" -> handleInfo(ctx, playerId, worldId, chunkX, chunkZ);
            case "list" -> handleList(ctx, playerId);
            case "admin" -> handleAdminHelp(ctx);
            default -> {
                // Check if it's a dimension pattern
                var matcher = DIMENSION_PATTERN.matcher(args);
                if (matcher.matches()) {
                    int width = Integer.parseInt(matcher.group(1));
                    int length = Integer.parseInt(matcher.group(2));
                    handlePreview(ctx, playerId, playerName, worldId, chunkX, chunkZ, width, length, false);
                } else if (lowerArgs.startsWith("admin ")) {
                    handleAdminCommand(ctx, playerId, playerName, worldId, chunkX, chunkZ, args.substring(6));
                } else {
                    showUsage(ctx);
                }
            }
        }
    }

    private void handlePreview(CommandContext ctx, UUID playerId, String playerName,
                               String worldId, int chunkX, int chunkZ,
                               int width, int length, boolean isAdmin) {
        // Check for existing preview
        if (claimSystem.hasPendingPreview(playerId)) {
            ctx.sendMessage(Message.raw("You already have a pending claim preview.")
                .color(ColorUtil.getHexColor("yellow")));
            ctx.sendMessage(Message.raw("Use '/ll claim confirm' to create or '/ll claim cancel' to cancel.")
                .color(ColorUtil.getHexColor("gray")));
            return;
        }

        // Create preview
        String error = claimSystem.createPreview(playerId, playerName, worldId, chunkX, chunkZ, width, length, isAdmin);
        if (error != null) {
            ctx.sendMessage(Message.raw("Cannot create claim: " + error)
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        var preview = claimSystem.getPreview(playerId);
        if (preview == null) {
            ctx.sendMessage(Message.raw("Failed to create preview.")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        // Show preview information
        String typeLabel = isAdmin ? " (Admin)" : "";
        ctx.sendMessage(Message.raw(String.format("Preview: %dx%d claim%s (%d chunks)",
            width, length, typeLabel, preview.getTotalChunks()))
            .color(ColorUtil.getHexColor("yellow")));
        ctx.sendMessage(Message.raw(String.format("Chunks: [%d, %d] to [%d, %d]",
            preview.startChunkX(), preview.startChunkZ(), preview.endChunkX(), preview.endChunkZ()))
            .color(ColorUtil.getHexColor("gray")));
        ctx.sendMessage(Message.raw(String.format("Blocks: [%d, %d] to [%d, %d]",
            preview.startChunkX() * PlotClaim.CHUNK_SIZE, preview.startChunkZ() * PlotClaim.CHUNK_SIZE,
            (preview.endChunkX() + 1) * PlotClaim.CHUNK_SIZE - 1, (preview.endChunkZ() + 1) * PlotClaim.CHUNK_SIZE - 1))
            .color(ColorUtil.getHexColor("gray")));
        ctx.sendMessage(Message.raw("")
            .color(ColorUtil.getHexColor("white")));
        ctx.sendMessage(Message.raw("Use '/ll claim confirm' to create or '/ll claim cancel' to cancel.")
            .color(ColorUtil.getHexColor("aqua")));
        ctx.sendMessage(Message.raw(String.format("Preview expires in %d seconds.", config.previewTimeoutSeconds))
            .color(ColorUtil.getHexColor("gray")));
    }

    private void handleConfirm(CommandContext ctx, UUID playerId, String playerName) {
        var result = claimSystem.confirmPreview(playerId, playerName);

        if (!result.success()) {
            ctx.sendMessage(Message.raw("Failed to create claim: " + result.message())
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        var claim = result.claim();
        if (claim == null) {
            ctx.sendMessage(Message.raw("Claim created successfully!")
                .color(ColorUtil.getHexColor("green")));
            return;
        }

        int currentPlots = claimSystem.getPlayerClaimCount(playerId);
        int maxPlots = claimSystem.getMaxPlots(playerId);

        ctx.sendMessage(Message.raw(String.format("Claim created! %dx%d (%d chunks)",
            claim.getWidthChunks(), claim.getLengthChunks(), claim.getTotalChunks()))
            .color(ColorUtil.getHexColor("green")));
        ctx.sendMessage(Message.raw(String.format("You now have %d/%d plots.", currentPlots, maxPlots))
            .color(ColorUtil.getHexColor("gray")));
    }

    private void handleCancel(CommandContext ctx, UUID playerId) {
        if (claimSystem.cancelPreview(playerId)) {
            ctx.sendMessage(Message.raw("Claim preview cancelled.")
                .color(ColorUtil.getHexColor("yellow")));
        } else {
            ctx.sendMessage(Message.raw("No pending preview to cancel.")
                .color(ColorUtil.getHexColor("gray")));
        }
    }

    private void handleInfo(CommandContext ctx, UUID playerId, String worldId, int chunkX, int chunkZ) {
        PlotClaim claim = claimSystem.getClaimAt(worldId, chunkX, chunkZ);

        if (claim == null) {
            ctx.sendMessage(Message.raw("This chunk is not claimed.")
                .color(ColorUtil.getHexColor("gray")));
            ctx.sendMessage(Message.raw(String.format("Current chunk: [%d, %d]", chunkX, chunkZ))
                .color(ColorUtil.getHexColor("gray")));
            return;
        }

        // Show claim info
        ctx.sendMessage(Message.raw("=== Claim Info ===")
            .color(ColorUtil.getHexColor("gold")));
        ctx.sendMessage(Message.raw("Owner: " + claim.getOwnerName())
            .color(claim.isOwner(playerId) ? ColorUtil.getHexColor("green") : ColorUtil.getHexColor("white")));

        if (claim.isAdminClaim()) {
            ctx.sendMessage(Message.raw("[Admin Claim]")
                .color(ColorUtil.getHexColor("red")));
        }

        ctx.sendMessage(Message.raw(String.format("Size: %dx%d (%d chunks)",
            claim.getWidthChunks(), claim.getLengthChunks(), claim.getTotalChunks()))
            .color(ColorUtil.getHexColor("gray")));
        ctx.sendMessage(Message.raw(String.format("Chunks: [%d, %d] to [%d, %d]",
            claim.getStartChunkX(), claim.getStartChunkZ(), claim.getEndChunkX(), claim.getEndChunkZ()))
            .color(ColorUtil.getHexColor("gray")));

        // Show permissions
        var perms = claim.getPermissions();
        if (!perms.isEmpty()) {
            ctx.sendMessage(Message.raw(String.format("Trusted: %d players", perms.size()))
                .color(ColorUtil.getHexColor("aqua")));
        }

        // Show your permission level
        if (!claim.isOwner(playerId)) {
            var yourPerm = claim.getPermission(playerId);
            if (yourPerm != null) {
                ctx.sendMessage(Message.raw("Your access: " + yourPerm.name())
                    .color(ColorUtil.getHexColor("green")));
            } else {
                ctx.sendMessage(Message.raw("Your access: None")
                    .color(ColorUtil.getHexColor("red")));
            }
        }
    }

    private void handleList(CommandContext ctx, UUID playerId) {
        var claims = claimSystem.getClaimsByOwner(playerId);
        int maxPlots = claimSystem.getMaxPlots(playerId);

        ctx.sendMessage(Message.raw(String.format("=== Your Claims (%d/%d) ===", claims.size(), maxPlots))
            .color(ColorUtil.getHexColor("gold")));

        if (claims.isEmpty()) {
            ctx.sendMessage(Message.raw("You have no claims.")
                .color(ColorUtil.getHexColor("gray")));
            ctx.sendMessage(Message.raw("Use '/ll claim [WxL]' to create one.")
                .color(ColorUtil.getHexColor("aqua")));
            return;
        }

        int totalChunks = 0;
        for (PlotClaim claim : claims) {
            String adminTag = claim.isAdminClaim() ? " [Admin]" : "";
            ctx.sendMessage(Message.raw(String.format("- %dx%d at [%d, %d]%s",
                claim.getWidthChunks(), claim.getLengthChunks(),
                claim.getStartChunkX(), claim.getStartChunkZ(), adminTag))
                .color(ColorUtil.getHexColor("gray")));
            totalChunks += claim.getTotalChunks();
        }

        ctx.sendMessage(Message.raw(String.format("Total: %d chunks (max %d)",
            totalChunks, config.maxTotalChunksPerPlayer))
            .color(ColorUtil.getHexColor("aqua")));
    }

    private void handleAdminHelp(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Admin Claim Commands ===")
            .color(ColorUtil.getHexColor("red")));
        ctx.sendMessage(Message.raw("/ll claim admin [WxL]")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Create admin claim").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll claim admin remove")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Remove claim at location").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll claim admin bypass")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Toggle bypass mode").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll claim admin list <player>")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - List player's claims").color(ColorUtil.getHexColor("gray"))));
    }

    private void handleAdminCommand(CommandContext ctx, UUID playerId, String playerName,
                                    String worldId, int chunkX, int chunkZ, String subArgs) {
        // Check if player is admin
        if (!claimSystem.isAdmin(playerId)) {
            ctx.sendMessage(Message.raw("You don't have permission to use admin commands.")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        String[] parts = subArgs.trim().split("\\s+", 2);
        String subCommand = parts[0].toLowerCase();

        switch (subCommand) {
            case "remove" -> handleAdminRemove(ctx, playerId, worldId, chunkX, chunkZ);
            case "bypass" -> handleAdminBypass(ctx, playerId);
            case "list" -> {
                if (parts.length < 2) {
                    ctx.sendMessage(Message.raw("Usage: /ll claim admin list <player>")
                        .color(ColorUtil.getHexColor("red")));
                } else {
                    // Would need player lookup - simplified for now
                    ctx.sendMessage(Message.raw("Player lookup not yet implemented.")
                        .color(ColorUtil.getHexColor("yellow")));
                }
            }
            default -> {
                // Check if it's a dimension pattern for admin claim
                var matcher = DIMENSION_PATTERN.matcher(subCommand);
                if (matcher.matches()) {
                    int width = Integer.parseInt(matcher.group(1));
                    int length = Integer.parseInt(matcher.group(2));
                    handlePreview(ctx, playerId, playerName, worldId, chunkX, chunkZ, width, length, true);
                } else if (subCommand.isEmpty()) {
                    // Default admin claim 1x1
                    handlePreview(ctx, playerId, playerName, worldId, chunkX, chunkZ, 1, 1, true);
                } else {
                    handleAdminHelp(ctx);
                }
            }
        }
    }

    private void handleAdminRemove(CommandContext ctx, UUID playerId, String worldId, int chunkX, int chunkZ) {
        PlotClaim claim = claimSystem.getClaimAt(worldId, chunkX, chunkZ);

        if (claim == null) {
            ctx.sendMessage(Message.raw("No claim at this location.")
                .color(ColorUtil.getHexColor("yellow")));
            return;
        }

        if (claimSystem.deleteClaim(claim.getId())) {
            ctx.sendMessage(Message.raw(String.format("Removed claim owned by %s.", claim.getOwnerName()))
                .color(ColorUtil.getHexColor("green")));
        } else {
            ctx.sendMessage(Message.raw("Failed to remove claim.")
                .color(ColorUtil.getHexColor("red")));
        }
    }

    private void handleAdminBypass(CommandContext ctx, UUID playerId) {
        boolean enabled = claimSystem.toggleAdminBypass(playerId);
        if (enabled) {
            ctx.sendMessage(Message.raw("Admin bypass ENABLED - you can now modify protected claims.")
                .color(ColorUtil.getHexColor("green")));
        } else {
            ctx.sendMessage(Message.raw("Admin bypass DISABLED - normal protection rules apply.")
                .color(ColorUtil.getHexColor("yellow")));
        }
    }

    private void showUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Claim Commands ===")
            .color(ColorUtil.getHexColor("gold")));
        ctx.sendMessage(Message.raw("/ll claim [WxL]")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Preview a claim (e.g., 3x3)").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll claim confirm")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Confirm pending claim").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll claim cancel")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - Cancel pending claim").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll claim info")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - View claim at location").color(ColorUtil.getHexColor("gray"))));
        ctx.sendMessage(Message.raw("/ll claim list")
            .color(ColorUtil.getHexColor("aqua"))
            .insert(Message.raw(" - List your claims").color(ColorUtil.getHexColor("gray"))));
    }
}
