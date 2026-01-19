# Changelog

All notable changes to Living Lands will be documented in this file.

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
