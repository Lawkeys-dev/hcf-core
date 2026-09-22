# Placeholders

Placeholders are words between `%` signs that the plugin replaces when it shows a text or runs a command. Each place accepts its own set, listed here. The plugin has no PlaceholderAPI integration: these work in HCFCore's own files only.

## Scoreboard and tab list

*`ui.yml` — `scoreboard.lines`, and every line of `tablist`*

**A scoreboard row whose placeholders all come out empty is dropped** rather than shown blank. That is how the conditional rows work: `%combat_line%` is empty while you are not in combat, so the row is simply not there. A scoreboard shows 15 rows at most.

| Placeholder | Shows |
|---|---|
| `%player%` | Your name |
| `%online%` | Players online — those you can see: vanished staff are not counted, except by staff who can see them |
| `%world%` | The world you are in |
| `%ping%` | Your ping |
| `%team%` | Your team, or "None" |
| `%territory%` | The team owning the land you stand on; empty on land nobody owns |
| `%dtr%` | Your team's DTR, e.g. `1.10`; empty without a team |
| `%dtr_coloured%` | The same, green, or dark red with "(raidable)" when your land is open — EOTW and the Purge included |
| `%dtr_max%` | Your team's maximum DTR, which grows with its size |
| `%kills%` `%deaths%` `%kdr%` `%killstreak%` `%playtime%` | Your statistics |
| `%balance%` | Your balance, with the currency symbol |
| `%combat%` / `%combat_line%` | Combat tag time left / a whole row, empty when not tagged |
| `%pearl%` / `%pearl_line%` | Ender pearl cooldown left / a whole row, empty when none runs |
| `%cooldown_<id>%` / `%cooldown_<id>_line%` | An item cooldown of `pvp.yml` by its id — `%cooldown_notch-apple_line%` — left / a whole row, empty when none runs |
| `%deathban%` / `%deathban_line%` | Always empty: a deathbanned player is not online to read a board. Kept so an older `ui.yml` that lists them still renders |
| `%focus_line%` | Who your team is focusing |
| `%rally_line%` | Your team's rally point, while it lasts |
| `%phase_line%` | SOTW with its time left, EOTW, or the Purge with its time left; empty when none runs |
| `%event_line%` | The running KOTH or Citadel closest to being captured, with its time left |
| `%king_line%` | The King and the time left, during Kill the King |
| `%king_location_line%` | Where the King is, during Kill the King — redrawn with the board |
| `%conquest_line%` | The running Conquest's leading team and its points |
| `%conquest_zone_1%` … `%conquest_zone_4%` | Each Conquest zone's countdown |
| `%dtc_line%` | The running DTC: common health, or (`PER_TEAM`) the leading team's breaks |
| `%dtc_team_line%` | Your own team's breaks in the running DTC; empty without a team or a run |
| `%last_break_line%` | The running Last Break's common health |
| `%slide_line%` | The running Slide's leading team and its points |
| `%slide_top_1%` … `%slide_top_3%` | The Slide's live top 3 teams |
| `%totem_line%` | The running Totem: the team on its way and how many blocks it has broken |
| `%class%` | Your class's name; empty in none |
| `%class_line%` | Your class, or the class warming up with its time left |
| `%class_energy_line%` | Your class's energy, for a class that has some (the Bard) |
| `%archer_tag_line%` | How long you stay archer-tagged |
| `%timer_1%` `%timer_2%` `%timer_3%` | The custom timers (`/timer`, `/keyall`), the one ending soonest first |

The `*_line` placeholders, and the Conquest and timer rows, are whole rows whose wording is in `lang/en.yml` under `ui.scoreboard`, so it is changed in one place.

### Tab list only

*`ui.yml` — the HCF grid's cells, and both styles' headers and footers*

| Placeholder | Shows |
|---|---|
| `%prefix%` `%suffix%` | Your LuckPerms prefix and suffix; empty without LuckPerms |
| `%x%` `%y%` `%z%` | Where you stand, in blocks |
| `%direction%` | Which way you face: `N`, `NE`, `E`… (`ui.tab.directions`) |
| `%location%` | The claim you stand in, or the wilderness |
| `%team_name_line%` | Your team's name, or how to make one |
| `%members_online%` `%members_total%` | Your team's members online — a vanished one counts as offline to whoever cannot see them — and in all |
| `%team_balance%` `%team_points%` `%team_leader%` | Your team's bank, points and leader |
| `%members_title%` | The members heading; empty without a team |
| `%member_1%` … `%member_20%` | Your team's members, online first, then by rank, with the rank's marker |
| `%top_team_1%` … `%top_team_10%` | The teams with most points |
| `%no_event_line%` | A row saying no event runs, when none does — no phase, capture event, King, Conquest, DTC, Last Break, Slide, Totem or timer |

**In the classic list's `name`**, the placeholders are the listed player's own: `%prefix%` `%suffix%` `%player%` `%team%` `%team_tag%` `%kills%` `%ping%`.

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
| `events.yml` — DTC, Last Break, Slide and Totem `reward-commands` | `%team%` (the winning team), `%event%` |
| `killstreaks.yml` — `commands` and `broadcast` | `%player%`, `%streak%` |
| `abilities.yml` — a `commands` ability | `%player%` |
| `staff.yml` — toolbar `command` | `%player%` (the player clicked), `%staff%` (you). These run **as the staff member**, not the console |
| `staff.yml` — a strike offence's `commands` | `%team%`, `%strikes%`, `%offence%` |
| `redeem` — a code's reward commands | `%player%` |
| `schedule.yml` — `key-all.commands` | `%player%`; `key-all.broadcast` takes `%count%` |
| `schedule.yml` — a timer's `end-commands` | `%timer%`, the timer's name |

`crowbar.yml`'s item lore also takes `%uses%`, the uses left.

## Messages

Every message in `lang/en.yml` already contains the placeholders it receives — `%player%`, `%team%`, `%time%` and so on. Move or remove them freely; a placeholder that a message does not receive is shown as typed.
