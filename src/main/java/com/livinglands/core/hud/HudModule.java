package com.livinglands.core.hud;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.livinglands.api.AbstractModule;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.PlayerSession;
import com.livinglands.core.commands.LivingLandsCommand;
import com.livinglands.modules.leveling.LevelingSystem;
import com.livinglands.modules.leveling.ability.AbilitySystem;
import com.livinglands.modules.metabolism.MetabolismSystem;
import com.livinglands.modules.metabolism.buff.BuffsSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Core HUD module that manages the combined Living Lands HUD.
 * Other modules register their HUD elements with this module.
 *
 * This module should be started before other modules that need HUD access.
 */
public final class HudModule extends AbstractModule {

    public static final String ID = "hud";
    public static final String NAME = "HUD System";
    public static final String VERSION = "1.0.0";

    private static final long UPDATE_INTERVAL_MS = 500;
    private static final long HUD_INIT_DELAY_MS = 3000L;

    private PlayerRegistry playerRegistry;

    // Registered HUD elements from other modules
    private final List<HudElement> elements = new ArrayList<>();

    // Per-player HUD instances
    private final Map<UUID, LivingLandsHud> playerHuds = new ConcurrentHashMap<>();

    // Track when HUD was initialized (for delay)
    private final Map<UUID, Long> hudInitTime = new ConcurrentHashMap<>();

    // Track players being initialized (prevent duplicates)
    private final Set<UUID> initializingPlayers = ConcurrentHashMap.newKeySet();

    // Background update executor
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> updateTask;
    private volatile boolean running = false;

    // Unified panel element
    private LivingLandsPanelElement panelElement;
    private LivingLandsCommand llCommand;

    // References to other module systems (set when modules integrate)
    @Nullable
    private MetabolismSystem metabolismSystem;
    @Nullable
    private LevelingSystem levelingSystem;
    @Nullable
    private AbilitySystem abilitySystem;
    @Nullable
    private BuffsSystem buffsSystem;

    public HudModule() {
        super(ID, NAME, VERSION, Set.of());
    }

