# Capture events

*Configured in [`events.yml`](../reference/configuration/events.md). Command: `/events`.*

Events pull teams into the open. HCFCore ships nine kinds — **KOTH**, **Citadel**, **Conquest**, **Kill the King**, **DTC**, **Last Break**, **Slide**, **Totem** and **Mini Totem** — all defined in `events.yml` as data: a new KOTH or Citadel is a block of YAML, never a recompile.

```text
/events                  # what runs and what is coming (aliases /event, /koth)
/events start <id>       # staff: open one now
/events stop <id>        # staff: end it with no winner
```

Starting and stopping needs `hcfcore.events.admin`. Ids are shared by every kind, so they must be unique across the file.

**Scheduling**: each event has an optional `schedule` — local times of day, read in the file's `time-zone` — at which it opens by itself. The shipped examples have none: they wait for staff until you move them onto your map and give them times.

!!! note "A restart ends a running event"
    Nothing about a running KOTH, Citadel, Conquest, Kill the King, DTC, Last Break, Slide or Totem is kept across a restart — except the King's items, which are always given back. A DTC or Last Break's core itself is not run state: it is a permanent block, placed and kept in the world, and a restart leaves it as its idle block (bedrock).

## KOTH

*King of the Hill.* A team must **hold the zone alone** for the capture time — **10 minutes** for the example (`capture-seconds: 600`).

```yaml title="events.yml — the shipped KOTH"
--8<-- "src/main/resources/events.yml:koth"
```

The zone is the box between the two corners, bounds included, written in any order.

- **Holding**: one team alone in the zone runs its countdown down.
- **Contesting**: an enemy in the zone — or a player with no team, by default (`teamless-players-contest`) — **freezes** the countdown without costing the holder anything. A teamless player can never hold a zone: capturing is a team act.
- **Allies contest each other**: a capture belongs to one team.
- **Losing the zone** — it empties, or another team takes sole control — is governed by `contest-policy`:

    | Policy | Losing the zone... |
    |---|---|
    | `RESET` | sends the countdown back to full. The classic, brutal rule: pushed off at nine minutes means starting over |
    | `PAUSE` | freezes the countdown where it is; it resumes for whoever holds next |

- **Announcements** at remaining-time marks (`announce-at-seconds`), and when the zone becomes contested — once per change, never every tick, so a long brawl produces one message.
- **Hard stop**: `max-duration-seconds` ends an event nobody manages to win (`0` runs until someone does).

**Winning**: the capture is credited to the team — counted up to `teams.yml`'s KOTH cap, and worth its `points-per-capture` — and the reward commands run from the console with `%team%` and `%event%`. There is no `%player%`: a capture belongs to a team.

## Citadel

A Citadel is a KOTH with a much longer hold — **30 minutes** in the example — fought inside a **Citadel**: a large area where the fight is decided by **teams and classes**, and where nobody gets away by throwing an item. It lives in its own section of `events.yml`, `citadels:`.

A Citadel has **two zones**:

| Zone | What it is | How it is set up |
|---|---|---|
| **The zone to hold** | The box the team must hold alone, exactly as for a KOTH | `world`, `corner-1`, `corner-2` in `events.yml` |
| **The Citadel** | A large area around it, where the restrictions below apply | Server land: a server team's claim, named by `claim` |

```yaml title="events.yml — the shipped Citadel"
--8<-- "src/main/resources/events.yml:citadel"
```

The Citadel's land is its [territory](#setting-up-an-event-in-game), drawn around the zone to hold with the claiming wand:

```text
/events claim citadel             # makes the server team Citadel if it does not exist, then hands over the wand
```

A **combat** server team: players fight there, nobody builds, and `/team map` shows it. The console warns when a Citadel starts without its claim, or with its zone to hold outside it.

### What the Citadel refuses

On the Citadel's land, **at all times** — whether the event runs or not:

| Refused | Detail |
|---|---|
| **Ender pearls** | A pearl cannot be thrown from inside, so nobody pearls out of a lost fight |
| **Partner items** | The [abilities](abilities.md) of `abilities.yml` do nothing inside |
| **Chorus fruit** | Refused before it is eaten, so it is kept — any food that teleports as well |
| **Elytra** | Nobody takes off inside, and a player gliding in lands |
| **Riptide tridents** | A Riptide trident cannot be charged inside |

**Class abilities keep working**: a Bard's buffs and bursts, an Archer's tag and dyed arrows, a Rogue's backstab — they are the point. Each restriction can be switched off in `restrictions`. The rest of the Citadel plays as a KOTH: contest policy, announcements, schedule, rewards, the hologram above the zone, and allies who may fight each other inside its zone to hold.

