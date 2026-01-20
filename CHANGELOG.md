# Changelog

All notable changes to Living Lands will be documented in this file.

## [2.4.1-beta] - 2026-01-20

### Fixed

#### Stale Stat Modifier Persistence
- **Hytale Persists Modifiers** - Discovered that Hytale saves stat modifiers (health/stamina bonuses) in player save files
- **Mismatch on Mod Reset** - When mod data was deleted but player saves weren't, old ability modifiers persisted incorrectly
- **Automatic Cleanup on Login** - Both ability and metabolism buff modifiers are now cleaned up on player login
- **Re-application Based on Actual State** - Modifiers are re-applied based on current levels/metabolism, not persisted values

#### Missing Tier 2 Ability Implementations
- **Warrior's Resilience** - Now properly restores 15% max health after kills (was TODO placeholder)
- **Survivalist Integration** - Metabolism system now queries PermanentBuffManager for depletion multiplier
- **Hunger/Thirst Reduction** - Both depleteHunger() and depleteThirst() apply Survivalist's -15% depletion rate

#### XP System Ability Handler Wiring
- **All 5 Professions Connected** - XP systems now trigger Tier 2 abilities when awarding XP:
  - MiningXpSystem → MiningAbilityHandler.onOreMined()
  - LoggingXpSystem → LoggingAbilityHandler.onLogChopped()
  - BuildingXpSystem → BuildingAbilityHandler.onBlockPlaced()
  - GatheringXpSystem → GatheringAbilityHandler.onItemGathered()
  - CombatXpSystem → CombatAbilityHandler.onKill() (already existed)
- **Removed Duplicate Handlers** - Ability handlers now wired through XP systems only, not registered separately

### Changed

#### Improved Modifier Management
- **cleanupStaleModifiers()** in PermanentBuffManager - Removes ability modifiers player hasn't unlocked
- **cleanupStaleModifiers()** in BuffsSystem - Removes metabolism buff modifiers on login
- **Order of Operations** - Cleanup happens after ECS ready, before HUD init and first tick

### Technical Details
- Added `getSurvivalistMultiplier()` helper to MetabolismSystem
- PermanentBuffManager checks all Tier 3 abilities to determine which modifiers should exist
- BuffsSystem clears tracking state after cleanup so buffs re-evaluate on next tick
- Modifier cleanup uses same `Predictable.SELF` for client sync

## [2.4.0-beta] - 2026-01-20

### Added

#### Passive Abilities Redesign
Complete redesign of the passive abilities system with harder-to-obtain, more impactful abilities:

**New Ability Structure (3 per Profession)**:
| Profession | Tier 1 (Lv.15) | Tier 2 (Lv.35) | Tier 3 (Lv.60) |
|------------|----------------|----------------|----------------|
| **Combat** | Adrenaline Rush (+20% speed on kill) | Warrior's Resilience (15% health restore) | Battle Hardened (+10% max health) |
| **Mining** | Prospector's Eye (+50% XP) | Efficient Extraction (no hunger drain 30s) | Iron Constitution (+15% max stamina) |
| **Logging** | Lumberjack's Vigor (+50% XP) | Forest's Blessing (+5 energy) | Nature's Endurance (+10% speed) |
| **Building** | Architect's Focus (+100% XP) | Steady Hands (no stamina drain 30s) | Master Builder (+10% max stamina) |
| **Gathering** | Forager's Intuition (+50% XP) | Nature's Gift (+3 hunger/thirst) | Survivalist (-15% metabolism drain) |

- **Higher Unlock Levels** - Abilities now unlock at 15, 35, 60 instead of 1, 5, 10, 15
- **Tier 3 Permanent Passives** - Level 60 abilities are always-active stat bonuses
- **Timed Buff Management** - Tier 2 abilities apply temporary buffs with countdown timers
- **Audio/Visual Feedback** - Abilities trigger chat messages AND sound effects

#### Active Ability Buffs in HUD
- **Cyan Buff Indicators** - Active ability buffs (from passive abilities) now display in the HUD
- **Countdown Timers** - Shows remaining duration for timed ability buffs
- **Up to 3 Ability Buffs** - Three slots for ability-triggered buffs alongside metabolism buffs/debuffs

