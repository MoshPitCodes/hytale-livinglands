package com.livinglands.modules.claims.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.data.PlotClaim;
import com.livinglands.util.ColorUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Subcommand: /ll unclaim all
 *
 * Removes ALL claims owned by the player.
 */
public class UnclaimSubcommand extends AbstractPlayerCommand {

    @Nullable
    private ClaimSystem claimSystem;

    public UnclaimSubcommand() {
        super("unclaimall", "Remove all your claims", false);
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString());
    }

    public void setClaimSystem(@Nullable ClaimSystem claimSystem) {
        this.claimSystem = claimSystem;
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

        UUID playerId = playerRef.getUuid();

        // Get all claims owned by the player
        List<PlotClaim> playerClaims = claimSystem.getClaimsByOwner(playerId);

        if (playerClaims.isEmpty()) {
            ctx.sendMessage(Message.raw("You have no claims to remove.")
                .color(ColorUtil.getHexColor("yellow")));
            return;
        }

        int totalClaims = playerClaims.size();
        int totalChunks = 0;
        int successfulDeletes = 0;

        // Delete all claims
        for (PlotClaim claim : playerClaims) {
            int chunkCount = claim.getTotalChunks();
            if (claimSystem.deleteClaim(claim.getId())) {
                totalChunks += chunkCount;
                successfulDeletes++;
            }
        }

        if (successfulDeletes > 0) {
            ctx.sendMessage(Message.raw(String.format("Removed %d claim(s) (%d chunks freed).",
                    successfulDeletes, totalChunks))
                .color(ColorUtil.getHexColor("green")));
        }

        if (successfulDeletes < totalClaims) {
            ctx.sendMessage(Message.raw(String.format("Failed to remove %d claim(s).",
                    totalClaims - successfulDeletes))
                .color(ColorUtil.getHexColor("red")));
        }
    }
}
