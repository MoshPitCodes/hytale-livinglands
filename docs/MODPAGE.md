# Living Lands - Hytale RPG Survival Mod

**Living Lands** transforms Hytale into an immersive survival experience. Manage your character's **hunger**, **thirst**, and **energy** while exploring the world. The mod integrates seamlessly with vanilla items - no new blocks or complicated recipes, just pure survival gameplay.

**Version 2.3.4** features an **enhanced HUD** with semi-transparent backdrops, real-time buff/debuff indicators, a **buff/debuff system** that rewards well-maintained stats and penalizes neglect, **comprehensive consumable detection** for all vanilla items, plus a **modular architecture** - enable only the features you want!

---

## Features

### Buff System

- **Speed Buff** - Increased movement speed when Energy >= 90%
- **Defense Buff** - Increased max health when Hunger >= 90%
- **Stamina Buff** - Increased max stamina when Thirst >= 90%
- **Hysteresis** - Buffs activate at 90%, deactivate at 80% to prevent flickering
- **Native Detection** - Detects Hytale food buffs (Health_Boost, Stamina_Boost, Meat_Buff, FruitVeggie_Buff)

### Debuff System

- **Parched State** - Gradual speed/stamina reduction when Thirst < 30 (up to 55% at 0)
- **Tired State** - Gradual speed reduction + increased stamina consumption when Energy < 30 (up to 40% speed reduction + 1.5x stamina at 0)
- **Starvation** - Escalating damage when Hunger = 0
- **Dehydration** - Damage when Thirst = 0
- **Exhaustion** - Rapid stamina drain when Energy = 0
- **Player Feedback** - Colored chat messages when entering/exiting debuff states

### Modular Architecture

- **Toggle Features** - Enable/disable individual modules via `modules.json`
- **Per-Module Configuration** - Each module has its own config directory
- **Automatic Dependencies** - Enabling a module auto-enables its requirements
- **Future-Ready** - Placeholder modules for Claims, Economy, Groups, Leveling, and Traders

### Hunger System

- Depletes naturally over time (1 point/60 seconds)
- Activities like sprinting (2x), swimming (1.5x), and combat (1.5x) drain it faster
- Eat vanilla foods to restore - cooked meats and prepared meals give the best restoration
- Below 20: Starvation status with escalating damage

### Thirst System

- Depletes faster than hunger (1 point/45 seconds)
- Drink potions, water, or milk to stay hydrated
- Below 30: Parched status with movement penalties
- At 0: Dehydration damage

### Energy System

- Slow depletion (1 point/90 seconds)
- Sleep in beds to restore 50 energy (respects day/night cycle)
- Stamina potions restore energy quickly
- Below 30: Tired status with speed reduction and increased stamina consumption
- At 0: Exhaustion with rapid stamina drain

### Native Debuff Integration

- **Poison Effects** - Poison (T1-T3) drains hunger, thirst, and energy
- **Burn Effects** - Burn, Lava Burn, Flame Staff cause severe thirst drain
- **Stun Effects** - Stun, Bomb Explode cause high energy drain
- **Freeze Effect** - Causes high energy drain (hypothermia)
- **Root Effect** - Causes moderate energy drain
- **Slow Effects** - Cause low energy drain (fatigue)

### Real-time HUD

- Custom on-screen display shows all three stats with text-based progress bars
- **Semi-Transparent Backdrops** - Clean dark backgrounds for better readability
- **Active Effects Display** - Buffs shown in violet, debuffs shown in red with individual backdrops
- **Dynamic Visibility** - Buff/debuff backdrops only appear when effects are active
- **XP Notifications** - XP gain notifications appear to the right of metabolism bars
- Color-coded warnings when stats get low
- Buff indicators: `[+] Well Fed`, `[+] Hydrated`, `[+] Energized`
- Debuff indicators: `[-] Starving`, `[-] Dehydrated`, `[-] Exhausted`

### Leveling System

Five professions with XP and passive abilities:

| Profession | XP Source | Abilities |
|------------|-----------|-----------|
| **Combat** | Killing mobs | Critical Strike, Lifesteal |
| **Mining** | Breaking ores | Double Ore, Lucky Strike |
| **Logging** | Chopping trees | Efficient Chopping, Bark Collector |
| **Building** | Placing blocks | Material Saver |
| **Gathering** | Harvesting | Double Harvest, Rare Find |

### Passive Abilities