#### Death Penalty System
- **XP Loss on Death** - Dying reduces XP by 85% in 2 random professions
- **Level Protection** - Death penalty never causes level-down (minimum XP = 0 for current level)
- **Immediate Persistence** - XP loss is saved immediately after death
- **Chat Feedback** - Colored messages show which professions lost XP and how much

#### Centralized Death Detection
- **PlayerDeathBroadcaster** - Shared utility in CoreModule for death events
- **Consumer-Based Listeners** - Modules can subscribe to death events via `addListener(Consumer<UUID>)`
- **Used By** - Both Metabolism (stat reset) and Leveling (death penalty) modules

#### Automated Version Management
- **Single Source of Truth** - Version now defined in `version.properties`
- **Gradle Integration** - `build.gradle.kts` reads version from properties file
- **Runtime Access** - `ModVersion.get()` provides version string at runtime
- **No More Drift** - JAR filename and in-game display always match

### Changed

#### Thread Safety Improvements
Comprehensive thread safety audit and fixes across the codebase:

- **ProfessionData** - XP and level now use `AtomicLong`/`AtomicInteger` for thread-safe operations
- **PlayerMetabolismData** - All stat access methods now `synchronized`
- **LevelingSystem** - `awardXp()` and `applyDeathPenalty()` use synchronized blocks
- **HudModule** - Atomic initialization pattern prevents duplicate player HUD setup
- **SpeedManager** - New `addBuffMultiplier()` method for atomic buff stacking
- **TimedBuffManager** - Atomic speed operations and improved shutdown handling
- **CombatXpSystem** - Better deduplication using target entity ref hash
- **PlayerSession** - Fixed ECS ready race condition with proper state tracking

#### Random Number Generation
- **ThreadLocalRandom** - Replaced shared `Random` instances with `ThreadLocalRandom.current()` for concurrent access

### Fixed

- **Speed Buff Race Condition** - Fixed race between ability buffs and metabolism speed modifications
- **Combat XP Deduplication** - Fixed potential double XP from same kill event
- **HUD Initialization** - Fixed rare duplicate HUD initialization for same player
- **Version Mismatch** - Fixed in-game version showing old value (was hardcoded, now dynamic)

