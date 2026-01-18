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

# 🗃️ Overview

**Living Lands** is an immersive RPG survival mod for Hytale that transforms the gameplay experience by introducing realistic survival mechanics. Manage your character's hunger, thirst, and energy while exploring the world. Consume food and drinks to stay alive, and rest to recover your strength.

The mod seamlessly integrates with vanilla Hytale items, adding depth without breaking the core gameplay experience.

<br/>

## ✨ Features

| Feature | Description |
|---------|-------------|
| **🍖 Hunger System** | Your character gets hungry over time. Physical activities like sprinting and combat drain hunger faster. Eat food to restore your hunger levels. |
| **💧 Thirst System** | Stay hydrated! Thirst depletes faster than hunger. Drink potions, water, or milk to quench your thirst. |
| **⚡ Energy System** | Energy drains slowly throughout the day. Stamina potions can help restore energy quickly. |
| **🛏️ Bed Rest** | Sleep in a bed to restore energy. Respects the game's sleep schedule - you can only rest during valid sleep hours. |
| **🍽️ Food Consumption** | All vanilla Hytale foods now restore hunger. Cooked meats restore more than raw foods. Kebabs, pies, and prepared meals provide the best restoration. |
| **🧪 Potion Effects** | Potions restore thirst when consumed. Stamina potions provide both thirst and energy restoration. |
| **📊 Status Feedback** | Visual status indicators show your current state: Satiated, Hungry, Starving, Hydrated, Dehydrated, Energized, Exhausted. |

<br/>

## 📓 Survival Mechanics

### Hunger
Your hunger level ranges from 0 to 100. It naturally depletes over time:
- **Base rate**: 1 point every 60 seconds
- **Sprinting**: Depletes 2x faster
- **Swimming**: Depletes 1.5x faster
- **Combat**: Depletes 1.5x faster

When hunger drops below 20, you enter **Starvation** status with movement penalties and gradual health drain.

### Thirst
Thirst also ranges from 0 to 100 but depletes faster than hunger:
- **Base rate**: 1 point every 45 seconds
- Same activity multipliers as hunger

When thirst drops below 20, you become **Dehydrated** with similar penalties to starvation.

