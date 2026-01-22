# Living Lands - Changelog

All notable changes to Living Lands for players and server administrators.

---

## Version 2.6.0-beta - January 2026

### New Features

#### Land Claims System
Protect your builds with the new claiming system:

- **Create Claims** - Use `/claims create` to claim a 16x16 block plot
- **Trust Friends** - Use `/claims trust <player>` to let friends build in your claim
- **Automatic Protection** - Hostile NPCs are automatically removed from your claimed land
- **Claim Flags** - Toggle PvP, explosions, mob griefing, and NPC protection per-claim
- **Map Markers** - See your claims on the world map
- **Claim Management UI** - Easy-to-use interface for managing your claims

#### New Commands
| Command | Description |
|---------|-------------|
| `/claims create` | Create a new claim |
| `/claims delete` | Delete your current claim |
| `/claims trust <player>` | Allow a player to build |
| `/claims untrust <player>` | Remove building access |
| `/claims flags` | Toggle protection settings |
| `/ll buffs` | View your active buffs |
| `/ll debuffs` | View your active debuffs |
| `/ll settings` | Customize your HUD |
| `/ll stats` | View detailed statistics |

#### HUD Customization
- Customize which HUD elements are visible
- Settings persist across sessions

### Balance Changes

#### Survival is Harder
- **Faster Depletion** - Hunger, thirst, and energy now deplete 25% faster
- **Stronger Debuffs** - Low-stat penalties are 10% more severe
- **Weaker Buffs** - High-stat bonuses reduced by 12%

**New Depletion Rates:**
| Stat | Old Rate | New Rate |
|------|----------|----------|
| Hunger | 50 seconds/point | 40 seconds/point |
| Thirst | 37.5 seconds/point | 30 seconds/point |
| Energy | 75 seconds/point | 60 seconds/point |

### Fixed

- **Server Stability** - Fixed several crashes related to claim protection
- **Memory Optimization** - Fixed memory leaks when players disconnect
- **Thread Safety** - Fixed rare crashes from concurrent operations

---

## Version 2.4.1-beta - January 2026

### Bug Fixes

#### Stats Display Correctly on Login
- Fixed an issue where health and stamina could show incorrect values after logging in
- Old buff bonuses from previous sessions no longer persist incorrectly
- Stats now properly reflect your actual ability unlocks and metabolism state

#### All Tier 2 Abilities Now Working
- **Warrior's Resilience** (Combat Lv.35) - Now properly heals you after kills
- **Survivalist** (Gathering Lv.60) - Hunger and thirst now drain 15% slower as intended
- All profession activities now correctly trigger their Tier 2 abilities

### Technical Improvements
- Improved stat modifier cleanup on player login
- Better synchronization between mod data and game saves

---

## Version 2.4.0-beta - January 2026

### New Features

#### Completely Redesigned Passive Abilities
The ability system has been completely overhauled with harder-to-obtain, more impactful abilities:

**Each profession now has exactly 3 abilities:**

| Level | Type | Description |
|-------|------|-------------|
| **15** | XP Booster | Chance to gain bonus XP from profession activities |
| **35** | Timed Buff | Powerful temporary effect when triggered |
| **60** | Permanent | Always-active stat bonus once unlocked |

**Combat Abilities:**
- **Adrenaline Rush** (Lv.15) - After a kill, chance to gain +20% speed for 10 seconds
- **Warrior's Resilience** (Lv.35) - After a kill, chance to restore 15% of max health
- **Battle Hardened** (Lv.60) - Permanent +10% max health

**Mining Abilities:**
- **Prospector's Eye** (Lv.15) - Chance for +50% XP when mining ores
- **Efficient Extraction** (Lv.35) - Chance to pause hunger drain for 30 seconds
- **Iron Constitution** (Lv.60) - Permanent +15% max stamina

**Logging Abilities:**
- **Lumberjack's Vigor** (Lv.15) - Chance for +50% XP when chopping wood
- **Forest's Blessing** (Lv.35) - Chance to restore 5 energy
- **Nature's Endurance** (Lv.60) - Permanent +10% movement speed

**Building Abilities:**
- **Architect's Focus** (Lv.15) - Chance for +100% XP when building
- **Steady Hands** (Lv.35) - Chance to pause stamina drain for 30 seconds
- **Master Builder** (Lv.60) - Permanent +10% max stamina

**Gathering Abilities:**
- **Forager's Intuition** (Lv.15) - Chance for +50% XP when gathering
- **Nature's Gift** (Lv.35) - Chance to restore 3 hunger and 3 thirst
- **Survivalist** (Lv.60) - Permanent -15% hunger/thirst drain rate

#### Active Ability Buffs in HUD
- When abilities trigger, you'll see **cyan buff indicators** in your HUD
- Countdown timers show how long temporary effects last
- Up to 3 ability buffs can display at once

#### Death Penalty
- **XP Loss on Death** - When you die, you lose 85% of your current XP in 2 random professions
- Your level is protected - you can't lose a level from death (minimum XP stays at 0 for your current level)
- Chat messages show exactly which professions were affected and how much XP was lost

