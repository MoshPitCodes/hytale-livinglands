package com.livinglands.modules.claims.map;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapLoadException;
import com.hypixel.hytale.server.core.universe.world.worldmap.provider.IWorldMapProvider;
import com.livinglands.modules.claims.ClaimSystem;
import com.livinglands.modules.claims.config.ClaimsModuleConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Custom world map provider that renders terrain with claim coloring.
 *
 * <p>This provider creates map images that show both the terrain and
 * semi-transparent claim ownership overlays, similar to SimpleClaims.</p>
 *
 * <p>The provider accesses the ClaimSystem and ClaimsModuleConfig through
 * static holders that are set by the ClaimsModule during initialization.</p>
 *
 * <p>Registration: This provider is registered via the mod's content system
 * and replaces the default world map rendering for claimed chunks.</p>
 */
public class ClaimsWorldMapProvider implements IWorldMapProvider {

    private static final Logger LOGGER = Logger.getLogger("com.livinglands.modules.claims.map");

    public static final String ID = "livinglands:claims_worldmap";

    /**
     * Codec for serialization (required by IWorldMapProvider).
     */
    public static final BuilderCodec<ClaimsWorldMapProvider> CODEC = BuilderCodec.builder(
            ClaimsWorldMapProvider.class,
            ClaimsWorldMapProvider::new
    ).build();

    /**
     * Static holder for the claim system.
     * Set by ClaimsModule during initialization.
     */
    private static volatile ClaimSystem claimSystemHolder = null;

    /**
     * Static holder for the claims config.
     * Set by ClaimsModule during initialization.
     */
    private static volatile ClaimsModuleConfig configHolder = null;

    /**
     * Registers the claims world map provider codec with the Hytale API.
     * This must be called during module setup before any worlds are loaded.
     */
    public static void registerCodec() {
        LOGGER.log(Level.FINE, "[ClaimsWorldMapProvider] Registering codec with ID: {0}", ID);
        IWorldMapProvider.CODEC.register(ID, ClaimsWorldMapProvider.class, CODEC);
        LOGGER.log(Level.FINE, "[ClaimsWorldMapProvider] Codec registered successfully");
    }

    /**
     * Sets the claim system and config for all provider instances.
     * This should be called by ClaimsModule during setup.
     *
     * @param claimSystem The claim system
     * @param config      The claims config
     */
    public static void setClaimSystemAndConfig(@Nullable ClaimSystem claimSystem,
                                                @Nullable ClaimsModuleConfig config) {
        claimSystemHolder = claimSystem;
        configHolder = config;
    }

    /**
     * Gets the claim system holder (for access by image builders).
     */
    @Nullable
    public static ClaimSystem getClaimSystem() {
        return claimSystemHolder;
    }

    /**
     * Default constructor required for codec.
     */
    public ClaimsWorldMapProvider() {
        // Default constructor required for codec
    }

    @Override
    @Nonnull
    public IWorldMap getGenerator(@Nonnull World world) throws WorldMapLoadException {
        LOGGER.log(Level.FINE, "[ClaimsWorldMapProvider] getGenerator called for world: {0}", world.getName());
        // Create a new ClaimsWorldMap instance with the current claim system and config
        return new ClaimsWorldMap(claimSystemHolder, configHolder);
    }
}