    @Override
    protected void onSetup() {
        this.playerRegistry = context.playerRegistry();

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LivingLands-HUD");
            t.setDaemon(true);
            return t;
        });

        // Create unified panel element
        panelElement = new LivingLandsPanelElement();
        panelElement.setHudModule(this);
        registerElement(panelElement);

        // Register /ll command
        llCommand = new LivingLandsCommand();
        llCommand.setPanelElement(panelElement);
        context.commandRegistry().registerCommand(llCommand);

        logger.at(Level.INFO).log("[%s] HUD module setup complete, /ll command registered", name);
    }

    @Override
    protected void onStart() {
        updateTask = executor.scheduleAtFixedRate(
            this::updateAllHuds,
            UPDATE_INTERVAL_MS,
            UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        running = true;
        logger.at(Level.INFO).log("[%s] HUD update loop started (interval: %dms)", name, UPDATE_INTERVAL_MS);
    }

    @Override
    protected void onShutdown() {
        running = false;

        if (updateTask != null) {
            updateTask.cancel(false);
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        playerHuds.clear();
        hudInitTime.clear();
        initializingPlayers.clear();

        logger.at(Level.INFO).log("[%s] HUD module shutdown complete", name);
    }

    /**
     * Register a HUD element from another module.
     * Should be called during module setup phase.
     *
     * @param element The HUD element to register
     */
    public void registerElement(@Nonnull HudElement element) {
        elements.add(element);
        logger.at(Level.INFO).log("[%s] Registered HUD element: %s (%s)",
            name, element.getName(), element.getId());
    }

    /**
     * Initialize HUD for a player when ECS is ready.
     * Called by PlayerRegistry when player's ECS becomes available.
     *
     * @param playerId The player's UUID
     */
    public void initializePlayer(@Nonnull UUID playerId) {
        // Prevent duplicate initialization
        if (playerHuds.containsKey(playerId)) {
            return;
        }
        if (initializingPlayers.contains(playerId)) {
            return;
        }

        var sessionOpt = playerRegistry.getSession(playerId);
        if (sessionOpt.isEmpty()) {
            return;
        }

        initializePlayerHud(playerId, sessionOpt.get());
    }

    private void initializePlayerHud(@Nonnull UUID playerId, @Nonnull PlayerSession session) {
        if (!initializingPlayers.add(playerId)) {
            return; // Already being initialized
        }

        try {
            if (!session.isEcsReady()) {
                initializingPlayers.remove(playerId);
                return;
            }

            var playerRef = session.getPlayerRef();
            if (playerRef == null) {
                initializingPlayers.remove(playerId);
                return;
            }

            var entityRef = session.getEntityRef();
            var store = session.getStore();
            if (entityRef == null || store == null) {
                initializingPlayers.remove(playerId);
                return;
            }

            // Execute on world thread for ECS access
            session.executeOnWorld(() -> {
                try {
                    // Double-check we haven't already initialized
                    if (playerHuds.containsKey(playerId)) {
                        initializingPlayers.remove(playerId);
                        return;
                    }

                    var player = store.getComponent(entityRef, Player.getComponentType());
                    if (player == null) {
                        initializingPlayers.remove(playerId);
                        return;
                    }

                    var hudManager = player.getHudManager();
                    if (hudManager == null) {
                        initializingPlayers.remove(playerId);
                        return;
                    }

                    // Create combined HUD with all registered elements
                    var hud = new LivingLandsHud(playerRef, playerId, new ArrayList<>(elements));

                    // Register with player's HudManager (single slot)
                    hudManager.setCustomHud(playerRef, hud);

                    // Store for updates
                    playerHuds.put(playerId, hud);
                    hudInitTime.put(playerId, System.currentTimeMillis());

                    // Show the HUD
                    hud.show();

                    logger.at(Level.INFO).log("[%s] Initialized HUD for player %s with %d elements",
                        name, playerId, elements.size());

                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log(
                        "[%s] Error initializing HUD on world thread for player %s", name, playerId);
                } finally {
                    initializingPlayers.remove(playerId);
                }
            });

        } catch (Exception e) {
            initializingPlayers.remove(playerId);
            logger.at(Level.WARNING).withCause(e).log(
                "[%s] Error setting up HUD for player %s", name, playerId);
        }
    }

    /**
     * Remove HUD for a player when they disconnect.
     *
     * @param playerId The player's UUID
     */
    public void removePlayer(@Nonnull UUID playerId) {
        playerHuds.remove(playerId);
        hudInitTime.remove(playerId);
        initializingPlayers.remove(playerId);

        // Notify all elements
        for (var element : elements) {
            element.removePlayer(playerId);
        }

        logger.at(Level.FINE).log("[%s] Removed HUD for player %s", name, playerId);
    }

    /**
     * Get the HUD for a player.
     *
     * @param playerId The player's UUID
     * @return The player's HUD, or null if not initialized
     */
    public LivingLandsHud getHud(@Nonnull UUID playerId) {
        return playerHuds.get(playerId);
    }

    /**
     * Check if HUD is ready for updates (past initialization delay).
     *
     * @param playerId The player's UUID
     * @return true if HUD is ready for updates
     */
    public boolean isHudReady(@Nonnull UUID playerId) {
        Long initTime = hudInitTime.get(playerId);
        if (initTime == null) {
            return false;
        }
        return (System.currentTimeMillis() - initTime) >= HUD_INIT_DELAY_MS;
    }

    /**
     * Send immediate update to a player's HUD.
     * Use for time-sensitive updates like XP notifications.
     *
     * @param playerId The player's UUID
     * @param builder The UI command builder with updates
     */
    public void sendImmediateUpdate(@Nonnull UUID playerId, @Nonnull UICommandBuilder builder) {
        if (!isHudReady(playerId)) {
            return;
        }

        var hud = playerHuds.get(playerId);
        if (hud != null) {
            hud.updateImmediate(builder);
        }
    }

    /**
     * Update all player HUDs.
     * Called periodically by the update loop.
     */
    private void updateAllHuds() {
        if (!running) {
            return;
        }

        long now = System.currentTimeMillis();

        for (var entry : playerHuds.entrySet()) {
            var playerId = entry.getKey();
            var hud = entry.getValue();

            try {
                // Check if past initialization delay
                Long initTime = hudInitTime.get(playerId);
                if (initTime == null || (now - initTime) < HUD_INIT_DELAY_MS) {
                    continue;
                }

                // Update all elements
                hud.updateElements();

            } catch (Exception e) {
                logger.at(Level.FINE).withCause(e).log(
                    "[%s] Error updating HUD for player %s", name, playerId);
            }
        }
    }

    /**
     * Get the logger for HUD elements to use.
     */
    public HytaleLogger getLogger() {
        return logger;
    }

    /**
     * Set the metabolism system for the unified panel.
     * Called by MetabolismModule during integration.
     */
    public void setMetabolismSystem(@Nullable MetabolismSystem system) {
        this.metabolismSystem = system;
        if (panelElement != null) {
            panelElement.setMetabolismSystem(system);
        }
        if (llCommand != null) {
            llCommand.setMetabolismSystem(system);
        }
        logger.at(Level.INFO).log("[%s] Metabolism system integrated with panel", name);
    }

    /**
     * Set the buffs system for the /ll stats command.
     * Called by MetabolismModule during integration.
     */
    public void setBuffsSystem(@Nullable BuffsSystem system) {
        this.buffsSystem = system;
        if (llCommand != null) {
            llCommand.setBuffsSystem(system);
        }
        logger.at(Level.INFO).log("[%s] Buffs system integrated with command", name);
    }

    /**
     * Set the leveling system for the unified panel.
     * Called by LevelingModule during integration.
     */
    public void setLevelingSystem(@Nullable LevelingSystem system) {
        this.levelingSystem = system;
        if (panelElement != null) {
            panelElement.setLevelingSystem(system);
        }
        logger.at(Level.INFO).log("[%s] Leveling system integrated with panel", name);
    }

    /**
     * Set the ability system for the unified panel.
     * Called by LevelingModule during integration.
     */
    public void setAbilitySystem(@Nullable AbilitySystem system) {
        this.abilitySystem = system;
        if (panelElement != null) {
            panelElement.setAbilitySystem(system);
        }
        logger.at(Level.INFO).log("[%s] Ability system integrated with panel", name);
    }

    /**
     * Get the unified panel element.
     */
    public LivingLandsPanelElement getPanelElement() {
        return panelElement;
    }
}
