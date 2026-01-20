# Living Lands - Technical Design Document

This document provides a deep technical dive into how the Living Lands metabolism mod works internally. For user-facing documentation, see the [README](../README.md).

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Module System](#module-system)
3. [Metabolism Core](#metabolism-core)
4. [Thread Safety & ECS Access](#thread-safety--ecs-access)
5. [Buff & Debuff Systems](#buff--debuff-systems)
6. [Speed Modification](#speed-modification)
7. [Food & Potion Detection](#food--potion-detection)
8. [Poison System](#poison-system)
9. [Native Effect Integration](#native-effect-integration)
10. [Data Persistence](#data-persistence)
11. [Configuration System](#configuration-system)

---

## Architecture Overview

Living Lands is built on a modular plugin architecture with three layers:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        LivingLandsPlugin                             │
│  Entry point, module registration, lifecycle management              │
├─────────────────────────────────────────────────────────────────────┤
│                          ModuleManager                               │
│  Topological dependency sorting, setup/start/shutdown orchestration  │
├─────────────────────────────────────────────────────────────────────┤
│                         Core Services                                │
│  PlayerRegistry, EventRegistry, CommandRegistry, Persistence         │
├─────────────────────────────────────────────────────────────────────┤
│                           Modules                                    │
│  MetabolismModule, ClaimsModule (planned), EconomyModule (planned)   │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Design Principles

1. **Per-Player Isolation**: All state is keyed by player UUID
2. **Thread Safety**: ConcurrentHashMap for all shared state
3. **ECS Thread Compliance**: All ECS access via `world.execute()`
4. **Hysteresis**: Different enter/exit thresholds to prevent flickering
5. **Graceful Degradation**: Systems fail silently to avoid server crashes

---

## Module System

### Module Interface

```java
public sealed interface Module permits AbstractModule {
    String getId();
    String getName();
    String getVersion();
    Set<String> getDependencies();

    void setup(ModuleContext context);
    void start();
    void shutdown();

    boolean isEnabled();
    ModuleState getState();
}
```

### Module Lifecycle

```
DISABLED ──setup()──> SETUP ──start()──> STARTED ──shutdown()──> STOPPED
                        │                    │
                        └────── ERROR <──────┘
```

### Dependency Resolution

ModuleManager performs topological sort on dependencies:

```java
private List<Module> resolveDependencyOrder() {
    // Kahn's algorithm for topological sort
    // Auto-enables required dependencies
}
```

Example: Enabling `traders` module auto-enables `economy` module.

---

## Metabolism Core

### MetabolismSystem

Central coordinator for all metabolism processing.

**Location**: `com.livinglands.modules.metabolism.MetabolismSystem`

#### Tick Loops

Two separate tick loops with different frequencies:

| Loop | Interval | Purpose |
|------|----------|---------|
| Main Tick | 1000ms | Stat depletion, debuffs, buffs, poison |
| Effect Detection | 50ms | Food/potion consumption detection |

```java
private void tick() {
    // Process all tracked players
    playerData.values().forEach(data -> processPlayer(data, currentTime));

    // Process subsystems
    poisonEffectsSystem.processPoisonEffects();
    debuffEffectsSystem.processDebuffEffects();
    buffEffectsSystem.processBuffEffects();
}
```

#### Player Data Structure

```java
public class PlayerMetabolismData {
    private final UUID playerUuid;

    // Core stats (0-100)
    private double hunger;
    private double thirst;
    private double energy;

    // Activity tracking
    private ActivityState currentActivity;
    private long lastActivityChange;
    private long combatEndTime;

    // Depletion tracking (millisecond timestamps)
    private long lastHungerDepletion;
    private long lastThirstDepletion;
    private long lastEnergyDepletion;

    // Statistics (for analytics)
    private double totalHungerDepleted;
    private double totalThirstDepleted;
    private double totalEnergyDepleted;
}
```

#### Activity Detection

Activity state detected from ECS `MovementStatesComponent`:

```java
public ActivityState detectActivity(Ref<EntityStore> ref, Store<EntityStore> store, World world) {
    // Must run on WorldThread
    var movementStates = store.getComponent(ref, MovementStatesComponent.getComponentType());

    if (movementStates.isSprinting()) return ActivityState.SPRINTING;
    if (movementStates.isSwimming()) return ActivityState.SWIMMING;
    if (movementStates.isWalking()) return ActivityState.WALKING;
    return ActivityState.IDLE;
}
```

Combat detection uses a 5-second window after damage events.

#### Depletion Formula

```
adjustedRateSeconds = baseRateSeconds / activityMultiplier
depletionIntervalMs = adjustedRateSeconds * 1000

if (currentTime - lastDepletion >= depletionIntervalMs) {
    stat = max(stat - 1.0, 0.0)
    lastDepletion = currentTime
}
```

---

## Thread Safety & ECS Access

### The Problem

Hytale's ECS (Entity Component System) is NOT thread-safe. Components can only be accessed from the WorldThread that owns the entity.

### The Solution

All ECS access is wrapped in `world.execute()`:

```java
world.execute(() -> {
    // This runs on the WorldThread
    var statMap = store.getComponent(ref, EntityStatMap.getComponentType());
    statMap.putModifier(statId, key, modifier);
});
```

### Data Structure Selection

All per-player tracking uses thread-safe collections:

```java
// Thread-safe map for player data
private final Map<UUID, PlayerMetabolismData> playerData = new ConcurrentHashMap<>();

// Thread-safe set for state tracking
private final Set<UUID> starvingPlayers = ConcurrentHashMap.newKeySet();

// Nested concurrent map for complex state
private final Map<UUID, Map<DebuffType, Long>> lastDebuffTickTime = new ConcurrentHashMap<>();
```

### PlayerRegistry

Centralized management of player sessions and ECS references:

```java
public class PlayerRegistry {
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    public Optional<PlayerSession> getSession(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }
}

public class PlayerSession {
    private final UUID playerId;
    private Ref<EntityStore> entityRef;
    private Store<EntityStore> store;
    private World world;

    public boolean isEcsReady() {
        return entityRef != null && store != null && world != null && entityRef.isValid();
    }
}
```

---

## Buff & Debuff Systems

### System Hierarchy

```
MetabolismSystem
├── DebuffsSystem         (low stat penalties)
├── BuffsSystem           (high stat bonuses)
├── PoisonEffectsSystem   (consumable poison)
├── DebuffEffectsSystem   (native Hytale debuffs)
└── BuffEffectsSystem     (native Hytale buffs)
```

### Hysteresis Pattern

Prevents rapid state flickering when stats hover near thresholds:

```java
// Buff activation/deactivation
activationThreshold = 90.0    // Stat must reach 90 to gain buff
deactivationThreshold = 80.0  // Stat must drop below 80 to lose buff

// Debuff example (hunger damage)
damageStartThreshold = 0.0    // Damage starts at 0 hunger
recoveryThreshold = 30.0      // Damage stops when hunger >= 30
```

### Debuff Priority

Debuffs suppress all buffs:

```java
public void processBuffs(UUID playerId, PlayerMetabolismData data) {
    // Check if player has active debuffs
    if (debuffsSystem != null && debuffsSystem.hasActiveDebuffs(playerId)) {
        removeAllBuffs(playerId, ref, store, world, player);
        return;  // Skip buff processing
    }
    // ... process buffs
}
```

### State Tracking

Each system tracks player states independently:

```java
// DebuffsSystem state tracking
private final Set<UUID> starvingPlayers = ConcurrentHashMap.newKeySet();
private final Set<UUID> dehydratedPlayers = ConcurrentHashMap.newKeySet();
private final Set<UUID> exhaustedPlayers = ConcurrentHashMap.newKeySet();
private final Set<UUID> parchedPlayers = ConcurrentHashMap.newKeySet();
private final Set<UUID> tiredPlayers = ConcurrentHashMap.newKeySet();

// BuffsSystem state tracking
private final Set<UUID> speedBuffedPlayers = ConcurrentHashMap.newKeySet();
private final Set<UUID> defenseBuffedPlayers = ConcurrentHashMap.newKeySet();
private final Set<UUID> staminaBuffedPlayers = ConcurrentHashMap.newKeySet();
```

---

## Speed Modification

### The Challenge

Hytale has no "movement speed" stat in `DefaultEntityStatTypes`. Speed is controlled through `MovementManager` component.

### Implementation

Speed modification uses `MovementSettings.baseSpeed`:

```java
private void applySpeedModifier(UUID playerId, Ref<EntityStore> ref,
                                 Store<EntityStore> store, World world,
                                 float multiplier) {
    world.execute(() -> {
        var movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager != null) {
            var settings = movementManager.getSettings();
            if (settings != null) {
                // Store original speed on first modification
                if (!originalBaseSpeeds.containsKey(playerId)) {
                    originalBaseSpeeds.put(playerId, settings.baseSpeed);
                }

                // Apply multiplier to original speed
                float originalSpeed = originalBaseSpeeds.get(playerId);
                settings.baseSpeed = originalSpeed * multiplier;
            }
        }
    });
}
```

### Speed Restoration

When debuffs clear, original speed is restored:

```java
private void removeSpeedModifier(UUID playerId, ...) {
    world.execute(() -> {
        // Only restore if no other speed-affecting debuffs active
        if (!parchedPlayers.contains(playerId) && originalBaseSpeeds.containsKey(playerId)) {
            var settings = movementManager.getSettings();
            settings.baseSpeed = originalBaseSpeeds.get(playerId);
            originalBaseSpeeds.remove(playerId);
        }
    });
}
```

### Conflict Prevention

Multiple systems can affect speed. Conflict prevention:

1. **Original speed stored once** - First modifier stores it
2. **Check other systems** - Don't restore if another system still needs reduced speed
3. **Buff suppression** - Debuffs automatically remove speed buffs

---

## Food & Potion Detection

### The Challenge

Hytale doesn't fire "item consumed" events. We must detect consumption indirectly.

### Detection Strategy

Monitor `EffectControllerComponent` for new food/potion effects:

```java
public class FoodEffectDetector {
    // Track effects seen on previous tick
    private final Map<UUID, Set<Integer>> previousEffects = new ConcurrentHashMap<>();

    // Cooldown to prevent double-detection
    private final Map<UUID, Long> recentConsumables = new ConcurrentHashMap<>();

    public List<DetectedEffect> detectNewEffects(PlayerSession session) {
        var currentEffects = getAllActiveEffectIds(session);
        var previousEffects = this.previousEffects.get(playerId);

        // Find effects that are new this tick
        var newEffects = currentEffects.stream()
            .filter(id -> !previousEffects.contains(id))
            .collect(toList());

        this.previousEffects.put(playerId, currentEffects);
        return newEffects;
    }
}
```

### Effect ID Patterns

Hytale generates dynamic effect IDs. Pattern matching required:

```java
// Food effect patterns
"Food_Instant_Heal_*"      // Instant health food (T1/T2/T3/Bread)
"Food_Health_Restore_*"    // Water drinks (Tiny/Small/Medium/Large)
"Food_Stamina_Restore_*"   // Stamina drinks
"Food_Health_Boost_*"      // Max health buff
"Food_Stamina_Boost_*"     // Max stamina buff
"Food_Health_Regen_*"      // Health regen food
"Food_Stamina_Regen_*"     // Stamina regen food
"Meat_Buff_*"              // Cooked meat (T1/T2/T3)
"FruitVeggie_Buff_*"       // Fruits/vegetables
"HealthRegen_Buff_*"       // Health regen buff
"Antidote"                 // Milk bucket

// Potion effect patterns
"Potion_Health_*"          // Health potions (Instant/Regen, Lesser/Greater)
"Potion_Stamina_*"         // Stamina potions
"Potion_Signature_*"       // Mana/Signature potions
"Potion_Mana_*"            // Mana potions (alternative naming)
"Potion_Morph_*"           // Morph potions (Dog, Frog, Mosshorn, Mouse, Pigeon)
"Potion_Regen_*"           // Generic regen potions
```

### Effect ID Retrieval

Effect IDs are retrieved using multiple strategies to handle dynamic effects:

```java
private String getEffectIdFromEffect(Object effect) {
    // Strategy 1: Try getType().getId() pattern (works for most effects)
    // Strategy 2: Try direct getId() method
    // Strategy 3: Fall back to asset map lookup by index
}
```

### Deduplication

Index-based deduplication prevents double detection while allowing rapid consecutive consumption:

```java
// Track processed effect indexes per player
private final Map<UUID, Set<Integer>> processedEffectIndexes = new ConcurrentHashMap<>();

// Cleanup interval allows same effect index to be re-detected
private static final long CLEANUP_INTERVAL_MS = 200;

// Skip if already processed this effect index recently
if (processed.contains(effectIndex)) {
    continue;
}
processed.add(effectIndex);
```

This approach:
1. Prevents duplicate detection if effect persists across ticks
2. Allows different consumables (different effect indexes) to be detected immediately
3. Cleans up processed indexes every 200ms for repeated consumption of same item type

### High-Frequency Detection

Some effects (instant heals) last only 100ms. Detection tick runs at 50ms:

```java
// effectDetectionTick() runs every 50ms
tickExecutor.scheduleAtFixedRate(this::effectDetectionTick, 50, 50, TimeUnit.MILLISECONDS);
```

---

## Poison System

### Two Poison Types

1. **Consumable Poison** - From eating poisonous items (our system)
2. **Native Poison** - Hytale's poison debuff (detected and enhanced)

### Consumable Poison Effects

```java
public enum PoisonEffectType {
    MILD_TOXIN,    // Short burst, fast drain
    SLOW_POISON,   // Long duration, slow drain
    PURGE,         // Severe drain then recovery phase
    RANDOM         // Randomly selects one of above
}
```

### Poison State Machine

```java
private static class ActivePoisonState {
    final PoisonEffectType effectType;
    final long startTime;
    final float durationSeconds;
    int ticksApplied;
    long lastTickTime;
    boolean inRecoveryPhase;  // For PURGE effect

    boolean isExpired() {
        return System.currentTimeMillis() - startTime > (durationSeconds * 1000);
    }
}
```

### Native Poison Enhancement

When Hytale's poison debuff is active, we add metabolism drain:

```java
if (nativePoisonDetector.hasNativePoisonDebuff(session)) {
    var details = nativePoisonDetector.getActivePoisonDetails(session);
    float tierMultiplier = getTierDrainMultiplier(details.tier());

    drainMetabolism(
        playerId,
        config.hungerDrainPerTick() * tierMultiplier,
        config.thirstDrainPerTick() * tierMultiplier,
        config.energyDrainPerTick() * tierMultiplier
    );
}
```

Tier multipliers:
- T1: 0.75x (lighter)
- T2: 1.0x (standard)
- T3: 1.5x (severe)

---

## Native Effect Integration

### DebuffEffectsSystem

Detects and responds to Hytale's native debuffs:

```java
public enum DebuffType {
    POISON,   // Poison, Poison_T1, Poison_T2, Poison_T3
    BURN,     // Burn, Lava_Burn, Flame_Staff_Burn
    STUN,     // Stun, Bomb_Explode_Stun
    FREEZE,   // Freeze
    ROOT,     // Root
    SLOW      // Slow, Two_Handed_Bow_Ability2_Slow
}
```

### NativeDebuffDetector

Scans `EffectControllerComponent` for active debuffs:

```java
public Map<DebuffType, ActiveDebuffDetails> getActiveDebuffs(PlayerSession session) {
    var effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());
    var activeEffects = effectController.getAllActiveEntityEffects();

    Map<DebuffType, ActiveDebuffDetails> debuffs = new HashMap<>();
    for (var effect : activeEffects) {
        if (!effect.isDebuff()) continue;

        String effectId = getEffectId(effect);
        DebuffType type = matchDebuffType(effectId);
        if (type != null) {
            debuffs.put(type, new ActiveDebuffDetails(effectId, type,
                effect.getRemainingDuration(), effect.getInitialDuration()));
        }
    }
    return debuffs;
}
```

### BuffEffectsSystem

Detects native food buffs for future integration:

```java
private static final Map<String, BuffType> BUFF_EFFECT_PATTERNS = Map.ofEntries(
    Map.entry("Food_Health_Boost", BuffType.DEFENSE),
    Map.entry("Food_Stamina_Boost", BuffType.STAMINA_REGEN),
    Map.entry("Meat_Buff", BuffType.STRENGTH),
    Map.entry("FruitVeggie_Buff", BuffType.VITALITY)
);
```

---

## Data Persistence

### File Structure

```
LivingLands/
├── modules.json           # Module enable/disable
├── metabolism/
│   └── config.json        # Metabolism configuration
└── playerdata/
    ├── {uuid1}.json       # Per-player data
    ├── {uuid2}.json
    └── ...
```

### PlayerDataPersistence

```java
public class PlayerDataPersistence {
    private final Path dataDirectory;
    private final Gson gson;

    public PlayerMetabolismData load(UUID playerId, double defaultHunger,
                                      double defaultThirst, double defaultEnergy) {
        Path file = dataDirectory.resolve(playerId.toString() + ".json");
        if (Files.exists(file)) {
            return gson.fromJson(Files.readString(file), PlayerMetabolismData.class);
        }
        return new PlayerMetabolismData(playerId, defaultHunger, defaultThirst, defaultEnergy);
    }

    public boolean save(PlayerMetabolismData data) {
        Path file = dataDirectory.resolve(data.getPlayerUuid().toString() + ".json");
        Files.writeString(file, gson.toJson(data));
        return true;
    }
}
```

### Save Triggers

1. **Player Disconnect** - `removePlayer()` saves before cleanup
2. **Server Shutdown** - `saveAll()` saves all tracked players
3. **Periodic Auto-save** - Optional, configurable

---

## Configuration System

### Configuration Hierarchy

```
ModulesConfig (modules.json)
└── MetabolismModuleConfig (metabolism/config.json)
    ├── MetabolismConfig
    │   ├── enableHunger, enableThirst, enableEnergy
    │   ├── depletionRates
    │   └── activityMultipliers
    ├── ConsumablesConfig
    │   └── food/potion restoration values
    ├── DebuffsConfig
    │   ├── HungerDebuffs
    │   ├── ThirstDebuffs
    │   └── EnergyDebuffs
    ├── BuffConfig
    │   ├── enabled, activationThreshold, deactivationThreshold
    │   └── StatBuffConfig (per buff type)
    └── PoisonConfig
        ├── MildToxinConfig
        ├── SlowPoisonConfig
        └── PurgeConfig
```

### Config Loading

```java
protected <T> T loadConfig(String filename, Class<T> type, Supplier<T> defaultSupplier) {
    Path configFile = configDirectory.resolve(filename);
    if (Files.exists(configFile)) {
        return gson.fromJson(Files.readString(configFile), type);
    }
    T defaultConfig = defaultSupplier.get();
    saveConfig(filename, defaultConfig);
    return defaultConfig;
}
```

### Configuration Records

Java records ensure immutability:

```java
public record DebuffsConfig(
    HungerDebuffs hunger,
    ThirstDebuffs thirst,
    EnergyDebuffs energy
) {
    public static DebuffsConfig defaultConfig() {
        return new DebuffsConfig(
            HungerDebuffs.defaults(),
            ThirstDebuffs.defaults(),
            EnergyDebuffs.defaults()
        );
    }
}
```

---

## Appendix: Key Classes Reference

| Class | Package | Purpose |
|-------|---------|---------|
| `LivingLandsPlugin` | `com.livinglands` | Plugin entry point |
| `ModuleManager` | `com.livinglands.core` | Module lifecycle |
| `PlayerRegistry` | `com.livinglands.core` | Player session management |
| `HudModule` | `com.livinglands.core.hud` | HUD system management |
| `LivingLandsPanelElement` | `com.livinglands.core.hud` | Unified panel display |
| `MetabolismModule` | `com.livinglands.modules.metabolism` | Module wrapper |
| `MetabolismSystem` | `com.livinglands.modules.metabolism` | Core metabolism logic |
| `DebuffsSystem` | `com.livinglands.modules.metabolism` | Low-stat penalties |
| `BuffsSystem` | `com.livinglands.modules.metabolism.buff` | High-stat bonuses |
| `PoisonEffectsSystem` | `com.livinglands.modules.metabolism.poison` | Consumable poison |
| `DebuffEffectsSystem` | `com.livinglands.modules.metabolism.debuff` | Native debuff drain |
| `FoodConsumptionProcessor` | `com.livinglands.modules.metabolism.consumables` | Food detection |
| `ActivityDetector` | `com.livinglands.modules.metabolism` | Movement detection |
| `PlayerMetabolismData` | `com.livinglands.modules.metabolism` | Per-player state |
| `LevelingModule` | `com.livinglands.modules.leveling` | Leveling module wrapper |
| `LevelingSystem` | `com.livinglands.modules.leveling` | Core leveling logic |
| `AbilitySystem` | `com.livinglands.modules.leveling.ability` | Passive ability management |
| `AbilityType` | `com.livinglands.modules.leveling.ability` | Ability definitions |

---

## Version History

| Version | Changes |
|---------|---------|
| 1.0.0-beta | Initial release: hunger, thirst, energy |
| 1.1.0-beta | Native debuff system, enhanced potion detection |
| 2.0.0-beta | Modular architecture refactor |
| 2.1.0-beta | Buff system, enhanced debuffs, speed modification |
| 2.2.0-beta | Speed modification client sync, performance optimization |
| 2.2.1-beta | Stamina debuff implementation |
| 2.3.0-beta | Enhanced HUD, passive abilities panel, leveling integration |
| 2.3.1-beta | Death detection fix, mining/logging XP exploit fix, block persistence |
| 2.3.2-beta | Speed flickering fix via centralized SpeedManager |
| 2.3.3-beta | Comprehensive consumable detection, reflection-based effect ID retrieval |
