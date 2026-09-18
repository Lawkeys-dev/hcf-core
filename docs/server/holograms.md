# Holograms

*Configured in [`holograms.yml`](../reference/configuration/holograms.md). Command: `/hologram` (aliases `/holo`, `/holograms`), permission `hcfcore.hologram.admin`.*

Floating text, drawn by Paper's own text displays: nothing to install, and **every client sees them** — not only Lunar Client.

## Making one

```text
/hologram create welcome &c&lWelcome to the server
/hologram addline welcome &7Type &f/team help &7to start
```

| Command | Does |
|---|---|
| `create <id> <text>` | A new hologram where you stand, at eye height, with its first line |
| `addline <id> <text>` | Add a line at the bottom |
| `setline <id> <line> <text>` | Replace a line (numbered from 1) |
| `removeline <id> <line>` | Remove a line |
| `movehere <id>` | Move it where you stand |
| `tp <id>` | Go to it |
| `list` | Every hologram |
| `delete <id>` | Delete it |

Colour codes (`&c`, `&l`...) work.

## Leaderboards

A line can show a leaderboard entry, redrawn every `refresh-seconds` (10):

| Placeholder | Shows |
|---|---|
| `%top_<board>_<rank>_name%` | The player at that rank |
| `%top_<board>_<rank>_value%` | Their value |

`<board>` is `kills`, `deaths`, `kdr`, `killstreak` or `playtime`; `<rank>` runs from 1 to 100.

```text
/hologram create kills &6&lTop kills
/hologram addline kills &f1. %top_kills_1_name% &7- &c%top_kills_1_value%
/hologram addline kills &f2. %top_kills_2_name% &7- &c%top_kills_2_value%
/hologram addline kills &f3. %top_kills_3_name% &7- &c%top_kills_3_value%
```

Only holograms with a leaderboard are redrawn; the others never are.

## Zone holograms

Every capture zone — KOTH, Citadel, each Conquest zone — gets a hologram of its own, drawn by the event module: the time left and who holds the zone while it runs, otherwise when it runs next. They are configured in `events.yml` (`zone-holograms`), not with `/hologram`. See [Capture events](../gameplay/events.md#zone-holograms).

## Nothing left floating

Holograms are **stored in the database, never saved into the world**. They are drawn again whenever their chunk loads. The database is the only copy, so a crash, a removed plugin or a delete made while the chunk was unloaded never leaves text floating in somebody's base.
