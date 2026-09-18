# Placeholders

Placeholders are words between `%` signs that the plugin replaces when it shows a text or runs a command. Each place accepts its own set, listed here. The plugin has no PlaceholderAPI integration: these work in HCFCore's own files only.

## Scoreboard and tab list

*`ui.yml` — `scoreboard.lines`, `tablist.header`, `tablist.footer`*

**A scoreboard row whose placeholders all come out empty is dropped** rather than shown blank. That is how the conditional rows work: `%combat_line%` is empty while you are not in combat, so the row is simply not there. A scoreboard shows 15 rows at most.

| Placeholder | Shows |
|---|---|
| `%player%` | Your name |
| `%online%` | Players online |
| `%world%` | The world you are in |
| `%ping%` | Your ping |
| `%team%` | Your team, or "None" |
| `%territory%` | The team owning the chunk you stand in; empty on land nobody owns |
| `%dtr%` | Your team's DTR, e.g. `1.10`; empty without a team |
| `%dtr_coloured%` | The same, green, or dark red with "(raidable)" when your land is open — EOTW and the Purge included |
| `%dtr_max%` | Your team's maximum DTR, which grows with its size |
| `%kills%` `%deaths%` `%kdr%` `%killstreak%` `%playtime%` | Your statistics |
| `%balance%` | Your balance, with the currency symbol |
| `%combat%` / `%combat_line%` | Combat tag time left / a whole row, empty when not tagged |
| `%deathban%` / `%deathban_line%` | Always empty: a deathbanned player is not online to read a board. Kept so an older `ui.yml` that lists them still renders |
| `%focus_line%` | Who your team is focusing |
| `%rally_line%` | Your team's rally point, while it lasts |
| `%phase_line%` | SOTW with its time left, EOTW, or the Purge with its time left; empty when none runs |
| `%event_line%` | The running KOTH or Citadel closest to being captured, with its time left |
| `%king_line%` | The King and the time left, during Kill the King |
| `%king_location_line%` | Where the King is, during Kill the King — redrawn with the board |
| `%conquest_line%` | The running Conquest's leading team and its points |
| `%conquest_zone_1%` … `%conquest_zone_4%` | Each Conquest zone's countdown |
| `%class%` | Your class's name; empty in none |
| `%class_line%` | Your class, or the class warming up with its time left |
| `%class_energy_line%` | Your class's energy, for a class that has some (the Bard) |
| `%archer_tag_line%` | How long you stay archer-tagged |
| `%timer_1%` `%timer_2%` `%timer_3%` | The custom timers (`/timer`, `/keyall`), the one ending soonest first |

The `*_line` placeholders, and the Conquest and timer rows, are whole rows whose wording is in `lang/en.yml` under `ui.scoreboard`, so it is changed in one place.

## Holograms

*`/hologram create`, `addline`, `setline`*

A hologram line can show a leaderboard entry, redrawn every `refresh-seconds` of `holograms.yml`:

| Placeholder | Shows |
|---|---|
| `%top_<board>_<rank>_name%` | The player at that rank |
| `%top_<board>_<rank>_value%` | Their value |

`<board>` is `kills`, `deaths`, `kdr`, `killstreak` or `playtime`; `<rank>` runs from 1 to 100.

```
/hologram create kills &6&lTop kills
/hologram addline kills &f1. %top_kills_1_name% &7- &c%top_kills_1_value%
/hologram addline kills &f2. %top_kills_2_name% &7- &c%top_kills_2_value%
```

## Chat

| Where | Placeholders |
|---|---|
| `chat.yml` — `format` | `%player%`, `%message%`, `%prefix%` and `%suffix%` (from LuckPerms, empty without it), `%kills%` |
| `chat.yml` — `kills-format` | `%value%`, the kill count. `%kills%` in the format is empty for a player with no kills |
| `staff.yml` — `staff-chat.format` | `%player%`, `%message%` |
| `staff.yml` — `broadcast.format` | `%message%` |

## Lunar Client nametags

*`apollo.yml` — `nametags.team-line`, `nametags.name-line`*

`%color%` — the colour for how that player's team relates to yours (`nametags.colors`); `%team%`; `%dtr%`; `%player%`.

## Commands the plugin runs

These run from the console.

| Where | Placeholders |
|---|---|
| `events.yml` — KOTH and Citadel `reward-commands` | `%team%` (the winning team), `%event%` |
| `events.yml` — Conquest `reward-commands` | `%team%`, `%event%` |
| `events.yml` — Kill the King `reward-commands` | `%player%` (the King or the killer), `%event%` |
| `killstreaks.yml` — `commands` and `broadcast` | `%player%`, `%streak%` |
| `kits.yml` — ability `commands` | `%player%` |
| `staff.yml` — toolbar `command` | `%player%` (the player clicked), `%staff%` (you). These run **as the staff member**, not the console |
| `staff.yml` — strike ladder `commands` | `%team%`, `%strikes%` |
| `redeem` — a code's reward commands | `%player%` |
| `schedule.yml` — `key-all.commands` | `%player%`; `key-all.broadcast` takes `%count%` |
| `schedule.yml` — a timer's `end-commands` | `%timer%`, the timer's name |

`crowbar.yml`'s item lore also takes `%uses%`, the uses left.

## Messages

Every message in `lang/en.yml` already contains the placeholders it receives — `%player%`, `%team%`, `%time%` and so on. Move or remove them freely; a placeholder that a message does not receive is shown as typed.
