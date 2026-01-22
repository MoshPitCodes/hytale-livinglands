<!-- DO NOT TOUCH THIS SECTION#1: START -->
<h1 align="center">
   <br>
   <img src="./.github/assets/logo/hytale-livinglands-logo.png" width="400px" /><br>
      Living Lands | Hytale RPG Survival Mod
   <br>
   <img src="./.github/assets/pallet/pallet-0.png" width="800px" /> <br>

   <div align="center">
      <p></p>
      <div align="center">
         <a href="https://github.com/MoshPitCodes/hytale-livinglands/stargazers">
            <img src="https://img.shields.io/github/stars/MoshPitCodes/hytale-livinglands?color=FABD2F&labelColor=282828&style=for-the-badge&logo=starship&logoColor=FABD2F">
         </a>
         <a href="https://github.com/MoshPitCodes/hytale-livinglands/">
            <img src="https://img.shields.io/github/repo-size/MoshPitCodes/hytale-livinglands?color=B16286&labelColor=282828&style=for-the-badge&logo=github&logoColor=B16286">
         </a>
         <a href="https://hytale.com">
            <img src="https://img.shields.io/badge/Hytale-Server%20Mod-blue.svg?style=for-the-badge&labelColor=282828&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0iIzQ1ODU4OCIgZD0iTTEyIDJMMiA3bDEwIDVsMTAtNUwxMiAyeiIvPjwvc3ZnPg==&logoColor=458588&color=458588">
         </a>
         <a href="https://github.com/MoshPitCodes/hytale-livinglands/blob/main/LICENSE">
            <img src="https://img.shields.io/static/v1.svg?style=for-the-badge&label=License&message=Apache-2.0&colorA=282828&colorB=98971A&logo=apache&logoColor=98971A&"/>
         </a>
      </div>
      <br>
   </div>
</h1>

<br/>
<!-- DO NOT TOUCH THIS SECTION#1: END -->

# Overview

**Living Lands** is an immersive RPG survival mod for Hytale that introduces realistic survival mechanics. Players must manage three core stats - **Hunger**, **Thirst**, and **Energy** - while exploring, fighting, and surviving.

The mod features a dynamic **buff and debuff system** that rewards well-maintained stats with powerful bonuses while penalizing neglect with harsh consequences. All mechanics integrate seamlessly with vanilla Hytale items and effects.

**Key Highlights:**
- **Three survival stats** that deplete based on activity level
- **Buffs** when stats are high (90%+) - speed, defense, and stamina bonuses
- **Debuffs** when stats are low - movement penalties, stamina drain, and damage
- **Native Hytale integration** - poison, burn, freeze effects drain your metabolism
- **Modular architecture** - server admins can enable/disable features independently

<br/>

# How It Works

## The Three Stats

Living Lands tracks three core survival statistics for each player:

| Stat | Icon | Depletion Rate | Primary Restoration |
|------|------|----------------|---------------------|
| **Hunger** | 🍖 | 1 point / 60 seconds | Food items |
| **Thirst** | 💧 | 1 point / 45 seconds | Potions, water, milk |
| **Energy** | ⚡ | 1 point / 90 seconds | Sleeping, stamina potions |

All stats range from **0 to 100**. Higher is better.

## Activity Multipliers

Your activity level affects how fast stats deplete:

| Activity | Multiplier | Example |
|----------|------------|---------|
| **Idle/Walking** | 1.0x | Normal exploration |
| **Sprinting** | 2.0x | Running consumes stats twice as fast |
| **Swimming** | 1.5x | Water activities are tiring |
| **Combat** | 1.5x | Fighting drains you faster (5-second window) |

<br/>

# Buff System

When your stats are **high** (90% or above), you gain powerful buffs that enhance your abilities.

## Stat-Based Buffs

| Stat Threshold | Buff | Effect |
|----------------|------|--------|
| Energy ≥ 90 | **Speed Boost** | Increased movement speed |
| Hunger ≥ 90 | **Defense Boost** | Increased max health |
| Thirst ≥ 90 | **Stamina Boost** | Increased max stamina |

