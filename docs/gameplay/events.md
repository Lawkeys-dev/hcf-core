# Capture events

*Configured in [`events.yml`](../reference/configuration/events.md). Command: `/events`.*

Events pull teams into the open. HCFCore ships four kinds — **KOTH**, **Citadel**, **Conquest** and **Kill the King** — all defined in `events.yml` as data: a new KOTH or Citadel is a block of YAML, never a recompile.

```text
/events                  # what runs and what is coming (aliases /event, /koth)
/events start <id>       # staff: open one now
/events stop <id>        # staff: end it with no winner
```

Starting and stopping needs `hcfcore.events.admin`. Ids are shared by every kind, so they must be unique across the file.

**Scheduling**: each event has an optional `schedule` — local times of day, read in the file's `time-zone` — at which it opens by itself. The shipped examples have none: they wait for staff until you move them onto your map and give them times.

!!! note "A restart ends a running event"
    Nothing about a running KOTH, Citadel, Conquest or Kill the King is kept across a restart — except the King's items, which are always given back.

## KOTH

*King of the Hill.* A team must **hold the zone alone** for the capture time — **10 minutes** for the example (`capture-seconds: 600`).

```yaml
events:
  koth:
    display-name: "&6KOTH"
    world: world
    corner-1: {x: 100, y: 60, z: 100}
    corner-2: {x: 115, y: 90, z: 115}
    capture-seconds: 600
    contest-policy: RESET
    schedule: ["18:00", "21:00"]
    reward-commands: []   # console commands; %team% and %event% are filled in
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

```yaml title="events.yml"
citadels:
  citadel:
    display-name: "&5Citadel"
    world: world
    corner-1: {x: -200, y: 60, z: -200}     # the zone to hold
    corner-2: {x: -185, y: 90, z: -185}
    capture-seconds: 1800
    contest-policy: RESET
    schedule: []
    reward-commands: []
    claim: Citadel                          # the server team whose land is the Citadel
    restrictions:
      ender-pearls: true
      partner-items: true
      chorus-fruit: true
      elytra: true
      riptide: true
```

Create the Citadel's land once, around the zone to hold:

```text
/team createsystem Citadel combat
/team forceclaim Citadel 4        # as many times, and wherever, as needed
```

A **combat** server team: players fight there, nobody builds, and `/team map` shows it. The console warns when a Citadel starts without its claim, or with its zone to hold outside it.

### What the Citadel refuses

On the Citadel's land, **at all times** — whether the event runs or not:

| Refused | Detail |
|---|---|
| **Ender pearls** | A pearl cannot be thrown from inside, so nobody pearls out of a lost fight |
| **Partner items** | The kit abilities of `kits.yml` do nothing inside |
| **Chorus fruit** | Refused before it is eaten, so it is kept — any food that teleports as well |
| **Elytra** | Nobody takes off inside, and a player gliding in lands |
| **Riptide tridents** | A Riptide trident cannot be charged inside |

**Class abilities keep working**: a Bard's buffs and bursts, an Archer's tag and dyed arrows, a Rogue's backstab — they are the point. Each restriction can be switched off in `restrictions`. The rest of the Citadel plays as a KOTH: contest policy, announcements, schedule, rewards, the hologram above the zone, and allies who may fight each other inside its zone to hold.

## Conquest

The classic HCF Conquest: several zones — four in the example, *Red*, *Blue*, *Green* and *Yellow* — captured **at the same time**.

```yaml
conquest:
  conquest:
    display-name: "&6Conquest"
    world: world
    capture-seconds: 30
    points-per-capture: 1
    points-to-win: 250
    death-penalty: 20
    zones:
      red:
        display-name: "&cRed"
        corner-1: {x: 300, y: 60, z: 300}
        corner-2: {x: 306, y: 70, z: 306}
      # ...
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
3. **The hunt.** They are sent to a random spot in the **warzone** of the event's world, on the surface, off claimed land. **Their coordinates are announced every second** (`coordinates-interval-ticks`).

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

The scoreboard shows `%king_line%`: the King and the time left.

!!! tip "Called off?"
    The announcement says why: not enough eligible players online, no warzone in the event's world, or no safe spot found in it.

## Zone holograms

A hologram floats above every capture zone — KOTH, Citadel and each Conquest zone — for as long as it is configured (`zone-holograms`): while its event runs, the time left and who holds it (or "contested"); otherwise, when it runs next (or "not scheduled"). Its texts are under `events.hologram` in `lang/en.yml`. Kill the King has no zone of its own, so no hologram. The hologram module (`holograms.yml`) must be enabled.

## Friendly fire in events

Allies can hurt each other inside the zone of a running KOTH, Citadel or Conquest, and on or by the King during Kill the King — where allied teams compete. See [Combat](combat.md#friendly-fire).

## Points

Winning also earns [team points](teams.md#points-and-ranking), all `0` by default: `koth.points-per-capture` for KOTH and Citadel, `points.per-conquest-win`, `points.per-king-win`.

## Lunar Client

Lunar Client players see waypoints on KOTH, Citadel and Conquest zones, and on the King during Kill the King. See [Integrations](../server/integrations.md#lunar-client-apollo).