#### Ability Feedback
When abilities trigger, you'll receive:
- A colored chat message describing the effect
- A sound effect for audio confirmation

### Fixed

- **Version Display** - The in-game version now correctly matches the CurseForge release version
- **Thread Safety** - Fixed potential issues with concurrent player actions
- **Speed Flickering** - Fixed rare flickering when ability speed buffs stack with metabolism buffs

---

## Version 2.3.4-beta - January 2026

### Changed

#### Reduced Console Spam
- **Quieter Logs** - Server consoles are now much cleaner with reduced logging
- **Server Admins** - Set `"LivingLands": "WARNING"` in server config.json for minimal logs
- **Debug Mode** - Set `"LivingLands": "FINE"` in server config.json for detailed debugging

#### Simplified Commands
- **`/ll`** - Shows all available subcommands
- **`/ll main`** - Toggles the Living Lands stats panel
- **`/ll help`** - Shows mod information and config file paths
- **`/stats` Removed** - Use `/ll main` instead (shows same information in the panel)
- **`/skillgui` Removed** - Use `/ll main` instead (skills shown in the panel)

#### Visual Improvements
- **HUD Backdrop** - Metabolism stats now have a semi-transparent dark background for better readability
- **Buff/Debuff Backdrops** - Each buff (purple) and debuff (red) has its own backdrop
- **Cleaner Display** - Buff/debuff backdrops only appear when effects are active
- **Aligned Bars** - Progress bars now display consistently regardless of stat value

---

## Version 2.3.3-beta - January 2026

### New Features

#### Comprehensive Consumable Detection
All vanilla Hytale consumables are now properly detected:
- **Water Mugs & Buckets** - Drinking water now restores thirst
- **Milk Buckets** - Drinking milk now restores hunger and thirst
- **Stamina Drinks** - Stamina restoration drinks properly detected
- **Health/Stamina Regen Foods** - Foods with regen effects properly detected
- **Morph Potions** - Transformation potions (Dog, Frog, Mosshorn, etc.) detected

### Fixed

#### Potion Detection Reliability
- **More Reliable Detection** - Potions are now detected more consistently
- **Rapid Consumption** - You can now drink potions quickly without missing detection

### Changed

#### Commands
- **Stats Command** - Changed `/stats` to `/ll stats` for consistency

---

## Version 2.3.2-beta - January 2026

### Fixed

#### Speed Flickering
- **Fixed Speed Flickering** - Movement speed no longer flickers when buffs and debuffs are active
- The speed system now uses centralized management to prevent conflicts

---

## Version 2.3.1-beta - January 2026

### Fixed

#### Death Detection
- **Metabolism Reset on Death** - Fixed metabolism not resetting when players die and respawn
- Metabolism stats now properly reset to initial values immediately when you die

#### Mining/Logging XP Exploit
- **Player-Placed Block Detection** - Fixed exploit where players could gain XP by breaking blocks they placed themselves
- Breaking player-placed ores, rocks, wood, or leaves no longer awards XP
- Only naturally-generated blocks award XP

#### Player-Placed Block Tracking
- **Persistent Tracking** - Player-placed blocks are now tracked across server restarts
- Your placed blocks will still be tracked after the server reboots

---

## Version 2.3.0-beta - January 2026

### New Features

#### Enhanced HUD with Active Effects
- **Vertically Aligned Bars** - Metabolism bars are now cleanly aligned with labels on the left
- **Active Effects Display** - Buffs and debuffs now appear below your metabolism bars
- **Color-Coded Effects** - Buffs display in violet, debuffs display in red for easy identification
- **Panel Title** - The main Living Lands panel now has proper title and section headers
- **XP Notifications** - XP gain notifications now appear to the right of the metabolism bars

#### Buff/Debuff Indicators
When your stats are high (80%+), you'll see violet buff indicators:
- `[+] Well Fed` - Hunger >= 80%
- `[+] Hydrated` - Thirst >= 80%
- `[+] Energized` - Energy >= 80%

When your stats are low (20% or below), you'll see red debuff indicators:
- `[-] Starving` - Hunger <= 20%
- `[-] Dehydrated` - Thirst <= 20%
- `[-] Exhausted` - Energy <= 20%

#### Passive Abilities Display
The `/ll main` panel now shows your unlocked passive abilities:
- Abilities display in green with trigger chances (e.g., `[✓] Critical Strike (15%)`)
- Up to 5 abilities shown at once
- Integrates with the leveling system

