package com.livinglands.modules.claims.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.data.PlotClaim;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Subcommand: /ll untrust
 *
 * Removes trust from a player in the current claim.
 */
public class UntrustSubcommand extends AbstractPlayerCommand {

    @Nullable
    private ClaimSystem claimSystem;

    @Nullable
    private PlayerRegistry playerRegistry;

    private final RequiredArg<String> playerArg;

    public UntrustSubcommand() {
        super("untrust", "Remove trust from a player", false);
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());

        this.playerArg = withRequiredArg("player", "<player>", ArgTypes.STRING);
    }

    public void setClaimSystem(@Nullable ClaimSystem claimSystem) {
        this.claimSystem = claimSystem;
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

        if (claimSystem == null) {
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

        String targetName = ctx.get(playerArg);
        if (targetName == null || targetName.isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /ll untrust <player>")
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        // Find target player by name
        UUID targetId = findPlayerByName(targetName);

        // Also check existing trust entries for offline players
        if (targetId == null) {
            targetId = findTrustedByName(claim, targetName);
        }

        if (targetId == null) {
            ctx.sendMessage(Message.raw("Player not found: " + targetName)
                .color(ColorUtil.getHexColor("red")));
            return;
        }

        // Check if player is actually trusted
        if (claim.getPermission(targetId) == null) {
            ctx.sendMessage(Message.raw(targetName + " is not trusted in this claim.")
                .color(ColorUtil.getHexColor("yellow")));
            return;
        }

        // Remove trust
        if (claimSystem.untrustPlayer(claim.getId(), targetId)) {
            ctx.sendMessage(Message.raw("Removed trust from " + targetName + ".")
                .color(ColorUtil.getHexColor("green")));
        } else {
            ctx.sendMessage(Message.raw("Failed to remove trust.")
                .color(ColorUtil.getHexColor("red")));
        }
    }

    @Nullable
    private UUID findPlayerByName(String name) {
        if (playerRegistry == null) {
            return null;
        }

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

    @Nullable
    private UUID findTrustedByName(PlotClaim claim, String name) {
        // This is a simplified lookup - in a real implementation you'd want
        // to store player names with UUIDs for offline lookup
        return null;
    }
}
