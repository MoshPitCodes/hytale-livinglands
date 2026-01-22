package com.livinglands.modules.claims.data;

/**
 * Configuration flags for individual claims.
 * Controls various behaviors within the claim.
 */
public class ClaimFlags {
    /**
     * Whether PvP is enabled within the claim.
     */
    public boolean pvp = false;

    /**
     * Whether explosions can damage blocks within the claim.
     */
    public boolean explosions = false;

    /**
     * Whether mobs can grief (e.g., endermen, creepers) within the claim.
     */
    public boolean mobGriefing = false;

    /**
     * Whether hostile NPCs should be despawned when entering the claim.
     * When enabled, hostile NPCs are immediately removed from the claim.
     */
    public boolean hostileNpcProtection = true;

    public ClaimFlags() {}

    /**
     * Create a copy of these flags.
     */
    public ClaimFlags copy() {
        ClaimFlags copy = new ClaimFlags();
        copy.pvp = this.pvp;
        copy.explosions = this.explosions;
        copy.mobGriefing = this.mobGriefing;
        copy.hostileNpcProtection = this.hostileNpcProtection;
        return copy;
    }
}