#### Leveling System Abilities
Nine passive abilities across five professions:
- **Combat**: Critical Strike (1.5x damage), Lifesteal (10% heal)
- **Mining**: Double Ore, Lucky Strike (rare gems)
- **Logging**: Efficient Chopping (instant tree), Bark Collector
- **Building**: Material Saver (don't consume materials)
- **Gathering**: Double Harvest, Rare Find

### Fixed

- **UI Loading Issues** - Fixed crashes that could occur when loading the custom HUD
- **Display Errors** - Fixed errors with UI element selectors not being found

---

## Version 2.2.1-beta - January 2026

### New Features

#### Stamina Debuffs Now Fully Functional
- **Energy Stamina Penalty** - When tired (Energy < 30), your max stamina is reduced, making actions consume stamina faster
- **Thirst Stamina Penalty** - When parched (Thirst < 30), your max stamina pool shrinks proportionally

### How It Works
- At 15 energy: 1.25x stamina consumption
- At 0 energy: 1.5x stamina consumption (stamina depletes 50% faster)
- At 15 thirst: 27.5% reduced max stamina
- At 0 thirst: 55% reduced max stamina

---

## Version 2.2.0-beta - January 2026

### Fixed

- **Speed Effects Now Work** - Speed buffs and debuffs are now visible in-game (previously only showed in logs)
- **Server Stability** - Fixed a crash that could occur when players disconnected

### Improved

- **Better Performance** - Optimized for servers with many players online simultaneously

---

## Version 2.1.0-beta - January 2026

### New Features

#### Buff System
When your stats are high, you gain powerful bonuses:
- **Speed Boost** - Move faster when Energy is 90% or higher
- **Defense Boost** - More health when Hunger is 90% or higher
- **Stamina Boost** - More stamina when Thirst is 90% or higher

Buffs use smart thresholds - they activate at 90% and only deactivate when you drop below 80%, preventing annoying flickering.

**Important:** If you have any debuff active, all buffs are suppressed until you recover.

#### Enhanced Debuffs
More gradual penalties that scale with how low your stats are:

- **Parched** (Thirst < 30) - Speed and stamina regeneration slow down proportionally
  - At 15 thirst: 27.5% slower
  - At 0 thirst: 55% slower
- **Tired** (Energy < 30) - Movement speed decreases proportionally
  - At 0 energy: 40% slower

#### Player Feedback
You now receive colored chat messages when your condition changes:
- Red messages warn you when entering a bad state ("You are starving!")
- Green messages confirm recovery ("You are no longer starving.")

#### Native Buff Detection
The mod now detects when you eat foods with special Hytale buffs:
- Health Boost foods
- Stamina Boost foods
- Meat Buff foods
- Fruit/Veggie Buff foods

### Changed

- Thirst debuff severity increased from 15% to 55% maximum reduction
- `/stats` command now shows your active buffs

---

## Version 2.0.0-beta - January 2026

### New Features

#### Modular Architecture
Server administrators can now enable or disable features independently:
- Toggle modules in `LivingLands/modules.json`
- Each module has its own configuration folder
- Enabling a module automatically enables any modules it depends on

#### Available Modules
- **Metabolism** - The core survival system (hunger, thirst, energy)
- **Claims** - Land protection (coming soon)
- **Economy** - Currency system (coming soon)
- **Groups** - Clans and parties (coming soon)
- **Leveling** - XP and progression (coming soon)
- **Traders** - NPC merchants (coming soon)

### Fixed

- Chat message colors now display correctly

---

## Version 1.1.0-beta - January 2026

### New Features

#### Native Debuff Integration
Hytale's combat debuffs now affect your metabolism:

| Debuff | Effect |
|--------|--------|
| **Poison** | Drains hunger, thirst, and energy |
| **Burn/Lava** | Severe thirst drain (dehydration from heat) |
| **Stun** | High energy drain |
| **Freeze** | High energy drain (hypothermia) |
| **Root** | Moderate energy drain |
| **Slow** | Low energy drain |

Poison tiers (T1, T2, T3) have increasing drain rates.

#### Enhanced Potion Effects
Potions now restore multiple stats:
- **Health Potions** - Also restore slight hunger and high thirst
- **Mana Potions** - Also restore slight energy and high thirst
- **Stamina Potions** - Also restore slight energy and high thirst

### Fixed

- Instant heal effects (like some foods) are now properly detected
- Potions no longer accidentally restore stats twice

---

## Version 1.0.0-beta - January 2026

### Initial Release

The first beta of Living Lands introducing core survival mechanics.

#### Survival Stats
- **Hunger** - Depletes every 60 seconds, restored by eating food
- **Thirst** - Depletes every 45 seconds, restored by drinking
- **Energy** - Depletes every 90 seconds, restored by sleeping

#### Activity System
Your activity affects how fast stats deplete:
- Sprinting: 2x faster depletion
- Swimming: 1.5x faster depletion
- Combat: 1.5x faster depletion

#### Consumables
- 40+ vanilla Hytale foods restore hunger
- Potions restore thirst
- Stamina potions restore both thirst and energy
- Milk and salads restore multiple stats

#### Sleep System
- Sleep in beds to restore 50 energy
- Respects Hytale's day/night cycle
- 5-second cooldown between attempts

#### Debuffs
- **Starvation** - Damage when hunger hits 0
- **Dehydration** - Damage when thirst hits 0
- **Exhaustion** - Stamina drain when energy hits 0

#### Commands
- `/stats` - View your current survival stats

#### HUD
- Custom on-screen display showing hunger, thirst, and energy
- Color-coded warnings for low stats

---

*Report issues at [GitHub](https://github.com/MoshPitCodes/hytale-livinglands/issues)*
