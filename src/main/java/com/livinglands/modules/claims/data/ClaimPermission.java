package com.livinglands.modules.claims.data;

/**
 * Permission levels for trusted players in a claim.
 */
public enum ClaimPermission {
    /**
     * Full trust - can build, break, interact, and access containers.
     */
    TRUSTED,

    /**
     * Limited trust - can only access containers.
     */
    ACCESSOR
}
