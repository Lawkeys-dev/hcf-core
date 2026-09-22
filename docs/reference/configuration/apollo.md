# apollo.yml

Lunar Client features through Apollo: waypoints, team view, cooldowns and nametags. **Needs the `Apollo-Bukkit` plugin** on the server; without it nothing here does anything.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../server/integrations.md#lunar-client-apollo)

Every example on this page is **taken from the shipped `apollo.yml`**. Changes apply with `/hcf reload`: everything sent is taken back and sent again. Apollo's own configuration can switch a module off for the whole server, whatever this file says.

## General settings

```yaml title="apollo.yml"
--8<-- "src/main/resources/apollo.yml:general"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Every Lunar Client feature |
| `update-ticks` | `10` | How often everything is brought up to date; only what changed is sent |

## Waypoints

```yaml title="apollo.yml"
--8<-- "src/main/resources/apollo.yml:waypoints"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Waypoints at all |
| `hq`, `base`, `rally` | `true` | The team's HQ, base and rally point |
| `focus` | `true` | A focused player, and a focused team's HQ |
| `event-height` | `60` | How far above the event itself its waypoint is put, in blocks (0 to 320): a beam starting on the floor of a zone is lost behind the landscape. The King's follows the player and is never raised |
| `events` | `true` | Every running event: KOTH, Citadel and Conquest zones, a DTC or Last Break core, a Slide zone, a Totem's column, the King |
| `colors` | hex colours | One per kind |

## Team view

```yaml title="apollo.yml"
--8<-- "src/main/resources/apollo.yml:team-view"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Teammates marked above their heads and on the minimap |
| `marker-color` | `#55FF55` | The marker's colour |
| `tracking-range` | `48` | Beyond this many blocks, position and name are sent |

## Cooldowns

```yaml title="apollo.yml"
--8<-- "src/main/resources/apollo.yml:cooldowns"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Lunar cooldown icons |
| `combat-tag`, `combat-tag-icon` | `true`, `DIAMOND_SWORD` | The combat tag, and its icon |
| `warmups`, `warmup-icon` | `true`, `CLOCK` | Running countdowns (`/spawn`, `/team hq`...) |
| `abilities` | `true` | Partner item cooldowns, each with its own item — and the shared one, and the Pocket Bard's sets |
| `ability-global-icon` | `NETHER_STAR` | The shared partner item cooldown's icon |
| `ender-pearl`, `ender-pearl-icon` | `true`, `ENDER_PEARL` | The ender pearl cooldown, and its icon |
| `item-cooldowns` | `true` | The item cooldowns of `pvp.yml`, each with its own item |
| `classes` | `true` | A class's click items (a Bard's effects) and a Rogue's backstab, each with its own item |
| `crowbar` | `true` | The crowbar's cooldown |

## Nametags

```yaml title="apollo.yml"
--8<-- "src/main/resources/apollo.yml:nametags"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Nametags |
| `team-line` | `%color%%team% &7\| &e%dtr%` | The line above the name; left out for a player with no team |
| `name-line` | `%color%%player%` | The name line |
| `colors` | `self`, `ally`, `enemy`, `focus`, `neutral` | `%color%` for each relation |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/apollo.yml).

<div class="hcf-shipped" markdown>

```yaml title="apollo.yml"
--8<-- "src/main/resources/apollo.yml"
```

</div>