### Energy
Energy represents your character's overall stamina:
- **Base rate**: 1 point every 90 seconds (slowest depletion)
- Stamina potions can restore energy quickly
- **Sleep in a bed** to restore 50 energy (respects game's sleep schedule)

When energy drops below 20, you become **Exhausted**.

### Sleeping
Rest in a bed to restore your energy:
- **Energy restored**: 50 per sleep
- **Cooldown**: 5 seconds between sleep attempts
- **Sleep schedule**: Only works during valid sleep hours (follows game's day/night cycle)
- You'll see a message if you try to sleep outside of sleep hours

<br/>

## 🍎 Consumables

### Foods (Restore Hunger)
| Tier | Examples | Hunger Restored |
|------|----------|-----------------|
| **Low** | Raw meats, Eggs, Vegetables | 8-18 |
| **Medium** | Bread, Cheese, Cooked vegetables | 20-35 |
| **High** | Cooked meats, Kebabs, Salads | 40-50 |
| **Premium** | Pies, Meat dishes | 55-65 |

### Drinks (Restore Thirst)
| Type | Examples | Thirst Restored |
|------|----------|-----------------|
| **Potions** | Health, Mana, Regen potions | 20-45 |
| **Stamina** | Stamina potions | 25-50 (+Energy) |
| **Water** | Bucket of water | 60 |
| **Milk** | Bucket of milk | 50 (+Hunger) |

### Special Items
- **Salads**: Restore both hunger and thirst
- **Stamina Potions**: Restore thirst AND energy
- **Milk**: Restores both hunger and thirst

<br/>

# 🚀 Quick Start

## For Players

### Checking Your Stats
Use this command to monitor all your survival stats at once:

```
/stats    - View hunger, thirst, and energy levels
```

The command displays:
- **Hunger**: Current level and status (Satiated, Hungry, Starving, etc.)
- **Thirst**: Current level and status (Hydrated, Thirsty, Dehydrated, etc.)
- **Energy**: Current level and status (Energized, Tired, Exhausted, etc.)
- **Warnings**: Critical alerts when any stat is dangerously low

### Staying Alive
1. **Eat regularly** - Consume food items to restore hunger
2. **Drink often** - Use potions or find water/milk to stay hydrated
3. **Watch for warnings** - Red warning messages appear when stats are critically low
4. **Manage activities** - Sprinting and combat drain stats faster

### Status Indicators

**Hunger Status:**
| Level | Status | Color |
|-------|--------|-------|
| 90-100 | Satiated | 🟢 Green |
| 70-89 | Well Fed | 🟢 Green |
| 50-69 | Peckish | 🟡 Yellow |
| 30-49 | Hungry | 🟡 Yellow |
| 20-29 | Very Hungry | 🟠 Gold |
| 1-19 | Starving | 🔴 Red |
| 0 | Critical | 🔴 Red |

**Thirst Status:**
| Level | Status | Color |
|-------|--------|-------|
| 90-100 | Hydrated | 🔵 Aqua |
| 70-89 | Quenched | 🔵 Aqua |
| 50-69 | Slightly Thirsty | 🔵 Blue |
| 30-49 | Thirsty | 🔵 Blue |
| 20-29 | Very Thirsty | 🟠 Gold |
| 1-19 | Dehydrated | 🔴 Red |
| 0 | Critical | 🔴 Red |

**Energy Status:**
| Level | Status | Color |
|-------|--------|-------|
| 90-100 | Energized | 🟢 Green |
| 70-89 | Rested | 🟢 Green |
| 50-69 | Tired | 🟡 Yellow |
| 30-49 | Fatigued | 🟡 Yellow |
| 20-29 | Very Tired | 🟠 Gold |
| 1-19 | Exhausted | 🔴 Red |
| 0 | Critical | 🔴 Red |

<br/>

## For Server Admins

### Installation
1. Download the latest `LivingLands-x.x.x.jar` from releases
2. Place the JAR file in your Hytale server's `Mods/` directory
3. Restart the server
4. The mod will automatically initialize with default settings

### Build from Source
```bash
# Clone the repository
git clone https://github.com/MoshPitCodes/hytale-livinglands.git
cd hytale-livinglands

# Build the mod
./gradlew build

# Find the JAR in build/libs/
```

### Requirements
- **Java**: 25+
- **Hytale Server**: Latest version
- **Gradle**: 9.x (included via wrapper)

### Configuration

The mod uses sensible defaults that work well for most servers:

| Setting | Default | Description |
|---------|---------|-------------|
| Hunger Depletion | 60s/point | How often hunger decreases |
| Thirst Depletion | 45s/point | How often thirst decreases |
| Energy Depletion | 90s/point | How often energy decreases |
| Critical Threshold | 20 | Level where debuffs begin |
| Initial Stats | 100 | Starting values for new players |

### Activity Multipliers

Stats deplete faster during activities:

| Activity | Multiplier | Effect |
|----------|------------|--------|
| Idle/Walking | 1.0x | Normal rate |
| Sprinting | 2.0x | Double depletion |
| Swimming | 1.5x | 50% faster |
| Combat | 1.5x | 50% faster |

### Server Logs

The mod logs important events:
- Player metabolism initialization on join
- Player metabolism cleanup on disconnect
- Item consumption events with restoration amounts
- Errors and warnings

### Technical Details

**Consumption Detection**: The mod uses Hytale's `Transaction` API to accurately detect item consumption:
- `MoveTransaction` (drops, splits, inventory moves) → Ignored
- `SlotTransaction` with quantity decrease → Detected as consumption

This ensures only actual food/drink consumption triggers stat restoration, not inventory management actions.

<br/>

# 📐 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Living Lands Plugin                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Commands  │  │  Listeners  │  │  Metabolism System  │  │
│  ├─────────────┤  ├─────────────┤  ├─────────────────────┤  │
│  │ /stats      │  │ Player      │  │ Tick-based updates  │  │
│  │             │  │ Connect/    │  │ Activity tracking   │  │
│  │             │  │ Disconnect  │  │ Stat depletion      │  │
│  │             │  │             │  │ Stat restoration    │  │
│  │             │  │ Item        │  │                     │  │
│  │             │  │ Consumption │  │                     │  │
│  │             │  │             │  │                     │  │
│  │             │  │ Bed         │  │                     │  │
│  │             │  │ Interaction │  │                     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│         │                │                    │              │
│         └────────────────┼────────────────────┘              │
│                          │                                   │
│                          ▼                                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Player Metabolism Data                    │  │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐               │  │
│  │  │ Hunger  │  │ Thirst  │  │ Energy  │               │  │
│  │  │  0-100  │  │  0-100  │  │  0-100  │               │  │
│  │  └─────────┘  └─────────┘  └─────────┘               │  │
│  └───────────────────────────────────────────────────────┘  │
│                          │                                   │
│                          ▼                                   │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Consumable Registry                       │  │
│  │  Foods → Hunger    Drinks → Thirst    Stamina → Energy │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

<br/>

# 🛣️ Roadmap

| Feature | Status |
|---------|--------|
| Hunger System | ✅ Complete |
| Thirst System | ✅ Complete |
| Energy System | ✅ Complete |
| Food Consumption | ✅ Complete |
| Potion Effects | ✅ Complete |
| Bed Rest (Energy) | ✅ Complete |
| Economy System | 📋 Planned |
| Trader NPCs | 📋 Planned |
| Land Claims | 📋 Planned |
| Admin Commands | 📋 Planned |

<br/>

# 👥 Credits

- **Author**: [MoshPitCodes](https://github.com/MoshPitCodes)
- **Version**: 1.0.0-SNAPSHOT
- **License**: Apache-2.0

### Resources
- [Hytale Official](https://hytale.com): Game information
- [Hytale Server API](https://github.com/hypixel): Server modding documentation

<br/>

<!-- DO NOT TOUCH THIS SECTION#2: START -->

<br/>

<p align="center"><img src="https://raw.githubusercontent.com/catppuccin/catppuccin/main/assets/footers/gray0_ctp_on_line.svg?sanitize=true" /></p>

<!-- end of page, send back to the top -->

<div align="right">
  <a href="#readme">Back to the Top</a>
</div>
<!-- DO NOT TOUCH THIS SECTION#2: END -->
