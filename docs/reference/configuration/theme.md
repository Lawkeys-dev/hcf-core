# theme.yml

The look of the whole plugin: its colours by role, its message prefix, its menus. Every text the plugin shows — `lang/en.yml`, the scoreboard, menus, holograms, the names and lines of the other files — is written with the theme's **tokens** instead of colour codes: change a colour here and it changes everywhere at once.

**How it plays:** [:octicons-arrow-right-24: Chat, scoreboard and settings](../../gameplay/interface.md)

Every example on this page is **taken from the shipped `theme.yml`**. Changes apply with `/hcf reload`.

## Colours

```yaml title="theme.yml"
--8<-- "src/main/resources/theme.yml:colors"
```

Each role is a hex colour, `#rrggbb`. A text uses it as a token:

| Token | As shipped | Used for |
|---|---|---|
| `{primary}` | `#F5B32E` gold | The prefix, titles, names, scoreboard labels |
| `{secondary}` | `#FFE39A` cream | Values: players, teams, numbers, times |
| `{text}` | `#F7F1E3` | The body of a message, chat |
| `{muted}` | `#B3A88F` | Secondary information, item descriptions |
| `{dark}` | `#5E5540` | Brackets, separators, the list symbol |
| `{success}` | `#8CE36B` | Something done |
| `{error}` | `#F2594B` | Something refused, a cooldown |
| `{warning}` | `#FFD24A` | Announcements, events, a cooldown running |

Legacy codes (`&a`, `&l`, `&r`...) still work anywhere, and so does a hex colour of your own written `&#rrggbb`. A brace a player types — in chat, a ticket — is never read as a token.

## Prefix, list symbol, titles

```yaml title="theme.yml"
--8<-- "src/main/resources/theme.yml:prefix"
```

| Key | As shipped | What it does |
|---|---|---|
| `prefix` | `HCF »` in bold gold | What `{prefix}` becomes. The shipped messages put it on the **announcements**: events, the map's phases, a team's own news (a member joined, an ally, a focus, a rally, raidable), strikes. Add it to any message you like |
| `bullet` | `➥` | What `{bullet}` becomes: the scoreboard's `Label ➥ value` rows, event lines |
| `small-caps-titles` | `true` | Menu and scoreboard titles in small capitals: `ᴀʙɪʟɪᴛɪᴇꜱ` |

## Menus

```yaml title="theme.yml"
--8<-- "src/main/resources/theme.yml:menus"
```

| Key | As shipped | What it does |
|---|---|---|
| `frame` | `full` | `full`: panes all around the menu; `bars`: the top and bottom rows; `none` |
| `pane` · `corner-pane` | black · yellow stained glass | The frame's pane and the four corners'. Panes show no tooltip |
| `action` | `» ` | What `{action}` becomes: the start of an item's "click to" line |

Every menu — `/settings`, `/ability`, the Pocket Bard, `/dyes`, `/tickets`, the kit layout editor — places its items centred inside the frame, a few spread out on one row. **When they do not fit, the menu has pages**, with arrows in its bottom row. The Pocket Bard's sets sit at the slots `abilities.yml` gives them, the frame going around them. The page arrows, and the line under an item's name, are in `lang/en.yml` under `menus`.

## Your own look

Nothing else needs to change: change a role here, `/hcf reload`, and every message, menu and board follows. To reword a message or move a colour inside one, edit `lang/en.yml` ([Messages](../messages.md)). The scoreboard's rows are `ui.yml`'s ([ui.yml](ui.md)).

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/theme.yml).

<div class="hcf-shipped" markdown>

```yaml title="theme.yml"
--8<-- "src/main/resources/theme.yml"
```

</div>