### Technical Details
- New `PlayerDeathBroadcaster` ECS system in `com.livinglands.core`
- New `ModVersion` utility class for runtime version access
- New `version.properties` file as single source of truth
- `TimedBuffManager.getActiveBuffsForDisplay()` for HUD integration
- `AbilityBuff1-3` slots added to `LivingLandsHud.ui` with cyan color (#00CED1)
- 57 files changed with 2,645 additions and 1,022 deletions

---

## [2.3.4-beta] - 2026-01-20

### Changed

#### Reduced Console Spam
- **Verbose Logging Reduced** - Changed per-player operational logs from `INFO` to `FINE` level
- **Affected Logs** - Buff gained/lost, consumable detected, player connected/disconnected, ECS ready, HUD initialized
- **Server Admins** - Set `"LivingLands": "WARNING"` in server config.json to suppress INFO messages
- **Debug Mode** - Set `"LivingLands": "FINE"` in server config.json to see detailed per-player logs

#### Command Restructuring
- **Native Subcommands** - Refactored `/ll` command to use Hytale's native `addSubCommand()` system
- **Command List** - `/ll` now shows all available subcommands
- **Main Panel** - `/ll main` toggles the Living Lands stats panel
- **Help Panel** - `/ll help` displays mod information and common config paths

#### Removed Commands
- **`/stats` Removed** - Functionality consolidated into `/ll main` panel
- **`/skillgui` Removed** - Functionality consolidated into `/ll main` panel

#### UI Improvements
- **Semi-Transparent Backdrop** - Metabolism HUD now has a dark backdrop (`#1a1a1a` at 70% opacity)
- **Buff/Debuff Backdrops** - Individual backdrops for each buff (purple) and debuff (red)
- **Dynamic Visibility** - Buff/debuff containers hidden when empty, shown only when active
- **Bar Alignment** - Progress bars now display as `[||||.....] 99` with bar first for consistent alignment
- **Compact Width** - All backdrop elements standardized to 165px width

### Technical Details
- 30+ log statements changed from `Level.INFO` to `Level.FINE` across BuffsSystem, DebuffsSystem, PlayerRegistry, MetabolismPlayerListener, HudModule, FoodEffectDetector, SpeedManager, and LevelingSystem
- Production servers should now see only startup/shutdown messages at default INFO level
- Per-player events only visible when log level is set to FINE or lower
- `LivingLandsCommand` refactored to register `LivingLandsMainSubcommand` and `LivingLandsHelpSubcommand` via `addSubCommand()`
- Deleted `StatsCommand.java` and `SkillsGuiCommand.java`
- New `#Buff1Container`, `#Buff2Container`, `#Debuff1Container`, `#Debuff2Container` groups in UI with `Visible: false` default
- `MetabolismHudElement.updateBuffsDisplay()` and `updateDebuffsDisplay()` now control container visibility

---

## [2.3.3-beta] - 2026-01-20

### Added

#### Comprehensive Consumable Detection
- **Water/Drink Detection** - Added `Food_Health_Restore_*` effect detection for water mugs and buckets
- **Stamina Drinks** - Added `Food_Stamina_Restore_*` effect detection for stamina restoration drinks
- **Regen Food Effects** - Added `Food_Health_Regen_*` and `Food_Stamina_Regen_*` effect detection
- **Milk Detection** - Added `Antidote` effect detection for milk buckets (milk applies Antidote in Hytale)
- **Morph Potions** - Added `Potion_Morph_*` detection for morph potions (Dog, Frog, Mosshorn, Mouse, Pigeon)

### Fixed

#### Consumable Effect Detection
- **Effect ID Retrieval** - Fixed effect ID retrieval to use multiple strategies (reflection-based) for dynamic effects
- **Potion Detection Reliability** - Improved potion detection by getting effect ID directly from effect object instead of asset map lookup
- **Deduplication System** - Changed from time-based (500ms window) to index-based deduplication, allowing rapid consecutive potion use while preventing duplicate detection

### Changed

#### Commands
- **Stats Command** - Changed `/stats` command to `/ll stats` for consistency with other Living Lands commands

### Technical Details
- New `getEffectIdFromEffect()` method uses three strategies: `getType().getId()`, direct `getId()`, and asset map fallback
- Removed redundant `DRINK_EFFECT_PREFIXES` - water/milk items use `Food_Health_Restore_*` effects
- `processedEffectIndexes` map tracks processed effects per-player with 200ms cleanup interval
- Added comprehensive effect pattern matching for all vanilla Hytale consumables

---

## [2.3.2-beta] - 2026-01-20

### Fixed

#### Speed Modification Flickering
- **Centralized Speed Management** - Fixed speed flickering caused by race condition between buff and debuff systems
- **SpeedManager** - New centralized component that tracks original speed and combines all multipliers before applying
- **Single Update Per Tick** - Speed is now applied once after both systems process, eliminating conflicting updates

### Technical Details
- New `SpeedManager` class handles all speed modifications centrally
- `DebuffsSystem` and `BuffsSystem` now set multipliers via SpeedManager instead of directly modifying baseSpeed
- Combined multiplier calculated as: `energyDebuff * thirstDebuff * buff` (debuffs take priority over buffs)
- Original base speed captured only once on first modification, ensuring correct restoration

---

## [2.3.1-beta] - 2026-01-20

### Fixed

#### Death Detection
- **Metabolism Reset on Death** - Fixed metabolism not resetting when players die and respawn
- **ECS-Based Detection** - Now uses `KillFeedEvent.DecedentMessage` ECS system to reset metabolism immediately on death rather than waiting for respawn event

#### Mining/Logging XP Exploit
- **Player-Placed Block Detection** - Fixed exploit where players could gain XP by breaking blocks they placed themselves
- **Empty Block Event Handling** - Fixed issue where `BreakBlockEvent` with `block=Empty` (fires when placing blocks) was corrupting block tracking
- **Correct Hytale Block IDs** - Updated to use actual Hytale block naming conventions:
  - Ores: `Ore_Copper`, `Ore_Iron`, `Ore_Gold`, etc. (prefix matching)
  - Rocks: `Rock_Stone`, `Rock_Basalt`, `Rock_Sandstone`, etc. (exact matching)
  - Wood: `Wood_*` prefix (e.g., `Wood_Oak_Trunk`, `Wood_Birch_Roots`)
  - Leaves: `Plant_Leaves_*` prefix (e.g., `Plant_Leaves_Oak`)

#### Player-Placed Block Persistence
- **Cross-Restart Tracking** - Player-placed blocks are now tracked persistently across server restarts
- **Per-World Storage** - Placed block positions stored per-world in `LivingLands/leveling/placed_blocks/`

### Technical Details
- New `PlayerDeathSystem` ECS system listens to `KillFeedEvent.DecedentMessage`
- `MiningXpSystem` and `LoggingXpSystem` skip events where `blockId == "Empty"`
- Block ID matching uses prefix patterns for ores/wood/leaves and exact matches for stone blocks
- `PlacedBlockPersistence` saves/loads placed block positions per world

---

## [2.3.0-beta] - 2026-01-19

### Added

#### Enhanced HUD Layout
- **Vertically Aligned Progress Bars** - Metabolism bars now align vertically with labels on the left
- **Active Effects Display** - Buffs and debuffs now display below the metabolism bars on the HUD
- **Color-Coded Effects** - Buffs display in violet (`[+] Well Fed`), debuffs display in red (`[-] Starving`)
- **Panel Title** - Main Living Lands panel now displays "LIVING LANDS" title with section headers
- **XP Notification Repositioned** - XP gain notifications now appear to the right of the metabolism bars

#### HUD Active Effects
- **Buff Indicators** - Shows `[+] Well Fed`, `[+] Hydrated`, `[+] Energized` when stats >= 80%
- **Debuff Indicators** - Shows `[-] Starving`, `[-] Dehydrated`, `[-] Exhausted` when stats <= 20%
- **Separate Display** - Up to 2 buffs (violet) and 2 debuffs (red) displayed simultaneously

#### Passive Abilities Section (Panel)
- **New Panel Section** - The `/ll main` panel now shows unlocked passive abilities from the leveling system
- **Ability Display** - Shows up to 5 unlocked abilities with trigger chances (e.g., `[✓] Critical Strike (15%)`)
- **Green Color Coding** - Abilities display in green (#58d68d) for easy identification
- **AbilitySystem Integration** - Panel integrates with the leveling module's ability system

#### Leveling Passive Abilities
Nine passive abilities across five professions:
- **Combat**: Critical Strike (1.5x damage), Lifesteal (10% heal)
- **Mining**: Double Ore, Lucky Strike (rare gems)
- **Logging**: Efficient Chopping (instant tree), Bark Collector
- **Building**: Material Saver (don't consume materials)
- **Gathering**: Double Harvest, Rare Find

### Changed

#### UI Element Structure
- **Text-Based Progress Bars** - Metabolism uses text bars (`75 [|||||||...]`) for reliable rendering
- **Simplified UI File** - Removed vanilla template references that caused loading errors
- **Proper Selector Names** - Panel effects now use `#PanelEffect1-3` selectors
- **Panel Height Increased** - Panel height increased from 420px to 520px to accommodate abilities section

### Fixed

- **UI Loading Crash** - Fixed "Could not resolve relative path" error with vanilla UI template references
- **Selector Mismatch** - Fixed `#Effect1.Text` selector not found error by using correct `#PanelEffect` selectors
- **Dynamic Width Error** - Fixed "selector doesn't match a markup property" for `#HungerBar.Anchor.Width`

### Technical Details
- UI file simplified to use only Group/Label elements without external template dependencies
- MetabolismHudElement now uses `#HungerBar.Text`, `#ThirstBar.Text`, `#EnergyBar.Text` for bar display
- Added separate `#Buff1`, `#Buff2` (violet #9b59b6) and `#Debuff1`, `#Debuff2` (red #e74c3c) labels
- LivingLandsPanelElement updated to use `#PanelEffect1-3` for panel effects section
- New `#PanelAbility1-5` labels for displaying unlocked abilities
- AbilitySystem integration via HudModule.setAbilitySystem()
- XP notification moved from Top: 160 to Top: 16, Left: 310 for right-side alignment

---

## [2.2.1-beta] - 2026-01-19

### Added

#### Stamina Debuff Implementation
- **Energy Stamina Debuff** - Low energy now reduces max stamina, making stamina deplete faster
- **Thirst Stamina Debuff** - Low thirst now reduces max stamina pool, simulating reduced stamina regeneration

### Technical Details
- Implemented `applyStaminaModifier` using `StaticModifier` with `MULTIPLICATIVE` on `MAX` stamina
- Implemented `removeStaminaModifier` to properly clean up energy-based stamina debuffs
- Implemented `applyThirstStaminaRegenModifier` using `StaticModifier` for thirst-based stamina reduction
- Implemented `removeThirstStaminaRegenModifier` to properly clean up thirst-based stamina debuffs
- Uses separate modifier keys (`MODIFIER_KEY_ENERGY_STAMINA`, `MODIFIER_KEY_THIRST_STAMINA`) for independent tracking
- Energy debuff uses inverse multiplier (1.5x consumption = 0.67x max stamina)
- Thirst debuff uses direct multiplier (0.45 = 45% max stamina at 0 thirst)

---

## [2.2.0-beta] - 2026-01-19

### Fixed

#### Speed Modification Client Sync
- **Movement Settings Sync** - Speed buffs/debuffs now properly sync to the client via `MovementManager.update(PacketHandler)`
- **Debuff Speed Penalties** - Players now actually feel speed reductions from thirst/energy debuffs
- **Buff Speed Bonuses** - Speed buff from high energy now works correctly in-game
- **Thirst Speed Debuff** - Parched state speed reduction now visible to players
- **Energy Speed Debuff** - Tired state speed reduction now visible to players

#### Stat Modifier Compatibility
- **StaticModifier Usage** - Replaced custom `MultiplyModifier` class with Hytale's built-in `StaticModifier`
- **Server Crash Fix** - Fixed codec serialization error that crashed server on player save
- **Client Compatibility** - Fixed "Only static modifiers supported on the client currently" error

### Changed

#### Performance Optimization
- **Batched Effect Detection** - Food consumption detection now processes players in batches of 10 per 50ms tick
- **O(n) Scaling** - Reduced per-tick overhead from O(n) to O(batch_size) for effect detection
- **High Player Count Support** - Improved performance for servers with 100+ players

### Technical Details
- Added `PlayerRef` import for accessing `PacketHandler` to sync movement settings
- Added `movementManager.update(playerRef.getPacketHandler())` calls after all speed modifications
- Added `EFFECT_DETECTION_BATCH_SIZE = 10` constant for configurable batch processing
- Added `effectDetectionBatchIndex` for round-robin batch iteration
- Removed unused `MultiplyModifier` inner class from `DebuffsSystem`

---

## [2.1.0-beta] - 2026-01-19

### Added

#### Buff System (High Metabolism Rewards)
- **Speed Buff** - Increased movement speed when Energy ≥ 90%
- **Defense Buff** - Increased max health when Hunger ≥ 90%
- **Stamina Buff** - Increased max stamina when Thirst ≥ 90%
- **Hysteresis System** - Buffs activate at 90%, deactivate at 80% to prevent flickering
- **Buff Priority** - Debuffs suppress all buffs (debuffs take priority)
- **Buff Detection** - Detects native Hytale food buffs (Health_Boost, Stamina_Boost, Meat_Buff, FruitVeggie_Buff)

#### Enhanced Thirst Debuffs
- **Parched State** - New debuff tier when Thirst < 30
- **Gradual Speed Reduction** - Speed decreases proportionally as thirst drops (up to 55% reduction at 0)
- **Gradual Stamina Regen Reduction** - Stamina regeneration slows proportionally (up to 55% reduction at 0)
- **Separate from Dehydrated** - Parched applies movement penalties, Dehydrated (Thirst = 0) adds damage

#### Enhanced Energy Debuffs
- **Tired State** - New debuff tier when Energy < 30 (separate from Exhausted)
- **Gradual Speed Reduction** - Speed decreases proportionally as energy drops (up to 40% reduction at 0)
- **Exhausted State** - Stamina drains rapidly when Energy = 0

#### Player Feedback System
- **Chat Messages** - Players receive colored messages when entering/exiting debuff states
- **State Entry Messages** - Red messages warn of debuff activation (e.g., "You are starving! Find food quickly!")
- **State Exit Messages** - Green messages confirm recovery (e.g., "You are no longer starving.")
- **All States Covered** - Feedback for Starving, Dehydrated, Parched, Tired, and Exhausted states

#### StatsCommand Enhancement
- **Active Buffs Display** - `/stats` command now shows list of active buffs

### Changed

#### Debuff Severity
- **Thirst Debuff Severity** - Increased from 15% to 55% maximum speed/stamina reduction
- **Proportional Scaling** - Debuffs now scale linearly based on stat level below threshold

#### Architecture
- **BuffsSystem** - New system for managing stat-based buffs
- **BuffEffectsSystem** - New system for detecting native Hytale food buff effects
- **NativeBuffDetector** - Detects active buff effects from EffectControllerComponent
- **BuffType Enum** - SPEED, DEFENSE, STAMINA_REGEN, STRENGTH, VITALITY
- **BuffConfig** - Configuration for buff activation/deactivation thresholds and multipliers

#### Documentation
- **README Overhaul** - Complete rewrite with clearer structure and comprehensive documentation
- **Buff System Documentation** - Detailed explanation of buff mechanics and hysteresis
- **Debuff System Documentation** - Detailed explanation of debuff tiers and severity scaling
- **Architecture Diagrams** - Updated to show new buff/debuff systems

### Technical Details
- New package: `com.livinglands.modules.metabolism.buff`
- New files: `BuffType.java`, `BuffConfig.java`, `BuffsSystem.java`, `BuffEffectsSystem.java`, `NativeBuffDetector.java`, `ActiveBuffDetails.java`
- New tracking sets in DebuffsSystem: `parchedPlayers`, `tiredPlayers`
- Thread-safe player feedback via `sendDebuffMessage()` helper

---

## [2.0.0-beta] - 2026-01-19

### Added

#### Modular Plugin Architecture
- **Module API** - Sealed `Module` interface and `AbstractModule` base class for lifecycle management
- **ModuleManager** - Handles registration, dependency resolution via topological sort, and orchestration
- **modules.json Configuration** - Server administrators can toggle features independently
- **Per-Module Config Directories** - Each module receives dedicated configuration (e.g., `LivingLands/<module>/config.json`)
- **Module Lifecycle States** - Progression through: DISABLED → SETUP → STARTED → STOPPED → ERROR
- **Dependency Injection** - Clean dependency injection via `ModuleContext`

#### New Modules
- **Metabolism Module** - Refactored into a self-contained module with dedicated config
- **Claims Module** - Placeholder for future land claiming system
- **Economy Module** - Placeholder for future economy system
- **Groups Module** - Placeholder for future group/guild system
- **Leveling Module** - Placeholder for future leveling system
- **Traders Module** - Placeholder for future NPC trader system
- **Automatic Dependency Enablement** - Enabling dependent modules auto-enables requirements (e.g., Traders → Economy)

### Changed

#### Architecture
- **Codebase Restructure** - Entire codebase restructured to support enable/disable of individual features via configuration
- **Backward Compatibility** - Existing Metabolism functionality preserved

### Fixed

#### Chat Display
- **Chat Color Format** - Fixed chat message colors by enforcing hex code format (#RRGGBB, rgb(), rgba())
- **ColorUtil** - Added utility for semantic color name conversion (Hytale's Message API only recognizes hex formats)

### Technical Details
- 50 files changed with 2,228 additions and 677 deletions
- New package structure: `com.livinglands.core`, `com.livinglands.modules.*`
- New config: `modules.json` for module enable/disable toggles

---

## [1.1.0-beta] - 2026-01-19

### Added

#### Native Debuff System
- **Debuff Detection** - Detects all native Hytale debuffs via ECS EffectControllerComponent
- **Poison Effects** - Poison, Poison_T1, Poison_T2, Poison_T3 drain hunger, thirst, and energy
- **Burn Effects** - Burn, Lava_Burn, Flame_Staff_Burn cause severe thirst drain (dehydration from heat)
- **Stun Effects** - Stun, Bomb_Explode_Stun cause high energy drain (panic/struggle)
- **Freeze Effect** - Freeze causes high energy drain (hypothermia/shivering)
- **Root Effect** - Root causes moderate energy drain (struggling to break free)
- **Slow Effects** - Slow, Two_Handed_Bow_Ability2_Slow cause low energy drain (fatigue)

#### Enhanced Potion Detection
- **Health Potions** - Now restore slight hunger and high thirst
- **Mana/Signature Potions** - Now restore slight energy and high thirst
- **Stamina Potions** - Now restore slight energy and high thirst
- **Potion Deduplication** - Prevents double restoration when potions apply multiple effects
- **Auto-Generated Effect IDs** - Handles Hytale's dynamic effect ID patterns (e.g., `Potion_Health_Large_InteractionVars_...`)
- **Effect ID Markers** - Handles leading markers like `***` on effect IDs
- **Alternative Naming** - Supports `Potion_Regen_Health_*` pattern alongside `Potion_Health_*`

#### Improved Food Detection
- **High-Frequency Detection** - 50ms tick interval catches instant heal effects (100ms duration)
- **Thread Safety** - All ECS access runs on WorldThread via `world.execute()`

### Changed

#### Configuration
- **DebuffConfig** - New configuration section for all debuff drain rates
- **Per-Debuff Toggles** - Enable/disable individual debuff types
- **Customizable Drain Rates** - Configure hunger, thirst, and energy drain per debuff type
- **Tick Intervals** - Configure how often each debuff type applies drain

#### Architecture
- **DebuffEffectsSystem** - New system managing all native debuff metabolism impacts
- **NativeDebuffDetector** - Unified detector for all 13 debuff effect types
- **DebuffType Enum** - Categorizes debuffs: POISON, BURN, STUN, FREEZE, ROOT, SLOW
- **FoodEffectDetector** - Enhanced with potion detection and deduplication

### Fixed
- **Thread Safety** - Fixed `IllegalStateException` when accessing ECS from metabolism thread
- **Instant Heal Detection** - Fixed missing detection of 100ms duration effects
- **Potion Double-Counting** - Fixed potions applying metabolism restoration twice

### Technical Details
- New package: `com.livinglands.metabolism.debuff`
- New config: `DebuffConfig` with sub-configs for each debuff type
- Effect detection runs at 50ms intervals for consumables, 1s for debuffs

---

## [1.0.0-beta] - 2026-01-18

### Initial Beta Release

The first public beta of Living Lands - a survival mechanics mod for Hytale servers.

### Added

#### Core Systems
- **Metabolism System** - Tick-based stat management with configurable depletion rates
- **Player Registry** - Centralized player session and ECS reference management
- **Data Persistence** - JSON-based player data saving/loading per player UUID

#### Survival Stats
- **Hunger** (0-100) - Depletes every 60 seconds by default
- **Thirst** (0-100) - Depletes every 45 seconds by default
- **Energy** (0-100) - Depletes every 90 seconds by default

#### Activity Multipliers
- **Sprinting** - 2x faster stat depletion
- **Swimming** - 1.5x faster stat depletion
- **Combat** - 1.5x faster stat depletion (5-second combat detection window)

#### Consumables
- **Foods** - 40+ vanilla Hytale foods restore hunger (8-65 points based on tier)
- **Drinks** - Potions restore thirst (20-50 points)
- **Stamina Potions** - Restore both thirst and energy
- **Milk** - Restores both hunger and thirst
- **Salads** - Restore both hunger and thirst

#### Sleep System
- **Bed Interaction** - Sleep in beds to restore 50 energy
- **Sleep Schedule** - Respects Hytale's day/night cycle
- **Cooldown** - 5-second cooldown between sleep attempts

#### Debuff System
- **Starvation** - Movement penalty when hunger < 20
- **Dehydration** - Movement penalty when thirst < 20
- **Exhaustion** - Movement penalty when energy < 20

#### Commands
- `/stats` - View current hunger, thirst, and energy levels with status indicators

#### HUD Display
- **Custom HUD Overlay** - Displays hunger, thirst, and energy with icons
- **Real-time Updates** - Stats update as values change
- **Positioned Top-Left** - Non-intrusive placement

#### Configuration
- **JSON Configuration** - All values configurable via `config.json`
- **Per-Stat Toggle** - Enable/disable individual stats
- **Custom Depletion Rates** - Adjust how fast stats decrease
- **Custom Thresholds** - Set when debuffs activate
- **Activity Multipliers** - Fine-tune activity impacts

### Technical Details
- Built for Hytale Server API
- Java 25+ required
- Gradle 9.x build system
- Apache-2.0 License

### Known Issues
- `getPlayerRef()` API is deprecated and may change in future Hytale versions
- HUD icons require texture files in the correct asset path

---

*This is a beta release. Please report any issues on [GitHub](https://github.com/MoshPitCodes/hytale-livinglands/issues).*
