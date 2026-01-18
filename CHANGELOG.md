# Changelog

All notable changes to Living Lands will be documented in this file.

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
