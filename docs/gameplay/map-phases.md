# SOTW, EOTW and the Purge

*Configured in [`phases.yml`](../reference/configuration/phases.md). Commands: `/sotw`, `/eotw`, `/purge`.*

Three phases shape a map's life. Each starts by command or by itself at a date or time, and ends at a fixed instant — **restarts included**: a SOTW that ends at 20:00 ends at 20:00, however often the server goes down before then.

| | SOTW | EOTW | The Purge |
|---|---|---|---|
| **When** | the opening of the map | the end of the map | whenever the server wants |
| **Default length** | 2 hours | until staff stop it | 30 minutes |
| **PvP** | none, except players who opted in | normal | normal |
| **Deathbans** | none | **until the end of the map** | normal |
| **DTR lost on death** | none | normal | normal |
| **Raidable** | by DTR | **every team** | **every team** |
| **New player claims** | yes, and teams can lock their land | **no** | yes |
| **Spawn** | safe | safe | safe |

## SOTW — Start Of The World

```text
/sotw start 2h     # or /sotw start, for the configured duration
/sotw stop
/sotw              # status, for everyone
```

For its duration — **2 hours** by default (`sotw.duration-seconds`):

- **no PvP anywhere**, **no deathbans**, **no DTR lost**;
- teams can [lock their land](territory.md#lock-sotw-only) against campers.

**`/sotw enable`** lets a player fight early: for the rest of that SOTW they can hit, and be hit by, only players who did the same. **There is no going back** — fighting and then returning to SOTW's shelter would be an exploit.

The no-PvP rule holds even with `pvp.yml` turned off: it is the map's rule.

**By date**: `sotw.start-at: "2026-10-01 18:00"`, in the file's `time-zone`. A scheduled SOTW is a window, from that date to that date plus its duration: a server that was down at the start still gets what is left of it, and once the window is over the date does nothing — a date left over from an earlier map is harmless.

Remaining-time marks are broadcast (`sotw.announce-at-seconds`: 1 h, 30 min, 10 min, 5 min, 1 min, 10 s).

## EOTW — End Of The World

```text
/eotw start
/eotw stop
/eotw              # status
```

Until staff stop it:

- **every team is raidable** whatever its DTR;
- players cannot claim new land (staff still can, for server land);
- **a death bans until the end of the map** — no life can lift it; staff lift those bans with `/pvp lift` after the reset.

Spawn stays a safe zone.

**By date**: `eotw.start-at`. A server that was down at that time still starts EOTW when it comes back, within `start-window-seconds` (1 hour); past that, the date is treated as one left from an earlier map and ignored, so a fresh map never opens straight into EOTW.

## The Purge

```text
/purge start 30m
/purge stop
/purge             # status
```

A window during which **every team is raidable**, whatever its DTR — a temporary EOTW, without EOTW's closed claims or its bans until the end of the map. Deathbans and DTR work as usual. Spawn stays safe.

- **30 minutes** by default (`purge.duration-seconds`).
- Optional daily times: `purge.schedule: ["20:00"]` — empty by default, since when a server purges is its own call. A time passed while the server was off is not replayed.
- **Never during SOTW**: a SOTW that begins ends a running Purge.
- **Refused during EOTW**, when every team is raidable already.

## Together

SOTW and EOTW never run at the same time: one refuses to start during the other, and a scheduled EOTW that falls during a SOTW waits for its end.

## Seeing them

- `/sotw`, `/eotw`, `/purge` — each phase's status.
- `/events` — the agenda lists running and scheduled phases.
- The scoreboard — `%phase_line%` shows SOTW with its time left, EOTW, or the Purge with its time left.
- `/team dtr`, the scoreboard and raid announcements say a team is raidable during EOTW and the Purge — never "no longer raidable" while one runs.

Starting and stopping any phase needs `hcfcore.phase.admin`.
