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

## Food Buffs (Detected)

The mod detects when you consume food with buff effects:

| Buff Pattern | Type | Detected |
|--------------|------|----------|
| `Food_Health_Boost_*` | Defense buff | ✅ |
| `Food_Stamina_Boost_*` | Stamina buff | ✅ |
| `Meat_Buff_*` | Strength buff | ✅ |
| `FruitVeggie_Buff_*` | Vitality buff | ✅ |

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

| Profession | XP Source | Passive Abilities |
|------------|-----------|-------------------|
| **Combat** | Killing mobs | Critical Strike, Lifesteal |
| **Mining** | Breaking ores | Double Ore, Lucky Strike |
| **Logging** | Chopping trees | Efficient Chopping, Bark Collector |
| **Building** | Placing blocks | Material Saver |
| **Gathering** | Harvesting plants | Double Harvest, Rare Find |

## Passive Abilities

Passive abilities unlock at certain profession levels and have a chance to trigger:

| Ability | Profession | Effect |
|---------|------------|--------|
| **Critical Strike** | Combat | 1.5x damage on hit |
| **Lifesteal** | Combat | Restore 10% of damage dealt as health |
| **Double Ore** | Mining | Double ore drops |
| **Lucky Strike** | Mining | Chance to find rare gems |
| **Efficient Chopping** | Logging | Instantly break entire tree |
| **Bark Collector** | Logging | Bonus bark/planks when logging |
| **Material Saver** | Building | Don't consume block when placing |
| **Double Harvest** | Gathering | Double gathered resources |
| **Rare Find** | Gathering | Find rare items while gathering |

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

| Command | Description |
|---------|-------------|
| `/stats` | View your current hunger, thirst, energy, and any active buffs |
| `/ll main` | Toggle the Living Lands panel (shows metabolism, professions, effects, abilities) |
| `/skillgui` | Toggle XP gain notifications |

The `/stats` command displays:
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
- **Version**: 2.3.1-beta
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
