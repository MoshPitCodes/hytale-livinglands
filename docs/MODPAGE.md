# Living Lands - Hytale RPG Survival Mod

**Living Lands** transforms Hytale into an immersive survival experience. Manage your character's **hunger**, **thirst**, and **energy** while exploring the world. The mod integrates seamlessly with vanilla items - no new blocks or complicated recipes, just pure survival gameplay.

**Version 2.6.0** introduces the **complete Claims Module** for protecting your builds with plot-based land claims, trust management, and automatic hostile NPC protection. This release also includes **balance adjustments** making survival 25% more challenging with faster depletion rates, stronger debuffs, and reduced buff bonuses.

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

### Land Claims System

Protect your builds with plot-based land claims:

- **Plot Claims** - Claim 16x16 block areas to protect your builds
- **Trust System** - Grant other players building access to your claims
- **Claim Flags** - Toggle PvP, explosions, mob griefing, and NPC protection per-claim
- **NPC Protection** - Hostile NPCs automatically despawn in protected claims
- **Map Integration** - Visual markers show your claims on the world map
- **Claim UI** - Easy-to-use interface for managing all your claims

### Modular Architecture

- **Toggle Features** - Enable/disable individual modules via `modules.json`
- **Per-Module Configuration** - Each module has its own config directory
- **Automatic Dependencies** - Enabling a module auto-enables its requirements
- **Extensible Design** - Placeholder modules for Economy, Groups, and Traders

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

Five professions with XP and 3 passive abilities each (15 total):

| Profession | XP Source | Abilities (Lv.15 / Lv.35 / Lv.60) |
|------------|-----------|-----------------------------------|
| **Combat** | Killing mobs | Adrenaline Rush, Warrior's Resilience, Battle Hardened |
| **Mining** | Breaking ores | Prospector's Eye, Efficient Extraction, Iron Constitution |
| **Logging** | Chopping trees | Lumberjack's Vigor, Forest's Blessing, Nature's Endurance |
| **Building** | Placing blocks | Architect's Focus, Steady Hands, Master Builder |
| **Gathering** | Harvesting | Forager's Intuition, Nature's Gift, Survivalist |

### Passive Abilities

Each profession has 3 abilities across 3 tiers:

**Tier 1 (Lv.15)** - XP Boosters and Timed Buffs:
| Ability | Profession | Chance | Effect |
|---------|------------|--------|--------|
| **Adrenaline Rush** | Combat | 8-35% | +20% speed for 10s after kill |
| **Prospector's Eye** | Mining | 10-40% | +50% mining XP |
| **Lumberjack's Vigor** | Logging | 10-40% | +50% logging XP |
| **Architect's Focus** | Building | 10-40% | +100% building XP (double) |
| **Forager's Intuition** | Gathering | 10-40% | +50% gathering XP |

**Tier 2 (Lv.35)** - Resource Management:
| Ability | Profession | Chance | Effect |
|---------|------------|--------|--------|
| **Warrior's Resilience** | Combat | 6-25% | Restore 15% max health after kill |
| **Efficient Extraction** | Mining | 8-30% | Pause hunger for 30s |
| **Forest's Blessing** | Logging | 8-30% | Restore 5 energy |
| **Steady Hands** | Building | 8-30% | Pause stamina for 30s |
| **Nature's Gift** | Gathering | 6-25% | Restore 3 hunger and 3 thirst |

**Tier 3 (Lv.60)** - Permanent Passives (Always Active):
| Ability | Profession | Effect |
|---------|------------|--------|
| **Battle Hardened** | Combat | +10% max health |
| **Iron Constitution** | Mining | +15% max stamina |
| **Nature's Endurance** | Logging | +10% movement speed |
| **Master Builder** | Building | +10% max stamina |
| **Survivalist** | Gathering | -15% hunger/thirst depletion |

Trigger chances increase as you level up beyond the unlock level.

### Death Penalty

When you die:
- **85% XP Loss** in 2 randomly selected professions
- **Level Protection** - You can never lose a level from death (minimum XP = 0 for current level)
- **Chat Feedback** - Shows which professions were affected and how much XP was lost

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
| `/ll buffs` | View active metabolism buffs |
| `/ll debuffs` | View active metabolism debuffs |
| `/ll settings` | Customize HUD preferences |
| `/ll stats` | View detailed metabolism statistics |

### Claims Commands (All Players)

| Command | Description |
|---------|-------------|
| `/claims create` | Create a new claim at your location |
| `/claims delete` | Delete your current claim |
| `/claims trust <player>` | Allow a player to build in your claim |
| `/claims untrust <player>` | Remove a player's build access |
| `/claims flags` | Toggle claim protection flags |
| `/claims info` | View information about the claim you're standing in |

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
| Land Claims | ✅ Complete |
| HUD Preferences | ✅ Complete |
| Economy System | 📋 Planned |
| Trader NPCs | 📋 Planned |
| Groups/Guilds | 📋 Planned |
| Random Encounters | 📋 Planned |
| Admin Commands | 📋 Planned |

---

## Links

- **Source Code**: [GitHub](https://github.com/MoshPitCodes/hytale-livinglands)
- **Issues**: [GitHub Issues](https://github.com/MoshPitCodes/hytale-livinglands/issues)
- **License**: Apache-2.0
- **Author**: MoshPitCodes
