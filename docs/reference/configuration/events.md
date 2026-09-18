# events.yml

Capture events: KOTHs, Citadels (a zone to hold inside a claimed Citadel with its restrictions), Kill the King, Conquest, their schedules and rewards, and the zone holograms.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/events.md)

Every example on this page is **taken from the shipped `events.yml`**. Changes apply with `/hcf reload`. Event ids are shared by every kind — KOTH, Citadel, Kill the King, Conquest — and must be unique across the file.

!!! warning "The shipped events are examples"
    Their zones sit at made-up coordinates and none has a schedule: they run only when staff start them (`/events start <id>`). Move them onto your map, then give them times.

## General settings

```yaml title="events.yml"
--8<-- "src/main/resources/events.yml:general"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Every event |
| `tick-seconds` | `1` | How often the zones are sampled; 1 matches the countdowns |
| `time-zone` | `system` | The zone `schedule` times are read in. Write yours: `"Europe/Paris"` |
| `announce-contests` | `true` | Say when a zone becomes contested, once per change |
| `teamless-players-contest` | `true` | Whether a player with no team freezes a capture |

## Zone holograms

```yaml title="events.yml"
--8<-- "src/main/resources/events.yml:zone-holograms"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | A hologram above every KOTH, Citadel and Conquest zone. `holograms.yml` must be enabled too |
| `height` | `3.0` | Blocks above the top of the zone |

## KOTH

Under `events:`, one entry per KOTH.

```yaml title="events.yml — the shipped KOTH"
--8<-- "src/main/resources/events.yml:koth"
```

| Key | As shipped | What it does |
|---|---|---|
| `display-name` | `&6KOTH` | Its name in messages |
| `world`, `corner-1`, `corner-2` | an example | The zone to hold: the box between the corners, bounds included, in any order |
| `capture-seconds` | `600` | Seconds of holding alone needed to win |
| `contest-policy` | `RESET` | What losing the zone does: `RESET` back to full, `PAUSE` frozen for the next holder |
| `max-duration-seconds` | `0` | A hard stop with no winner; `0` runs until someone wins. At least `capture-seconds` |
| `announce-at-seconds` | `[300, 120, 60, 30, 10]` | Remaining-time marks to broadcast |
| `schedule` | `[]` | Local times it opens by itself, e.g. `["18:00", "21:00"]` |
| `reward-commands` | `[]` | Console commands for the winner, with `%team%` and `%event%` |

## Citadel

Under `citadels:`: a KOTH — every key above works the same — plus the Citadel's claim and what it refuses.

```yaml title="events.yml — the shipped Citadel"
--8<-- "src/main/resources/events.yml:citadel"
```

The two keys only a Citadel has:

```yaml title="events.yml"
--8<-- "src/main/resources/events.yml:citadel-claim"
```

| Key | As shipped | What it does |
|---|---|---|
| `claim` | `Citadel` | The server team whose land is the Citadel — create it with `/team createsystem Citadel combat` and `/team forceclaim` |
| `restrictions.ender-pearls` | `true` | No ender pearl thrown from inside |
| `restrictions.partner-items` | `true` | No partner item ([`abilities.yml`](abilities.md)) used inside; class abilities still work |
| `restrictions.chorus-fruit` | `true` | No chorus fruit, or any food that teleports, eaten inside |
| `restrictions.elytra` | `true` | No take-off with elytra; a player gliding in lands |
| `restrictions.riptide` | `true` | No Riptide trident charged inside |

The restrictions hold on the Citadel's land **at all times**, whether the event runs or not.

## Kill the King

Under `kill-the-king:`. The arena is the warzone of `world`, set in `claims.yml`.

```yaml title="events.yml — the shipped Kill the King"
--8<-- "src/main/resources/events.yml:ktk"
```

| Key | As shipped | What it does |
|---|---|---|
| `display-name`, `world` | `&4Kill the King`, `world` | Its name, and the world whose warzone is the arena |
| `duration-seconds` | `1800` | How long the King must survive |
| `minimum-players` | `2` | Eligible players needed online; below, the event is called off |
| `announce-interval-seconds` | `60` | How often the King's position and health go to chat; `0` never. The position is on the scoreboard all along |
| `announce-at-seconds` | `[900, 300, 60, 10]` | Remaining-time marks to broadcast |
| `schedule`, `reward-commands` | `[]` | As for a KOTH; rewards get `%player%` (the King or the killer) and `%event%` |

**Leaving the warzone:**

```yaml title="events.yml"
--8<-- "src/main/resources/events.yml:ktk-penalty"
```

| Key | As shipped | What it does |
|---|---|---|
| `grace-seconds` | `3` | Seconds outside before anything happens |
| `damage-per-second` | `1.0` | Damage each second outside after the grace; 2.0 is a heart |
| `wither-start-level` | `1` | Wither level once the grace is over |
| `wither-step-seconds` | `10` | One level more every this many seconds |
| `wither-max-level` | `5` | The highest level |

**The King's kit:**

```yaml title="events.yml"
--8<-- "src/main/resources/events.yml:ktk-kit"
```

`helmet`, `chestplate`, `leggings`, `boots` and each of `items` take a `material`, and optionally a `name`, an `amount` and `enchantments` (levels may exceed vanilla). `effects` gives effects for the reign, as levels. An unknown name is reported and left out.

## Conquest

Under `conquest:`.

```yaml title="events.yml — the shipped Conquest"
--8<-- "src/main/resources/events.yml:conquest"
```

| Key | As shipped | What it does |
|---|---|---|
| `display-name`, `world` | `&6Conquest`, `world` | Its name and world |
| `capture-seconds` | `30` | Seconds a team must hold a zone alone to score |
| `points-per-capture` | `1` | Points one capture is worth |
| `points-to-win` | `250` | The target |
| `death-penalty` | `20` | Points a team loses when one of its members dies |
| `contest-policy`, `max-duration-seconds`, `schedule` | `RESET`, `0`, `[]` | As for a KOTH |
| `reward-commands` | `[]` | For the winner, with `%team%` and `%event%` |
| `zones` | four examples | Each with a `display-name` and two corners |

```yaml title="events.yml — its zones"
--8<-- "src/main/resources/events.yml:conquest-zones"
```

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/events.yml).

<div class="hcf-shipped" markdown>

```yaml title="events.yml"
--8<-- "src/main/resources/events.yml"
```

</div>