### Hysteresis (Anti-Flicker)

Buffs use a **hysteresis system** to prevent rapid on/off flickering:
- **Activation**: Stat must reach **90%** to gain buff
- **Deactivation**: Stat must drop below **80%** to lose buff

This means once you have a buff, small fluctuations won't constantly toggle it.

### Buff Priority

**Debuffs suppress all buffs.** If you have any active debuff (starving, dehydrated, exhausted, etc.), all stat-based buffs are removed until you recover.

<br/>

# Debuff System

When your stats drop **low**, you suffer debuffs that impair your abilities. The system has multiple severity tiers.

## Hunger Debuffs

| Condition | Threshold | Effect |
|-----------|-----------|--------|
| **Starving** | Hunger = 0 | Takes damage every 3 seconds (escalating 1→5 damage) |
| **Recovery** | Hunger ≥ 30 | Damage stops |

## Thirst Debuffs

| Condition | Threshold | Effect |
|-----------|-----------|--------|
| **Parched** | Thirst < 30 | Gradual speed and stamina regen reduction |
| **Dehydrated** | Thirst = 0 | Takes 1.5 damage every 4 seconds |
| **Recovery** | Thirst ≥ 30 | Damage and penalties stop |

**Parched Severity** (proportional to thirst level):
- At 30 thirst: No penalty
- At 15 thirst: 27.5% speed/stamina reduction
- At 0 thirst: **55% speed/stamina reduction**

## Energy Debuffs

| Condition | Threshold | Effect |
|-----------|-----------|--------|
| **Tired** | Energy < 30 | Gradual speed reduction (up to 40% at 0) + increased stamina consumption |
| **Exhausted** | Energy = 0 | Stamina drains 5 per second |
| **Recovery** | Energy ≥ 50 | Stamina drain stops |

**Tired Severity** (proportional to energy level):
- At 30 energy: No penalty
- At 15 energy: 20% speed reduction + 1.25x stamina consumption
- At 0 energy: **40% speed reduction + 1.5x stamina consumption**

## Player Feedback

The mod sends **colored chat messages** when you enter or exit debuff states:

| Message | Color | Meaning |
|---------|-------|---------|
| "You are starving! Find food quickly!" | 🔴 Red | Entering debuff |
| "You are no longer starving." | 🟢 Green | Recovered |
| "You are getting thirsty..." | 🔴 Red | Entering parched state |
| "Your thirst is quenched..." | 🟢 Green | Recovered |

<br/>

# Native Hytale Effects

Living Lands integrates with Hytale's native effect system. Combat debuffs and food buffs affect your metabolism.

## Combat Debuffs (Drain Stats)

While affected by native Hytale debuffs, your metabolism drains:

| Debuff Type | Effects | Hunger | Thirst | Energy |
|-------------|---------|--------|--------|--------|
| **Poison** | Poison, Poison_T1/T2/T3 | ●●○ | ●●○ | ●○○ |
| **Burn** | Burn, Lava_Burn, Flame_Staff_Burn | ●○○ | ●●● | ●●○ |
| **Stun** | Stun, Bomb_Explode_Stun | ●○○ | ●○○ | ●●● |
| **Freeze** | Freeze | ●○○ | ●○○ | ●●● |
| **Root** | Root | ●○○ | ●○○ | ●●○ |
| **Slow** | Slow, Two_Handed_Bow_Ability2_Slow | ●○○ | ●○○ | ●○○ |

*Legend: ●●● = High drain, ●●○ = Moderate, ●○○ = Low*

**Poison tiers** scale drain amounts:
- T1: 75% drain rate
- T2: 100% drain rate (standard)
- T3: 150% drain rate

## Consumable Detection

The mod detects when you consume food, potions, and drinks by monitoring effect application:

