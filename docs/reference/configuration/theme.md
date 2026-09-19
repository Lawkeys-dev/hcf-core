# theme.yml

The look of the whole plugin: its colours by role, its message prefix, its menus. Every text the plugin shows — `lang/en.yml`, the scoreboard, menus, holograms, the names and lines of the other files — is written with the theme's **tokens** instead of colour codes: change a colour here and it changes everywhere at once.

**How it plays:** [:octicons-arrow-right-24: Chat, scoreboard and settings](../../gameplay/interface.md)

!!! tip "Theme Builder"
    Design your theme with live previews — chat, menus, scoreboard, tab — and download a `theme.yml` ready to drop into `plugins/HCFCore/`: **[open the Theme Builder](../../tools/theme-builder.html)**. It also reads a `theme.yml` you already have, and writes the scoreboard's rows for `ui.yml`.

Every example on this page is **taken from the shipped `theme.yml`**. Changes apply with `/hcf reload`.

## Colours

```yaml title="theme.yml"
--8<-- "src/main/resources/theme.yml:colors"
```

Each role is a hex colour, `#rrggbb` — **or a gradient**, two colours or more: `"#F5B32E>#FF4D3D"`, `"#5EF0A8>#56C8FF>#B07CFF"`. The text a gradient colours fades from one to the next, **letter by letter**, up to the next colour change; a bold or an underline inside it stays on every letter. A gradient suits `primary` best — the prefix, titles and scoreboard labels — and reads badly on a long sentence.

A text uses a role as a token:

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

## A whole look in one file

```yaml title="theme.yml"
--8<-- "src/main/resources/theme.yml:overrides"
```

A theme may also carry what other files hold, so **one `theme.yml` is all a server imports**. Each section is optional; what it sets is used in place of the other file's, what it leaves out stays theirs:

| Section | In place of | Holds |
|---|---|---|
| `messages` | `lang/en.yml` | Any text, by its key — nested as in `lang/en.yml`, or dotted on one line |
| `chat` | `chat.yml` | `format`, `kills-format`: how a chat line looks |
| `nametags` | `apollo.yml` | `team-line`, `name-line`, `colors` by relation: the Lunar Client nametags |

## Everything you can change in the interfaces

| Interface | What | Where | In the Theme Builder |
|---|---|---|---|
| Every text | Colours by role, gradients, the prefix, the list symbol | `theme.yml` | ✓ |
| Chat | A player's line, the kills badge | `chat.yml` (`format`, `kills-format`), or `theme.yml` `chat` | ✓ |
| Chat | Team and ally chat | `lang/en.yml` `team.chat`, or `theme.yml` `messages` | ✓ |
| Chat | Every message's wording and colours | `lang/en.yml`, or `theme.yml` `messages` | the tokens |
| Menus | Frame, panes, "click to" line, small-capital titles | `theme.yml` `menus`, `small-caps-titles` | ✓ |
| Menus | Page arrows, page line, the line under a name | `lang/en.yml` `menus`, or `theme.yml` `messages` | ✓ |
| Menus | `/settings`, `/dyes`, `/tickets`, kit layout titles and lines | `lang/en.yml` (`settings`, `classes.dyes`, `staff.ticket.menu`, `kit.layout`) | `/settings` title |
| Menus | `/ability` and Pocket Bard titles, the Pocket Bard's slots | `abilities.yml` (`menu-title`, `pocket-bard`) | — |
| Menus | Which `/settings` switches exist, in which order | `settings.yml` `offered` | — |
| Scoreboard | On or off, refresh, title, rows, sections | `ui.yml` `scoreboard` | ✓ |
| Scoreboard | Each ready-made row (`%combat_line%`...) | `lang/en.yml` `ui.scoreboard`, `classes.scoreboard` | — |
| Tab list | On or off, header, footer | `ui.yml` `tablist` | ✓ |
| Nametags | Team line, name line, colour by relation (Lunar Client) | `apollo.yml` `nametags`, or `theme.yml` `nametags` | ✓ |
| Holograms | Capture zones' title and lines | `lang/en.yml` `events.hologram`, or `theme.yml` `messages` | ✓ |
| Holograms | Your own holograms | in game, `/hologram` ([Holograms](../../server/holograms.md)) | — |
| Items | Partner items', kits', the crowbar's, the staff toolbar's names and lore | `abilities.yml`, `kits.yml`, `crowbar.yml`, `staff.yml` | the tokens |

The **[Theme Builder](../../tools/theme-builder.html)** writes a complete `theme.yml` and `ui.yml`; what it leaves at its shipped value, it leaves out of `messages`, `chat` and `nametags`, so the other files keep theirs. It reads an existing `theme.yml` back, and a link like `…/theme-builder.html?palette=aurora` opens it on one of its 14 palettes.

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/theme.yml).

<div class="hcf-shipped" markdown>

```yaml title="theme.yml"
--8<-- "src/main/resources/theme.yml"
```

</div>
