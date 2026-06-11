# Spill N' Drop

**Drop your items when you get hurt — literally.**

SpillNDrop is a lightweight Minecraft plugin that makes taking damage feel more impactful. When a player falls, gets rammed by a goat, or gets hit by a Warden's sonic boom, there's a chance their items spill out of their inventory — or just their held item, if you prefer a subtler approach.

Supports Spigot, Paper, and compatible server software from Minecraft `1.19` through `26.1.2`.

---

## Features

- **Inventory drop** — on damage, random items from the player's entire inventory may scatter onto the ground
- **Held item drop** — alternatively, only the item in the player's main hand drops
- **Water spill** — water buckets empty and place water at the player's feet instead of dropping as an item
- **Potion smash** — potions get thrown and shatter on impact instead of dropping as an item
- **Configurable drop chance** — set a flat percentage (`20%`) or a damage-scaled multiplier (`6.0` × damage dealt)
- **Per-cause control** — configure fall damage, sonic boom, and goat attacks independently
- **Scatter physics** — dropped items fly outward with configurable force
- **Pickup delay** — dropped items can't be immediately picked back up
- **World selection** — choose the worlds in which the plugin is active
- **Multi-language support** — ships with English and Polish, easily extendable

---

## Damage causes

| Cause | Config key | Default chance |
|---|---|---|
| Fall damage | `FALL` | `6.0` × damage |
| Warden sonic boom | `SONIC_BOOM` | `10.0` × damage |
| Goat ram | `GOAT_ATTACK` | `20.0` × damage |

---

## Commands

All commands require the `spillndrop.admin` permission.

| Command | Description |
|---|---|
| `/snd help` | Show command list |
| `/snd reload` | Reload config and language files |
| `/snd version` | Show plugin version and author |
| `/snd set <cause> <value>` | Set drop chance for a damage cause (e.g. `6.0` or `20%`) |
| `/snd toggle <feature> [true/false]` | Enable or disable a feature |

### Toggleable features

- `drop-items` — drops random items from the entire inventory
- `drop-held-item` — drops only the item held in the main hand
- `spill-water` — spills water buckets instead of dropping them
- `smash-potions` — throws and smashes potions instead of dropping them

> **Note:** `drop-items` and `drop-held-item` can both be enabled at the same time, but this is not recommended — the held item may drop twice.

---

## Configuration

```yaml
language: en  # Available: en, pl

enabled-worlds:
  - world
  - world_nether
  - world_the_end
# Use '*' as the only entry to enable the plugin in all worlds

multipliers:
  FALL: 6.0
  SONIC_BOOM: 10.0
  GOAT_ATTACK: 20.0

drop-height-offset: 0.5   # Height above player where items appear
pickup-delay: 20           # Ticks before dropped items can be picked up (20 = 1s)
scatter-force: 0.2         # How far items fly outward (0.1–1.0 recommended)

features:
  drop-items: true
  drop-held-item: false
  spill-water: true
  smash-potions: true
```

---

## Language support

Language files live in the `langs/` folder inside the plugin directory. To add a new language, create a new `.yml` file (e.g. `de.yml`) based on the existing `en.yml`, then set `language: de` in `config.yml` and reload.

---

## Permissions

| Permission | Description |
|---|---|
| `spillndrop.admin` | Access to all `/snd` commands |

## Installation

Download the mod from [Modrinth](h[ttps://modrinth.com/mod/better-mc-screenshots](https://modrinth.com/plugin/spill-n-drop).

The plugin is compiled for Java 17. Your server may require a newer Java version depending on its Minecraft version.

## Development

To build the project using Gradle, run the following commands in the project root, depending on your Minecraft version:

```bash
./gradlew build                              # Build against the minimum supported API (1.19)
./gradlew build -PspigotApiVersion=26.1.2    # Verify against the latest supported API