| Effect Pattern | Type | Detected |
|----------------|------|----------|
| `Food_Instant_Heal_*` | Instant health food | ✅ |
| `Food_Health_Restore_*` | Water/drinks | ✅ |
| `Food_Stamina_Restore_*` | Stamina drinks | ✅ |
| `Food_Health_Boost_*` | Defense buff | ✅ |
| `Food_Stamina_Boost_*` | Stamina buff | ✅ |
| `Food_Health_Regen_*` | Health regen food | ✅ |
| `Food_Stamina_Regen_*` | Stamina regen food | ✅ |
| `Meat_Buff_*` | Cooked meat | ✅ |
| `FruitVeggie_Buff_*` | Fruits/vegetables | ✅ |
| `HealthRegen_Buff_*` | Health regen buff | ✅ |
| `Potion_Health_*` | Health potions | ✅ |
| `Potion_Stamina_*` | Stamina potions | ✅ |
| `Potion_Signature_*` | Mana potions | ✅ |
| `Potion_Morph_*` | Morph potions | ✅ |
| `Antidote` | Milk bucket | ✅ |

<br/>

# Consumables

## Foods (Restore Hunger)

| Tier | Examples | Hunger Restored |
|------|----------|-----------------|
| **Low** | Raw meats, Eggs, Vegetables | 8-18 |
| **Medium** | Bread, Cheese, Cooked vegetables | 20-35 |
| **High** | Cooked meats, Kebabs, Salads | 40-50 |
| **Premium** | Pies, Meat dishes | 55-65 |

## Potions (Multi-Stat Restoration)

| Potion Type | Hunger | Thirst | Energy |
|-------------|--------|--------|--------|
| **Health Potions** | +5-10 | +20-40 | - |
| **Mana Potions** | - | +20-40 | +5-10 |
| **Stamina Potions** | - | +20-40 | +5-10 |

Potion tiers (Lesser/Small vs Greater/Large) affect restoration amounts.

## Drinks

| Type | Thirst | Hunger | Notes |
|------|--------|--------|-------|
| **Water Bucket** | +60 | - | Pure hydration |
| **Milk Bucket** | +50 | +15 | Dual restoration |

## Special Items

| Item | Hunger | Thirst | Notes |
|------|--------|--------|-------|
| **Salads** | +40-50 | +20-30 | Fresh vegetables hydrate |
| **Milk** | +15 | +50 | Nutritious drink |

<br/>

# Poison System

Living Lands has its own **consumable poison system** separate from native Hytale poison. Eating poisonous items triggers one of three effects:

| Effect | Duration | Drain Rate | Description |
|--------|----------|------------|-------------|
| **Mild Toxin** | ~10 seconds | Fast | Quick burst of metabolism drain |
| **Slow Poison** | ~60 seconds | Slow | Extended gradual drain |
| **Purge** | ~30 seconds | Severe then recovery | Major drain followed by faster recovery |

Some items have **RANDOM** poison that picks one of the above effects.

<br/>

# Sleep System

Rest in beds to restore energy:

| Setting | Value |
|---------|-------|
| **Energy Restored** | +50 per sleep |
| **Cooldown** | 5 seconds between attempts |
| **Schedule** | Only during valid sleep hours |

If you try to sleep outside of Hytale's sleep hours, you'll see a message explaining you can't rest yet.

<br/>

# Leveling System

Living Lands includes a complete profession leveling system with passive abilities.

## Five Professions

| Profession | XP Source | Passive Abilities (3 per profession) |
|------------|-----------|--------------------------------------|
| **Combat** | Killing mobs | Adrenaline Rush, Warrior's Resilience, Battle Hardened |
| **Mining** | Breaking ores | Prospector's Eye, Efficient Extraction, Iron Constitution |
| **Logging** | Chopping trees | Lumberjack's Vigor, Forest's Blessing, Nature's Endurance |
| **Building** | Placing blocks | Architect's Focus, Steady Hands, Master Builder |
| **Gathering** | Harvesting plants | Forager's Intuition, Nature's Gift, Survivalist |

## Passive Abilities

Each profession has **3 abilities** that unlock at specific levels:

| Tier | Unlock Level | Type | Description |
|------|--------------|------|-------------|
| **Tier 1** | Level 15 | XP Boost / Timed Buff | Chance-based abilities that boost XP or provide temporary effects |
| **Tier 2** | Level 35 | Resource Management | Chance-based abilities for stat restoration or depletion pause |
| **Tier 3** | Level 60 | Permanent Passive | Always-active stat bonuses once unlocked |

