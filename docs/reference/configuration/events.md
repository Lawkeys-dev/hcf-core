# events.yml

Capture events: KOTHs, Citadels (a zone to hold inside a claimed Citadel with its restrictions), Kill the King, Conquest, DTC, Last Break, Slide, their schedules and rewards, and the zone holograms.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/events.md)

Every example on this page is **taken from the shipped `events.yml`**. Changes apply with `/hcf reload`. Event ids are shared by every kind — KOTH, Citadel, Kill the King, Conquest, DTC, Last Break, Slide, Totem — and must be unique across the file. Any event with land may name its server team in a `claim` key: `/events delete` releases that team's land with the event.

!!! info "Every event can be set up in game"
    `/events create`, `claim`, `setzone`, `delzone`, `setblock` and `delete` write into the sections below for you, whatever the kind of event — the only commands in the plugin that ever rewrite a configuration file. See the [guide](../../gameplay/events.md#setting-up-an-event-in-game).

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
| `enabled` | `true` | A hologram above every KOTH, Citadel, Conquest, DTC, Last Break and Slide zone. `holograms.yml` must be enabled too |
| `height` | `3.0` | Blocks above the top of the zone (or the core, for DTC and Last Break) |

## Setup commands

What the setup commands do by default — see the [guide](../../gameplay/events.md#setting-up-an-event-in-game).

```yaml title="events.yml"
--8<-- "src/main/resources/events.yml:setup"
```

| Key | As shipped | What it does |
|---|---|---|
| `auto-claim` | `true` | `/events create` claims the new event's territory at once; `false` leaves it to `/events claim` |
| `claim-margin` | `10` | Blocks of territory `/events create` claims around the new event's zones |
| `target-distance` | `10` | How far `/events setblock` looks along where the player is looking |
| `zone-height` | `10` | A zone drawn with the wand rises this many blocks above the higher clicked block |

A new event is a copy of the plugin's own example of its kind — the jar's, never your file's: that stays hard-coded. Every event with land has a `claim` key naming its server team; `/events create` and `/events claim` write it.

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
| `claim` | `Citadel` | The server team whose land is the Citadel — `/events claim citadel` makes it and hands over the wand |
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

## DTC (Destroy The Core)

Under `dtc:`, one entry per DTC. **DTC and Last Break share one run slot — only one of the two runs at a time**, whatever their ids: starting one while the other runs is refused.

```yaml title="events.yml — the shipped DTC"
--8<-- "src/main/resources/events.yml:dtc"
```

| Key | As shipped | What it does |
|---|---|---|
| `display-name`, `world`, `corner-1`, `corner-2` | an example | Its name and zone, as for a KOTH |
| `core.x`, `core.y`, `core.z` | `410, 65, 410` | The core's block — must be inside the zone |
| `core.material` | `OBSIDIAN` | The block it is (and reappears as); must be a real, solid, non-air block with no gravity |
| `counter` | `SHARED` | `SHARED` — the core has `breaks` common health, most breaks of its own wins a tie by whoever reached that count first. `PER_TEAM` — each team has its own count to `breaks`, first there wins at once |
| `breaks` | `150` | The core's common health (`SHARED`), or each team's target (`PER_TEAM`) |
| `break-cooldown-seconds` | `1` | Delay between two breaks of the SAME team; `0` for none. Another team is never blocked by it |
| `announce-at` | `[125, 100, 75, 50, 25, 10]` | Remaining-count marks to broadcast — remaining common health (`SHARED`), or remaining-to-target per team (`PER_TEAM`) |
| `max-duration-seconds`, `schedule`, `reward-commands` | `0`, `[]`, `[]` | As for a KOTH; rewards get `%team%` and `%event%` |

## Last Break

Under `last-break:` — the same engine as DTC, with no `counter`: it is always common health, and whichever team lands the break that empties it wins.

```yaml title="events.yml — the shipped Last Break"
--8<-- "src/main/resources/events.yml:last-break"
```

Every key is as for DTC, minus `counter` — and it shares DTC's one run slot (above).

## Slide

Under `slide:`, one entry per Slide.

```yaml title="events.yml — the shipped Slide"
--8<-- "src/main/resources/events.yml:slide"
```

| Key | As shipped | What it does |
|---|---|---|
| `display-name`, `world`, `corner-1`, `corner-2` | an example | Its name and zone |
| `points-per-player` | `1` | Points ONE team member standing in the zone brings their team, per interval — cumulative |
| `interval-seconds` | `1` | How often points are awarded |
| `death-penalty` | `10` | Points a team loses when a member dies, anywhere, while the Slide runs; never below zero |
| `announce-deaths` | `true` | Whether a death's point loss is broadcast; the points are always lost either way |
| `points-to-win` | `500` | The target |
| `announce-at` | `[100, 250, 400, 450]` | Points marks to broadcast, once per team |
| `max-duration-seconds`, `schedule`, `reward-commands` | `0`, `[]`, `[]` | As for a KOTH; rewards get `%team%` and `%event%` |

## Totem and Mini Totem

Under `totem:`, one entry per Totem — a Mini Totem is only a shorter one. **How it plays:** [:octicons-arrow-right-24: Totem](../../gameplay/events.md#totem-and-mini-totem)

```yaml title="events.yml — the shipped Totem"
--8<-- "src/main/resources/events.yml:totem"
```

| Key | As shipped | What it does |
|---|---|---|
| `display-name`, `world`, `corner-1`, `corner-2` | an example | Its name and zone; the column must stand inside it |
| `base` | an example | The column's lowest block — set in game with `/events setblock <id>` |
| `height` | `5` (`3` for the Mini Totem) | How many blocks the column is, 1 to 64 |
| `material` | `QUARTZ_BLOCK` | The column during a run: what is broken |
| `broken-material` | `BEDROCK` | A block once a team has broken it |
| `idle-material` | `BEDROCK` | The column between runs |
| `tools` | every sword | What a block may be broken with; an empty list allows anything |
| `instant-break` | `true` | One hit breaks a block, rather than the time the game gives it to that tool |
| `rival-break` | `RESET` | A block broken by another team: `RESET` starts the totem over and counts for nobody; `RESET_AND_START` also makes it the other team's first |
| `announce-breaks` | `true` | Announce every block broken; the start, a reset and the win are always announced |
| `max-duration-seconds`, `schedule`, `reward-commands` | `0`, `[]`, `[]` | As for a KOTH; rewards get `%team%` and `%event%` |

What stays in code: one Totem runs at a time; creative, spectator and teamless players never break it; the column is always protected from explosions and pistons.

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/events.yml).

<div class="hcf-shipped" markdown>

```yaml title="events.yml"
--8<-- "src/main/resources/events.yml"
```

</div>
