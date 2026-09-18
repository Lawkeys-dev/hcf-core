# Chat, scoreboard and settings

*Configured in [`chat.yml`](../reference/configuration/chat.md), [`ui.yml`](../reference/configuration/ui.md) and [`settings.yml`](../reference/configuration/settings.md).*

## Public chat

The public chat line is a template:

```yaml title="chat.yml"
--8<-- "src/main/resources/chat.yml:format"
```

which renders as the historic HCF format — **`[50] Rank Player: message`**:

- `%prefix%` and `%suffix%` come from **LuckPerms**, read when the message is sent; empty without it.
- `%kills%` is the player's kill count in `kills-format` — **empty for a player with no kills**, since a new player reading `[0]` beside their name is just noise.
- `%message%` is substituted last, so a player typing `%player%` does not get it expanded. A format without `%message%` is refused when the file loads — it would silence the server without an error.

**What a player types is shown as typed** — their own `&c` stays the two characters `&c` — unless they hold `hcfcore.chat.color` (operators do). Colour and obfuscated text are the tools of spam and of lines dressed up as staff messages, so they are granted, not assumed. The same rule applies in team and ally chat, private messages, the staff channel and `/broadcast`.

**Local chat**: `range-blocks` above 0 makes public chat carry only that many blocks. `0` — the normal HCF setting — is the whole server. The console always receives every message.

`enabled: false` leaves chat exactly as the server renders it.

## Team and ally chat

`/team chat` (alias `c`) cycles your channel: public → team → ally. `/team chat team` picks one directly. A player with no team talks in public rather than into the void.

Team and ally lines are also written to the console and the server log, with the team's name (`log-team-chat`) — a conversation staff cannot read back is a moderation problem. Staff chat wins over team chat: with both on, a staff member's lines go to staff.

## Private messages

| Command | Aliases | Does |
|---|---|---|
| `/msg <player> <message>` | `/tell`, `/w`, `/whisper`, `/m` | Private message |
| `/reply <message>` | `/r` | Answer the last one |
| `/togglepm` | | Turn private messages on or off |
| `/ignore [player]` | | Ignore a player, or list who you ignore |

- Someone who turned their messages off cannot send any either: a one-way conversation is worse than none.
- Ignore lists last for the session only.
- Vanished staff cannot be found by `/msg`, nor by name completion.

## Scoreboard

A flicker-free sidebar, redrawn once a second (`update-ticks: 20`). **The board is a list of template lines** in `ui.yml`, top to bottom:

```yaml title="ui.yml — the shipped rows"
--8<-- "src/main/resources/ui.yml:scoreboard-lines"
```

**A line whose placeholders all come out empty is dropped** rather than left blank. That is how conditional lines work: `%combat_line%` is empty outside combat, so the line simply is not there — and why a server that does not run a module needs no edits: its placeholders are just empty. A scoreboard shows **15 lines at most**; the console warns when more than 15 of yours are always shown.

Every placeholder is listed in [Placeholders](../reference/placeholders.md#scoreboard-and-tab-list). The `*_line` placeholders are whole lines whose wording is in `lang/en.yml` under `ui.scoreboard`.

## Tab list

A header and a footer around the player list, with the same placeholders. **Off by default** — the vanilla list is fine, and a header nobody asked for is one more thing to switch off.

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml:tablist"
```

Set `enabled: true` to show it.

## Statistics and leaderboards

```text
/stats [player]                                            # kills, deaths, K/D, killstreak, playtime
/leaderboard [kills|deaths|kdr|killstreak|playtime]        # aliases /lb, /top10
```

- **K/D** of a player with no death is their number of kills, not infinity.
- **Playtime** is accumulated session by session, and a shutdown closes every session before the final save.
- Leaderboards break ties by name. For a leaderboard in the world, use a [hologram](../server/holograms.md).

## Player settings

Each player can switch parts of the game off for themselves:

```text
/settings                         # a menu: green is on, grey is off, click to toggle
/settings list
/settings <setting> [on|off]
/cobble [on|off]                  # the classic shortcut (alias /cobblestone)
```

| Setting | Controls |
|---|---|
| `scoreboard` | The HCF scoreboard |
| `private-messages` | `/msg` both ways — the same switch as `/togglepm` |
| `tips` | The rotating tips of `schedule.yml` |
| `cobblestone` | Picking up cobblestone (and cobbled deepslate), for mining without filling up |

Choices are **stored**, so they survive restarts. Removing a setting from `settings.yml`'s `offered` list takes it away: it is then on for everybody. `cobblestone.materials` sets what the cobblestone switch leaves on the ground.