### Combat Abilities
| Ability | Unlock | Base Chance | Effect |
|---------|--------|-------------|--------|
| **Adrenaline Rush** | Lv.15 | 8% → 35% | +20% movement speed for 10s after a kill |
| **Warrior's Resilience** | Lv.35 | 6% → 25% | Restore 15% max health after a kill |
| **Battle Hardened** | Lv.60 | Always Active | Permanent +10% max health |

### Mining Abilities
| Ability | Unlock | Base Chance | Effect |
|---------|--------|-------------|--------|
| **Prospector's Eye** | Lv.15 | 10% → 40% | +50% mining XP |
| **Efficient Extraction** | Lv.35 | 8% → 30% | Pause hunger depletion for 30s |
| **Iron Constitution** | Lv.60 | Always Active | Permanent +15% max stamina |

### Logging Abilities
| Ability | Unlock | Base Chance | Effect |
|---------|--------|-------------|--------|
| **Lumberjack's Vigor** | Lv.15 | 10% → 40% | +50% logging XP |
| **Forest's Blessing** | Lv.35 | 8% → 30% | Restore 5 energy when chopping |
| **Nature's Endurance** | Lv.60 | Always Active | Permanent +10% movement speed |

### Building Abilities
| Ability | Unlock | Base Chance | Effect |
|---------|--------|-------------|--------|
| **Architect's Focus** | Lv.15 | 10% → 40% | +100% building XP (double) |
| **Steady Hands** | Lv.35 | 8% → 30% | Pause stamina depletion for 30s |
| **Master Builder** | Lv.60 | Always Active | Permanent +10% max stamina |

### Gathering Abilities
| Ability | Unlock | Base Chance | Effect |
|---------|--------|-------------|--------|
| **Forager's Intuition** | Lv.15 | 10% → 40% | +50% gathering XP |
| **Nature's Gift** | Lv.35 | 6% → 25% | Restore 3 hunger and 3 thirst |
| **Survivalist** | Lv.60 | Always Active | Permanent -15% hunger/thirst depletion rate |

### Chance Scaling

Tier 1 and Tier 2 abilities have trigger chances that scale with your level:
- Chance starts at **base chance** when unlocked
- Increases by **+0.3-0.5%** per level above unlock
- Caps at **max chance** (shown as → value above)

Tier 3 abilities are **always active** once you reach level 60 in that profession.

## Metabolism Integration

The leveling system integrates with metabolism:
- **Well-Fed Bonus**: +25% XP when all stats are above 80%
- **Starving Penalty**: -50% XP when any stat is below 20%

## Panel Display

The `/ll main` panel shows:
- Current profession levels and XP
- Unlocked passive abilities with trigger chances
- Total XP earned across all professions

<br/>

# Commands

## Player Commands

Commands available to all players:

| Command | Description |
|---------|-------------|
| `/ll` | Show all available commands |
| `/ll main` | Toggle the Living Lands panel (shows metabolism, professions, effects, abilities) |
| `/ll help` | Show help panel with mod info and configuration paths |

## Admin Commands

Commands requiring OP/admin status:

| Command | Description |
|---------|-------------|
| `/setlevel <profession> <level>` | Set your profession level (1-99) |

**Professions:** `combat`, `mining`, `building`, `logging`, `gathering`

**Example:** `/setlevel mining 50` - Sets Mining profession to level 50

### Permission System

Living Lands uses Hytale's native permission system:
- **Player commands** (`requiresOp: false`) - Available to all players in Adventure and Creative modes
- **Admin commands** (`requiresOp: true`) - Requires server OP status

To grant OP status to a player, use Hytale's server console or admin commands.

The `/ll main` panel displays:
- Current values and status labels
- Color-coded indicators (green = good, red = critical)
- Active buff list
- Warning messages for critical stats

<br/>

# Status Indicators

## Hunger Status

