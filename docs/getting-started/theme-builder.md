# Theme Builder

**[Open the Theme Builder :material-palette:](../tools/theme-builder.html){ .md-button .md-button--primary }**

The Theme Builder designs the look of HCFCore in your browser, and writes the files for it. Nothing to install, nothing to edit by hand.

## What it does

- **Start from a palette** — 14 of them, 9 fading from one colour to another letter by letter — then change any colour, or turn it into a gradient of its own.
- **Set the whole look**: the message prefix and the list symbol, the menus' frame and panes, the scoreboard's title, rows and separators, the tab list in both its styles — the HCF grid's four columns, the classic list's names and order — the chat line, team and ally chat, Lunar Client nametags, capture zone holograms.
- **See it as players will**, in the game's own typeface and items: chat, a few menus with their tooltips, the scoreboard, the tab list as the HCF grid or the classic list, nametags above real skins, a hologram.
- **Pick the tab list's heads**: click a title's head in the preview of the HCF grid — or open *Tab list heads* — and type a player's name, paste a [mineskin.org](https://mineskin.org) link or id, or `self` for the viewer's own head. Only those two kinds of skin work in game: the game shows a head only if Mojang signed its skin, which a player's own skin and a mineskin.org skin are.
- **Download two files**, `theme.yml` and `ui.yml` — one by one or in a zip — complete and ready to use.
- **Read back a theme** you already have: paste your `theme.yml`, change it, download it again.

## Using what it writes

1. Put `theme.yml` and `ui.yml` in `plugins/HCFCore/`, replacing the ones there.
2. Run `/hcf reload`.

Every message, menu, board and hologram follows. The chat line, the nametags and the texts it changes travel inside `theme.yml` itself, so `chat.yml`, `apollo.yml` and `lang/en.yml` need no edit. What each setting does, and what else can be changed in the interfaces, is in [theme.yml](../reference/configuration/theme.md).

!!! tip "Share a palette"
    A link to the builder can open it on a palette: `theme-builder.html?palette=aurora`, `?palette=molten-gold`, `?palette=neon`...

*The previews use [Minecraft-Font](https://github.com/IdreesInc/Minecraft-Font), an open rendition of the game's typeface under the SIL Open Font License, and show the game's item textures and players' skins — the sample players are skins generated on [mineskin.org](https://mineskin.org), kept in the page; a name you type is looked up through [Ashcon](https://api.ashcon.app) or [PlayerDB](https://playerdb.co), since Mojang's own API does not answer a web page —, loaded from public services; none of them is part of HCFCore.*