## Conquest

The classic HCF Conquest: several zones — four in the example, *Red*, *Blue*, *Green* and *Yellow* — captured **at the same time**.

```yaml title="events.yml — the shipped Conquest"
--8<-- "src/main/resources/events.yml:conquest"
```

- Each zone is captured like a small KOTH: held alone by one team, frozen while contested, reset or paused when lost — on a short timer (**30 seconds**).
- **A capture ends nothing**: it gives the holding team points (`points-per-capture`) and that zone's countdown starts again, so a team keeps scoring as long as it holds.
- **A member's death, anywhere, costs the team `death-penalty` points** (20; never below zero).
- **The first team to `points-to-win` (250) wins**, and its reward commands run with `%team%` and `%event%`.
- One Conquest at a time.

The scoreboard shows `%conquest_line%` (the leading team and its points) and `%conquest_zone_1%` to `%conquest_zone_4%` (each zone's countdown).

## Kill the King

A random player becomes the King. Everyone else hunts them.

1. **The draw.** A random online player in survival or adventure mode becomes the King — at least **2** such players must be online (`minimum-players`), and players with `hcfcore.events.king.exempt` are never drawn.
2. **The crowning.** The King's items are **put aside in the database** and given back at the end. They receive the King's kit and effects (configurable: diamond armour, a sword, golden apples, pearls, Speed II and Resistance I in the example).
3. **The hunt.** They are sent to a random spot in the **warzone** of the event's world, on the surface, off claimed land. **Everybody can find them**: their position is on every scoreboard, redrawn every second (`%king_location_line%`), Lunar Client players see a waypoint on them, and **once a minute** the chat says where the King is and **how much health they have left**, as a percentage (`announce-interval-seconds`, `60`; `0` turns the chat line off).

**The arena is the warzone** set in `claims.yml` — a world without a warzone cannot run the event, and the world must not have a bedrock ceiling (not the Nether).

### The King's rules

While King, a player:

- **cannot leave the warzone** without penalty: after a 3-second grace, damage every second, then a Wither whose level rises every 10 seconds up to V (`outside-penalty`). The count starts over each time they come back in. A safe zone counts as outside;
- **cannot enter spawn by any means** — walking, pearls, chorus fruit, commands, portals;
- cannot mount anything, drop items, open containers, use item frames and armour stands, or put an item in a block that holds one (a decorated pot, a shelf, a lectern, a campfire...) — the kit stays on the King;
- keeps whatever they pick up. Only the kit is taken back at the end.

### Who wins

- **The King**, if alive when the time runs out (**30 minutes**, `duration-seconds`).
- Otherwise, **whoever kills the King** — unless that player was on the King's team when the King was crowned or killed. The King's allies are not excluded: the rule is strictly per team.
- Any other death — withered, fall, lava — or a logout ends the event with **no winner**.

Reward commands run with `%player%` (the King or the killer) and `%event%`. What drops at the King's death is the kit, and it is ordinary loot. Their own items come back: at once if they survive or the event is stopped, at respawn if they die, at their next login if they leave or the server goes down.

The scoreboard shows `%king_line%` — the King and the time left — and `%king_location_line%` — where the King is.

!!! tip "Called off?"
    The announcement says why: not enough eligible players online, no warzone in the event's world, or no safe spot found in it.

## DTC (Destroy The Core)

A block **core** stands inside a zone. Teams break it, over and over — it **reappears one tick later**, every time, and stays in place after the run ends: it is permanent scenery for the next run, not a one-off objective. It is `core.material` (end stone) **while the event runs** and `core.idle-material` (bedrock) **between runs**, the way a Totem's column is — so a core nobody may break does not look breakable.

```yaml title="events.yml — the shipped DTC"
--8<-- "src/main/resources/events.yml:dtc"
```

- **Each team waits `break-cooldown-seconds` between its OWN breaks** — never blocked by another team's. Set to `0` for no wait at all.
- **`counter` decides how the core is won**:

    | `counter` | How it is won |
    |---|---|
    | `SHARED` | the core has `breaks` **common** health. When it reaches zero, the team with the **most breaks of its own** wins. A tie goes to whichever of them reached that count first |
    | `PER_TEAM` | each team has its own count to `breaks`; the **first to get there wins at once**, whatever the others have |

- **Announcements** at remaining-count marks (`announce-at`) — remaining common health under `SHARED`, remaining-to-target for each team under `PER_TEAM`.
- **Hard stop**: `max-duration-seconds` ends a DTC nobody wins (`0` runs until someone does).
- **Creative, spectator and teamless players never break the core** — this is not configurable.
- **DTC and Last Break share one run slot: only one of the two runs at a time** — starting a DTC while a Last Break runs (or the reverse) is refused, naming whichever of the two is actually running. A KOTH, a Conquest, a Slide and a DTC (or a Last Break) can all run together — the families are independent.

The scoreboard shows `%dtc_line%` (common health, or the `PER_TEAM` leader) and `%dtc_team_line%` (your own team's breaks).

## Last Break

The same engine as DTC, always common health: whichever team lands the break that **empties the core wins** — even with fewer breaks of its own than another team that hit it more often.

```yaml title="events.yml — the shipped Last Break"
--8<-- "src/main/resources/events.yml:last-break"
```

Everything else — the per-team cooldown, announcements, hard stop, the hardcoded rule that creative, spectator and teamless players never score, and **sharing DTC's one run slot** — is exactly as for DTC. The scoreboard shows `%last_break_line%`.

## Slide

A zone where **every team member present scores for their team**.

```yaml title="events.yml — the shipped Slide"
--8<-- "src/main/resources/events.yml:slide"
```

- **Every `interval-seconds`, each team member standing in the zone brings their team `points-per-player`** — cumulative: three members in the zone score three times as fast as one.
- **A member's death, ANYWHERE on the server, while the Slide runs, costs the team `death-penalty` points** (never below zero). `announce-deaths: false` mutes the broadcast — the points are always lost either way.
- **The first team to `points-to-win` wins.** Several teams crossing it on the same tick hand it to whichever is highest; an exact tie changes nothing and the Slide keeps running.
- **Creative, spectator and teamless players never score** — this is not configurable.
- One Slide runs at a time.

The scoreboard shows `%slide_line%` (the leader) and the **live top 3**, `%slide_top_1%` to `%slide_top_3%`.

## Totem and Mini Totem

A column of blocks in the event zone — **5** for a Totem, **3** for a Mini Totem. Between runs it is **bedrock**; when the event starts it turns to **quartz**.

```yaml title="events.yml — the shipped Totem"
--8<-- "src/main/resources/events.yml:totem"
```

- **Break it with a sword** (`tools`, every sword by default) — at once, one hit per block (`instant-break`). Each block your team breaks turns to bedrock and stays so.
- **The first team to break the whole column wins.**
- **A block broken by any other team starts the totem over**: every block turns back to quartz, and that team's progress counts for nothing (`rival-break: RESET`). With `RESET_AND_START`, that break is then the other team's first.
- Creative, spectator and teamless players never break it. The column cannot be blown up or pushed by a piston, and outside a run only a staff member in creative can take it apart.
- When the event ends — won, out of time, stopped — the column is bedrock again.
- One Totem runs at a time.

The Mini Totem is the same event with `height: 3`:

```yaml title="events.yml — the shipped Mini Totem"
--8<-- "src/main/resources/events.yml:mini-totem"
```

The scoreboard shows `%totem_line%`: the team on its way and how many blocks it has broken.

## The weekly schedule

`/schedule` (`/planning`) shows every event start of the next seven days — for everybody — as a **window**: one item per day, holding that day's events hour by hour. `/schedule chat` prints the same as a list, and `weekly-schedule.menu.enabled: false` makes the list the default. It merges two sources:

- each event's own **daily** times, its `schedule` key — the same every day;
- the **weekly schedule**, `weekly-schedule` in `events.yml`: which event starts at what time on which day of the week. The plugin starts them itself, exactly as `/events start` would.

Staff edit the weekly schedule in game (`hcfcore.events.admin`), and the change applies at once:

```text
/schedule add friday 20:00 koth          # every Friday at 20:00
/schedule add sat 18:00 conquest         # a day's first three letters work too
/schedule remove friday 20:00 [koth]     # everything at that time, or only that event
```

Every start — weekly or daily — is announced `announce-before-minutes` ahead (15, 5 and 1 minute as shipped). A start that is refused — the event already running, another of its kind running, too few players for Kill the King — is reported in the console and skipped. Nothing is caught up: a time that passes while the server is down is missed.

## Setting up an event in game

Every kind of event — KOTH, Citadel, Kill the King, Conquest, DTC, Last Break, Slide, Totem, Mini Totem — is created, set up and deleted with the **same commands**, without touching `events.yml` by hand:

```text
/events create <type> <id>      # a new event where you stand: koth, citadel, ktk, conquest, dtc, lastbreak, slide, totem, minitotem
/events info <id>               # what it has, what it lacks, and the command for each
/events claim <id>              # its territory, drawn with the claiming wand
/events unclaim <id> [all]      # release the claim of its territory you stand in, or all of it
/events setzone <id> [zone]     # its zone, drawn with the wand - a Conquest names the zone (a new name adds one)
/events delzone <id> <zone>     # delete one of a Conquest's zones
/events setblock <id>           # DTC/Last Break: the core; Totem: the column - on the block you look at
/events delete <id>             # the event, and its territory with it
```

An event is set up in up to three parts, and `/events info` ticks off each:

- its **territory** — server land around it, the claims of a *server team* (a combat zone) named in the event's `claim` key. Nobody builds there, `/team map` shows it, and it is drawn with the **claiming wand** like any other claim: left-click a corner, right-click the other, sneak + left-click to claim. `/events claim` makes the server team when there is none yet — named after the event, `last-break` becoming `LastBreak`;
- its **zone** — what is held, stood in or broken inside, with heights: drawn with the same wand, from the lower of the two clicked blocks to `setup.zone-height` blocks above the higher. A zone reaching outside the territory is pointed out;
- its **block** — a DTC's or Last Break's core, a Totem's column (built at once, of bedrock), on the block you look at, up to `setup.target-distance` blocks away. It must be inside the zone.

Kill the King has none of the three: it is fought in the warzone of its world, and `/events info` says whether that world has one.

`/events create` does all of it at once, where you stand: the new event is a copy of the plugin's own example of its kind — the one shown on this page, with its capture time, its four Conquest zones... — moved so that its block (or the middle of its zone's floor) is on your position, with **no schedule**. Its server team is made, and its territory claimed: every zone and `setup.claim-margin` blocks around (`10`), unless `setup.auto-claim` is `false` or the land is taken — the command then says why. Then it shows `/events info`. From there, redraw what does not suit, give it its times (`schedule:` in `events.yml`), and try it with `/events start <id>`.

`/events delete` removes the event from `events.yml`, a Totem's column from the world, and **releases its territory**: the server team named in its `claim` is disbanded — unless another event names it too. None of the setup commands touches a running event: stop it first with `/events stop <id>`.

An `<id>` must be **2 to 32 characters of lowercase letters, digits, `_` and `-`** — a dot is refused, since `events.yml` is written through Bukkit's configuration API, which reads a dot in a key as a path separator; a bad id is reported rather than silently mangled. Typed in any case, it is lower-cased to match the shipped ids (`koth`, `last-break`...). Ids are shared by every kind: two events never have the same one.

!!! warning "These commands write events.yml"
    The `/events` setup commands are the **only** commands in HCFCore that ever rewrite a configuration file — and only the one section of the one event they name, never the rest of the file. Comments are kept; the layout becomes the server's own (quotes, lists one item per line). A file that does not parse is left untouched, and the command says it could not write. See [Upgrading](../getting-started/upgrading.md).

If a DTC's core or a Totem's column is not on server land, the console warns when the event loads — territory protection otherwise does not apply to it between runs. `/events claim <id>` fixes it.

## Zone holograms

A hologram floats above every capture zone — KOTH, Citadel, each Conquest zone, a DTC or Last Break's core, a Slide's zone, and a Totem's column — for as long as it is configured (`zone-holograms`): while its event runs, its status; otherwise, when it runs next (or "not scheduled"). A DTC or Last Break shows its health (or, under `PER_TEAM`, the leader's breaks); a Slide shows its live top 3. Its texts are under `events.hologram` in `lang/en.yml`. Kill the King has no zone of its own, so no hologram. The hologram module (`holograms.yml`) must be enabled.

## Friendly fire in events

Allies can hurt each other inside the zone of a running KOTH, Citadel, Conquest, DTC, Last Break, Slide or Totem, and on or by the King during Kill the King — where allied teams compete. See [Combat](combat.md#friendly-fire).

## Points

Winning also earns [team points](teams.md#points-and-ranking), all `0` by default: `koth.points-per-capture` for KOTH and Citadel, `points.per-conquest-win`, `points.per-king-win`, `points.per-dtc-win`, `points.per-last-break-win`, `points.per-slide-win`, `points.per-totem-win`.

## Lunar Client

Lunar Client players see waypoints on KOTH, Citadel and Conquest zones, on a running DTC or Last Break's core, on a running Slide's zone, on a running Totem's column, and on the King during Kill the King. See [Integrations](../server/integrations.md#lunar-client-apollo).