| Level | Status | Color |
|-------|--------|-------|
| 90-100 | Satiated | 🟢 Green |
| 70-89 | Well Fed | 🟢 Green |
| 50-69 | Peckish | 🟡 Yellow |
| 30-49 | Hungry | 🟡 Yellow |
| 20-29 | Very Hungry | 🟠 Gold |
| 1-19 | Starving | 🔴 Red |
| 0 | Critical | 🔴 Dark Red |

## Thirst Status

| Level | Status | Color |
|-------|--------|-------|
| 90-100 | Hydrated | 🔵 Aqua |
| 70-89 | Quenched | 🔵 Aqua |
| 50-69 | Slightly Thirsty | 🔵 Blue |
| 30-49 | Thirsty | 🔵 Blue |
| 20-29 | Very Thirsty | 🟠 Gold |
| 1-19 | Dehydrated | 🔴 Red |
| 0 | Critical | 🔴 Dark Red |

## Energy Status

| Level | Status | Color |
|-------|--------|-------|
| 90-100 | Energized | 🟢 Green |
| 70-89 | Rested | 🟢 Green |
| 50-69 | Tired | 🟡 Yellow |
| 30-49 | Fatigued | 🟡 Yellow |
| 20-29 | Very Tired | 🟠 Gold |
| 1-19 | Exhausted | 🔴 Red |
| 0 | Critical | 🔴 Dark Red |

<br/>

# Server Administration

## Installation

