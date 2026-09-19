# ui.yml

The scoreboard and the tab list.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/interface.md#scoreboard)

Every example on this page is **taken from the shipped `ui.yml`**. Changes apply with `/hcf reload`, which rebuilds every board. Every placeholder is in [Placeholders](../placeholders.md#scoreboard-and-tab-list).

## Scoreboard

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml:scoreboard"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | The scoreboard |
| `update-ticks` | `20` | How often it is redrawn; 20 is once a second |
| `title` | `&c&lHCF` | Its title |
| `lines` | see below | The rows, top to bottom |

**The rows.** A row whose placeholders all come out empty is dropped, so the conditional rows — combat, cooldowns, events, class, timers — appear only when they have something to say. At most 15 are shown.

**Sections.** A row starting with a tag belongs to a section of the board, which **each player may hide** in `/settings`: `[team]`, `[stats]`, `[balance]`, `[combat]`, `[cooldowns]`, `[class]`, `[events]`, `[timers]` — the setting `scoreboard-<section>` of `settings.yml`. Which rows carry which tag is yours; an untagged row always shows, and so does one whose tag has no setting.

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml:scoreboard-lines"
```

## Tab list

**How it plays:** [:octicons-arrow-right-24: the two styles](../../gameplay/interface.md#tab-list)

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | The tab list; `false` leaves the game's own |
| `style` | `auto` | `hcf` the grid, `classic` the player list, `auto` the grid on HCF and the list on a kitmap (`config.yml`, `kitmap-mode`) |
| `update-ticks` | `20` | How often it is redrawn; only what changed is sent |

### The HCF grid

Needs the **PacketEvents** plugin, 2.13 or later for Minecraft 26.2 — see [Integrations](../../server/integrations.md#packetevents). Without it, `hcf` shows the classic list and the console says so.

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml:tablist-hcf"
```

| Key | As shipped | What it does |
|---|---|---|
| `header`, `footer` | lines | Above and below the grid |
| `latency` | `0` | The connection bars every cell shows, in milliseconds: `0` full bars, below `0` none |
| `skin.texture`, `skin.signature` | empty | The default head, of every cell without a head of its own, as [mineskin.org](https://mineskin.org) gives it; empty for the game's default head — the same one in every cell |
| `column-1` … `column-4` | see above | Twenty cells each, top to bottom. An empty line is a blank cell; so is a cell whose placeholders are all empty — the grid never shifts. A longer column is cut at twenty, and the console says so |

**Heads.** A cell may start with the head it shows:

| Tag | Head |
|---|---|
| `[head:self]` | The viewer's own |
| `[head:member:3]` | The member of `%member_3%` |
| `[head:top:1]` | The leader of `%top_team_1%` |
| `[head:MHF_Chest]` | Any Minecraft account's skin, by name. The `MHF_` accounts are the classic icons: `MHF_Chest`, `MHF_Question`, `MHF_Exclamation`, `MHF_ArrowRight`, `MHF_Present1`, `MHF_TNT`… |

An online player's head is theirs at once; any other is asked of Mojang once, off the main thread, and remembered until the server stops — the default head shows meanwhile, and in a blank cell. An account that does not exist is reported once in the console.

### The classic list

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml:tablist-classic"
```

| Key | As shipped | What it does |
|---|---|---|
| `header`, `footer` | lines | Above and below the list |
| `name` | `%prefix%{secondary}%player%%suffix%` | How each player is written: `%prefix%` `%suffix%` (LuckPerms), `%player%`, `%team%`, `%team_tag%`, `%kills%`, `%ping%` — that player's own |
| `sort` | `rank` | `rank`: the weight of their LuckPerms primary group, heaviest first; `kills`: most first; `name`: alphabetical. Names break ties |

The words of the ready-made rows — the wilderness, a member, a top team, the team tag, the eight directions — are in `lang/en.yml` under `ui.tab`.

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/ui.yml).

<div class="hcf-shipped" markdown>

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml"
```

</div>
