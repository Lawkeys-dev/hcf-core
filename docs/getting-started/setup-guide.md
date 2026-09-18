# Setting up a map

This page takes a freshly installed server to one ready to open, in the order that usually works. Each step names the file or command involved; the file's own comments, and the [configuration reference](../reference/configuration/index.md), give the full detail.

Commands below are typed in game by an operator unless stated otherwise. After editing a file, `/hcf reload` applies it.

## 0. The examples

`events.yml` and `resourcenodes.yml` ship examples — `koth`, `citadel`, `ktk` and `conquest`, `glowstone-mountain` and `ore-mountain`. None runs by itself: each waits for staff (`/events start <id>`, `/resourcenode refill <id>`) until you give it times. But they sit at made-up coordinates, and every capture zone gets a floating hologram above it as long as it is configured.

!!! warning "Move or delete every example first"
    Move each example to your map, or delete it, before anything else. The comments show the times the classic KOTH and Glowstone Mountain use.

`events.yml`, `resourcenodes.yml`, `phases.yml` and `schedule.yml` read their times in `time-zone: "system"` — the host's clock. Write your zone (`"Europe/Paris"`) so moving hosts does not shift every event.

## 1. Ranks and permissions

Operators hold nearly every node through `hcfcore.admin`. For your staff ranks, in LuckPerms or any permission plugin:

- moderators: `hcfcore.staff` — staff mode, vanish, freeze, invsee, lastinv, the ticket queue, staff chat, `/staffbuild` and the staff teleports;
- add `hcfcore.staff.broadcast`, `hcfcore.staff.clearchat`, `hcfcore.staff.strike`, `hcfcore.pvp.admin`... as each rank needs them.

```text
/lp group mod permission set hcfcore.staff true
/lp group admin permission set hcfcore.staff.vanish.see true
```