1. Download the latest `LivingLands-x.x.x.jar` from [Releases](https://github.com/MoshPitCodes/hytale-livinglands/releases)
2. Place the JAR in your Hytale server's `Mods/` directory
3. Restart the server
4. Configure modules in `LivingLands/modules.json`

## Build from Source

```bash
git clone https://github.com/MoshPitCodes/hytale-livinglands.git
cd hytale-livinglands
./gradlew build
# JAR located at build/libs/LivingLands-*.jar
```

## Requirements

| Requirement | Version |
|-------------|---------|
| Java | 25+ |
| Hytale Server | Latest |
| Gradle | 9.x (wrapper included) |

<br/>

# Modular Architecture

Living Lands uses a **modular plugin architecture**. Server administrators can enable or disable features independently.

## Module Configuration

On first run, `LivingLands/modules.json` is created:

```json
{
  "enabled": {
    "metabolism": true,
    "claims": false,
    "economy": false,
    "leveling": false,
    "groups": false,
    "traders": false
  }
}
```

## Available Modules

| Module | Description | Status | Dependencies |
|--------|-------------|--------|--------------|
| **metabolism** | Hunger, thirst, energy, buffs, debuffs | ✅ Complete | None |
| **leveling** | XP, professions, passive abilities | ✅ Complete | hud |
| **claims** | Land/plot claiming and protection | 📋 Planned | None |
| **economy** | Currency and transactions | 📋 Planned | None |
| **groups** | Clans/parties | 📋 Planned | None |
| **traders** | NPC merchants | 📋 Planned | economy |

**Note**: Enabling a module with dependencies automatically enables required modules.

## Logging Configuration

Living Lands uses Java's standard logging levels. To reduce console spam on production servers, configure log levels in your Hytale server's `config.json`:

```json
{
  "LogLevels": {
    "LivingLands": "WARNING"
  }
}
```

**Available Log Levels:**

| Level | Description |
|-------|-------------|
| `INFO` | Default - shows startup/shutdown messages only |
| `WARNING` | Errors and important warnings |
| `SEVERE` | Critical errors only |
| `FINE` | Debug - per-player events (buffs, consumables, etc.) |
| `OFF` | Disable all Living Lands logging |

**Note:** As of v2.3.4-beta, per-player operational logs (buff gained/lost, consumable detected, player connected/disconnected, ECS ready, HUD initialized) are logged at `FINE` level by default. Set log level to `FINE` if you need to debug player-specific issues.

## Directory Structure

```
LivingLands/
├── modules.json              # Module toggles
├── metabolism/
│   └── config.json           # Metabolism settings
├── claims/
│   └── config.json           # Claims settings
├── economy/
│   └── config.json           # Economy settings
└── playerdata/
    └── {uuid}.json           # Per-player data
```

<br/>

# Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Living Lands Plugin                          │
├─────────────────────────────────────────────────────────────────┤
│  Module Manager                                                  │
│  • Registration • Dependencies • Lifecycle                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │ Metabolism  │  │   Claims    │  │   Economy   │  ...         │
│  │   Module    │  │   Module    │  │   Module    │              │
│  │  ✅ Active  │  │  ⬚ Planned │  │  ⬚ Planned │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
│        │                                                         │
│        ▼                                                         │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              Metabolism Module Systems                   │    │
│  ├─────────────────────────────────────────────────────────┤    │
│  │  • MetabolismSystem - Core stat management               │    │
│  │  • DebuffsSystem - Low-stat penalties                    │    │
│  │  • BuffsSystem - High-stat bonuses                       │    │
│  │  • PoisonEffectsSystem - Consumable poison               │    │
│  │  • DebuffEffectsSystem - Native Hytale debuffs           │    │
│  │  • BuffEffectsSystem - Native Hytale food buffs          │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
├─────────────────────────────────────────────────────────────────┤
│  Shared Core Services                                            │
│  • PlayerRegistry • EventRegistry • Persistence                  │
└─────────────────────────────────────────────────────────────────┘
```

## System Interactions

| System | Purpose | Runs |
|--------|---------|------|
| **MetabolismSystem** | Stat depletion, activity detection | Every 1 second |
| **DebuffsSystem** | Apply penalties for low stats | Per player per tick |
| **BuffsSystem** | Apply bonuses for high stats | Per player per tick |
| **PoisonEffectsSystem** | Consumable poison drain | Every 1 second |
| **DebuffEffectsSystem** | Native debuff drain | Every 1 second |
| **FoodConsumptionProcessor** | Detect food/potion use | Batched, 10 players/50ms |

**Priority Rule**: Debuffs suppress buffs. If any debuff is active, all stat-based buffs are removed.

## Performance

The metabolism module is optimized for **O(n) linear scaling** with player count:

| Component | Complexity | Notes |
|-----------|------------|-------|
| Main tick loop | O(n) | Processes all players once per second |
| Per-player processing | O(1) | Constant time hash lookups |
| Effect detection | O(batch) | Batched processing (10 players/tick) |

**Batched Effect Detection**: Food consumption detection processes players in batches of 10 every 50ms, reducing CPU overhead on high-population servers while maintaining responsive detection.

<br/>

# Roadmap

| Feature | Status |
|---------|--------|
| Hunger/Thirst/Energy Systems | ✅ Complete |
| Food & Potion Consumption | ✅ Complete |
| Native Debuff Integration | ✅ Complete |
| Bed Sleep System | ✅ Complete |
| Buff System (High Stats) | ✅ Complete |
| Enhanced Debuff System | ✅ Complete |
| Player Feedback Messages | ✅ Complete |
| Modular Architecture | ✅ Complete |
| Enhanced HUD with Effects | ✅ Complete |
| Leveling System | ✅ Complete |
| Profession XP | ✅ Complete |
| Passive Abilities | ✅ Complete |
| Economy System | 📋 Planned |
| Trader NPCs | 📋 Planned |
| Land Claims | 📋 Planned |
| Admin Commands | 📋 Planned |

<br/>

# Credits

- **Author**: [MoshPitCodes](https://github.com/MoshPitCodes)
- **Version**: 2.6.0-beta
- **License**: Apache-2.0

### Resources
- [Hytale Official](https://hytale.com)
- [Issues & Feedback](https://github.com/MoshPitCodes/hytale-livinglands/issues)

<br/>

<!-- DO NOT TOUCH THIS SECTION#2: START -->

<br/>

<p align="center"><img src="https://raw.githubusercontent.com/catppuccin/catppuccin/main/assets/footers/gray0_ctp_on_line.svg?sanitize=true" /></p>

<!-- end of page, send back to the top -->

<div align="right">
  <a href="#readme">Back to the Top</a>
</div>
<!-- DO NOT TOUCH THIS SECTION#2: END -->