Unlockable abilities with trigger chances:
- **Critical Strike** - 1.5x damage on hit
- **Lifesteal** - Heal 10% of damage dealt
- **Double Ore** - Double ore drops
- **Lucky Strike** - Find rare gems
- **Efficient Chopping** - Instant tree felling
- **Bark Collector** - Bonus wood materials
- **Material Saver** - Don't consume blocks
- **Double Harvest** - Double gathered resources
- **Rare Find** - Find rare items

---

## Consumables

### Foods (Restore Hunger)

| Tier | Examples | Hunger Restored |
|------|----------|-----------------|
| Low | Raw meats, Eggs, Vegetables | 8-18 |
| Medium | Bread, Cheese, Cooked veggies | 20-35 |
| High | Cooked meats, Kebabs, Salads | 40-50 |
| Premium | Pies, Meat dishes | 55-65 |

Items can be configured in server config. This way you should be able to include modded assets.

### Drinks (Restore Thirst)

| Type | Examples | Thirst Restored |
|------|----------|-----------------|
| Potions | Health, Mana, Regen | 20-45 |
| Stamina | Stamina potions | 25-50 (+Energy) |
| Water/Milk | Buckets | 50-60 |

Items can be configured in server config. This way you should be able to include modded assets.

### Special Items

- **Salads** - Restore both hunger AND thirst
- **Stamina Potions** - Restore thirst AND energy
- **Milk** - Restores hunger AND thirst
- **Health Potions** - Now also restore slight hunger and high thirst
- **Mana/Signature Potions** - Now also restore slight energy and high thirst

---

## Configuration

### Module Configuration (`modules.json`)

```json
{
  "enabled": {
    "metabolism": true,
    "claims": false,
    "economy": false,
    "groups": false,
    "leveling": false,
    "traders": false
  }
}
```

Each module can be toggled independently. Dependent modules are auto-enabled when required.

### Metabolism Configuration (`metabolism/config.json`)

The metabolism module has extensive configuration options:
- Stat depletion rates
- Activity multipliers
- Debuff thresholds and severity
- Buff activation thresholds and multipliers
- Food/drink restoration values

---

## Commands

### Player Commands (All Players)

| Command | Description |
|---------|-------------|
| `/ll` | Show all available commands |
| `/ll main` | Toggle the Living Lands panel (metabolism, professions, effects, abilities) |
| `/ll help` | Show help panel with mod info and configuration paths |

### Admin Commands (OP Required)

| Command | Description |
|---------|-------------|
| `/setlevel <profession> <level>` | Set a player's profession level (1-99) |

**Example:** `/setlevel combat 25`

### Permissions

Living Lands uses Hytale's native permission system. Admin commands require OP status on the server.

---

## Installation

1. Download the JAR file
2. Place in your Hytale server's `Mods/` folder
3. Restart the server
4. Configure modules in `LivingLands/modules.json`
5. Players automatically receive the HUD when joining

### Requirements

- Java 25+
- Hytale Server (latest version)

---

## Performance

Living Lands is optimized for servers of all sizes:

- **O(n) Linear Scaling** - Performance scales linearly with player count
- **Batched Processing** - Effect detection processes 10 players per tick
- **Thread-Safe** - All ECS access runs on the WorldThread
- **Efficient Data Structures** - ConcurrentHashMap for all player data

---

## Roadmap

| Feature | Status |
|---------|--------|
| Modular Architecture | ✅ Complete |
| Hunger System | ✅ Complete |
| Thirst System | ✅ Complete |
| Energy System | ✅ Complete |
| Food Consumption | ✅ Complete |
| Potion Effects | ✅ Complete |
| Bed Rest | ✅ Complete |
| Buff System | ✅ Complete |
| Debuff System | ✅ Complete |
| Native Buff Detection | ✅ Complete |
| Native Debuff Integration | ✅ Complete |
| Player Feedback Messages | ✅ Complete |
| Enhanced HUD with Effects | ✅ Complete |
| Leveling System | ✅ Complete |
| Profession XP | ✅ Complete |
| Passive Abilities | ✅ Complete |
| Economy System | 📋 Planned |
| Trader NPCs | 📋 Planned |
| Land Claims | 📋 Planned |
| Groups/Guilds | 📋 Planned |
| Random Encounters | 📋 Planned |
| Admin Commands | 📋 Planned |

---

## Links

- **Source Code**: [GitHub](https://github.com/MoshPitCodes/hytale-livinglands)
- **Issues**: [GitHub Issues](https://github.com/MoshPitCodes/hytale-livinglands/issues)
- **License**: Apache-2.0
- **Author**: MoshPitCodes