Three nodes nobody holds by default, operators included, because each is a choice: `hcfcore.staff.vanish.see` (seeing vanished staff), `hcfcore.staff.invsee.edit` (changing a player's inventory through `/invsee`) and `hcfcore.events.king.exempt` (never drawn as King).

Two things to know:

- **Staff build through territory protection only with `/staffbuild` on, and only if they hold `hcfcore.claim.bypass`** — operators included. The toggle alone lifts nothing, and so does the permission alone, so nobody breaks a wall by accident while looking around.
- **Deathban tiers.** A death bans for one hour by default; `pvp.yml` lists permission nodes with shorter bans (`permission-tiers`) and the shortest one a player holds wins. See [Deathbans and lives](../gameplay/deathbans-and-lives.md).

The full list is in [Permissions](../reference/permissions.md).

## 2. Spawn

Spawn is a *server team* marked as a safe zone: nobody fights on it, no player team can claim it, and nobody but staff builds, breaks, or uses doors, chests and buttons there — keep what players must use off server land. Kit refill signs are the exception: they work anywhere.

1. Stand in the middle of your spawn and create the team:

    ```text
    /team createsystem Spawn safe
    ```

    Server team names follow the usual length and character rules of `teams.yml`, but not its blacklist, so "Spawn" is accepted.

2. Claim the land — radius 3 is 7 × 7 chunks around you:

    ```text
    /team forceclaim Spawn 3
    ```

    The radius goes up to 32; run it again elsewhere to extend. `/team forceunclaim Spawn` releases the chunk you stand in, `/team forceunclaim Spawn all` everything.

3. Set the world's spawn point with the vanilla `/setworldspawn`: that is where `/spawn` sends players (`general.yml` can point it at another world).

`/team here` shows who owns the chunk you stand in, `/team map` the territory around you.

## 3. Warzone

The warzone is the square of land around each world's centre that no player team can claim, where everybody fights and — by default — nobody builds. In `claims.yml`:

```yaml
warzone:
  worlds:
    world:
      radius: 800
    world_nether:
      radius: 250
```

The radius is in blocks from the centre (0, 0 unless you set `center-x` / `center-z`) and is rounded outwards to whole chunks. The warzone governs only land nobody owns: spawn at its centre stays safe, and anything claimed inside it keeps its owner's rules. `warzone.allow-building` lets players build there if your server wants that.

!!! info
    Kill the King needs a warzone: it is the event's arena.

## 4. Roads and other server land

A road, or an event area you want protected from building but open to fighting, is a *combat* server team:

```text
/team createsystem North_Road combat
/team forceclaim North_Road 1
```

Claim it in squares along its length. `/team setzone <team> <safe|combat>` switches an existing server team.

## 5. Claim rules

`claims.yml` sets what players may claim: 16 chunks plus 4 per member by default, at least 2 chunks from another team, touching the team's existing land, in every world unless `claimable-worlds` lists some. It also holds the countdowns of `/team hq` (10 s) and `/team stuck` (60 s). `dtr.yml` sets how many deaths a team takes before its land opens. See [Territory](../gameplay/territory.md) and [DTR and raids](../gameplay/dtr-and-raids.md).

## 6. Capture events

In `events.yml`, each event under `events:` is a zone — a `world` and two corners, in any order — with a capture time, a contest policy and optional daily times:

```yaml
events:
  koth:
    display-name: "&6KOTH"
    world: world
    corner-1: {x: 100, y: 60, z: 100}
    corner-2: {x: 115, y: 90, z: 115}
    capture-seconds: 600
    schedule: ["18:00", "21:00"]
    reward-commands: []   # console commands; %team% and %event% are filled in
```

Stand at each corner and read your coordinates with ++f3++. A Citadel is the same thing with a longer `capture-seconds`. A **Conquest** (`conquest:` section) is several zones held at once for points; **Kill the King** (`kill-the-king:` section) needs the warzone of its world, and a world without a bedrock ceiling — not the Nether.

Try each one with `/events start <id>` and stop it with `/events stop <id>`. `/events` lists what runs and what is coming.

Reward commands run from the console. KOTH, Citadel and Conquest rewards know `%team%` and `%event%` (a capture belongs to a team, so there is no `%player%`); Kill the King's know `%player%` and `%event%`. See [Capture events](../gameplay/events.md).

## 7. Mountains

`resourcenodes.yml` defines regions that refill with a weighted palette of blocks on a timer anchored to midnight. The region cannot be built in, can be mined only for its palette by default, cannot be claimed, and resists explosions. By default a refill only fills air, so a region placed under water or inside stone refills nothing. Try one with `/resourcenode refill <id>`. See [Mountains](../gameplay/mountains.md).

## 8. Map phases

`phases.yml` holds SOTW, EOTW and the Purge.

- **SOTW** opens the map: `/sotw start 2h`, or a `sotw.start-at: "2026-10-01 18:00"` date.
- **EOTW** closes it: `/eotw start`, or `eotw.start-at`.
- **The Purge** — every team raidable for a while — is optional: `/purge start 30m`, or daily times in `purge.schedule`.

Durations are typed `90` (seconds), `45s`, `30m`, `2h`, `1d` or combined like `1h30m`. See [SOTW, EOTW and the Purge](../gameplay/map-phases.md).

## 9. Kits

Kits are built in game, not in YAML: equip the loadout, then `/kit create <id> [cooldown-seconds] [permission]`. It is saved with armour, enchantments and item names. Players take it with `/kit <id>`, and arrange its items with `/kit layout <id>`.

For kitmap, a sign whose first line is `[Kit]` and second line the kit id hands it out on click (creating one needs `hcfcore.kit.sign`), and `kits.yml`'s `clear-before-giving: true` gives exactly the kit. Signs work anywhere, spawn included. See [Kits and abilities](../gameplay/kits.md).

## 10. What the plugin leaves to you

Most of these ship neutral or empty on purpose — the plugin does not decide your server's balance for you — and the rest ship an example worth checking. Go through them all:

| Where | What | As shipped |
|---|---|---|
| `teams.yml` | Team Points scale (`per-kill`, `per-death`...), KOTH `points-per-capture` and `max-counted-captures` | all `0` |
| `killstreaks.yml` | streak rewards | none |
| `limiters.yml` | enchantment caps, potion caps, blocks per claim | none |
| `kits.yml` | abilities (partner items) | none |
| `enchants.yml` | custom enchants | nine well-known ones |
| `schedule.yml` | tips, daily announcements, custom timers, key-all commands | tips off, the rest empty |
| `ui.yml` | tab list header and footer | off |
| `pvp.yml` | knockback and attack speed | off |
| `pvp.yml` | `strength-nerf.vanilla-bonus-per-level` | `3.0`, **to verify** for your version — see the file |
| `staff.yml` | the strike ladder | half the team's points at strikes 1 and 2, disbanded at 3 |
| every reward list | `reward-commands` of events | empty |

Also worth a look: `classes.yml` (the five classes and their numbers, no team limit), `economy.yml` (starting balance 100), `pvp.yml` (deathban 1 hour, combat tag 30 seconds), `chat.yml` (format), `ui.yml` (scoreboard lines), `staff.yml` (staff mode toolbar).

## 11. Holograms and leaderboards

`/hologram create <id> <text>` puts a hologram where you stand; `addline`, `setline`, `movehere` finish it. Leaderboard lines use `%top_<board>_<rank>_name%` and `%top_<board>_<rank>_value%`:

```text
/hologram create kills &6&lTop kills
/hologram addline kills &f1. %top_kills_1_name% &7- &c%top_kills_1_value%
```

See [Holograms](../server/holograms.md).

## 12. Before opening

- [ ] Every example event and Mountain moved or deleted, times given to those that should run by themselves, time zones written.
- [ ] Spawn claimed and safe; world spawn point set; warzone configured.
- [ ] Staff ranks hold their nodes.
- [ ] The neutral values of step 10 chosen.
- [ ] Kits created, rewards filled.
- [ ] MySQL in production, with the password in `HCFCORE_MYSQL_PASSWORD` ([Installation](installation.md#keep-the-password-out-of-the-file)).
- [ ] A SOTW scheduled or ready to start.
