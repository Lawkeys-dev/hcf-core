# Changelog

Every release of HCFCore, newest first. Versions follow [Semantic Versioning](https://semver.org/), and each is on the [releases page](https://github.com/Lawkeys-dev/hcf-core/releases) with its jar.

A release gathers several changes: they collect under **Unreleased** as they reach `main`, and are published together. **HCFCore is in pre-release** (`0.x`) until `1.0.0`: a minor version may still change a setting's name or meaning — the notes say so, and what to do, since your configuration files are never rewritten.

## [Unreleased]

### Added
- **Elevator signs** (`elevators.yml`): a sign written `[Elevator]` over `Up` or `Down` takes whoever right-clicks it up or down its column — straight to the next elevator sign in that column, whatever lies between, or, for a sign alone in its column, to the next floor. Refused on an enemy's land unless it is raidable; optionally kept to one's own land or refused in combat.
- **A theme for the whole plugin** (`theme.yml`): eight colours by role, a message prefix, a list symbol, small-capital titles, framed menus. Every text is written with tokens — `{primary}`, `{secondary}`, `{muted}`, `{success}`, `{error}`, `{prefix}`, `{bullet}`, `{action}` — so a colour changed there changes everywhere. Shipped as "Or royal": gold and cream, a bold `HCF »` on the announcements, `Label ➥ value` scoreboard rows, black-framed menus with gold corners. Hex colours (`&#rrggbb`) now work in any text.
- **Gradients**: a colour of `theme.yml` may be `"#F5B32E>#FF4D3D"` — two colours or more — and the text it colours fades letter by letter.
- **A whole look in one file**: `theme.yml` may also set texts (`messages`, any key of `lang/en.yml`), the chat line (`chat`) and the Lunar nametags (`nametags`), in place of the other files.
- **The Theme Builder** ([online](https://lawkeys-dev.github.io/hcf-core/tools/theme-builder.html)): design the whole look — colours and gradients, prefix, menus, scoreboard, tab list, chat, nametags, holograms — with live previews in the game's typeface, items and skins, from 14 palettes, and download a complete `theme.yml` and `ui.yml`, one by one or zipped. It reads a `theme.yml` you already have.
- **Two tab lists** (`ui.yml`, `tablist.style`): **the HCF grid** — four columns of twenty cells in place of the player list: player info and location, team and members, server and events, top teams, every cell yours to write, **with heads** — an icon beside each category title, each member's face beside their name, a dark grey square in every other cell, and any account's skin or any mineskin.org skin wherever you want one — set in the Theme Builder by name or mineskin.org link — and **the classic list**, each player written with their **LuckPerms prefix and suffix** and ordered by rank, kills or name. `auto`, as shipped, shows the grid on an HCF server and the list on a kitmap. The grid needs **nothing installed**: the plugin sends the server's own tab list packets. The Theme Builder previews and writes both, and lets you change a title's head by clicking it in the preview — a player's name or a mineskin.org link.
- Menus page themselves when their items do not fit — `/ability` has two pages, `/tickets` is no longer capped at 54.

- **Strikes by offence**: a strike is given for an offence — cheating (50% of the team's points), bug abuse, kill boosting (40%), teaming (35%), other (25%) — and a team is disbanded at its 3rd active strike, whatever they were for (`staff.yml`, `strikes.offences` and `disband-at`). `/strike add <team|player> <offence> [details]`; `/strike offences` lists them. `/team show` writes a struck team's `Strikes: N`; a strike is no longer announced in the general chat — staff are told, and the team's members when it disbands them — and the details staff add are for staff only.

### Changed
- The tab list is **on by default**, as the HCF grid (or the classic list on a kitmap), and its header and footer are redrawn every second — they were sent once, on joining. *An existing `ui.yml` keeps its `tablist: enabled: false`; its old `header` and `footer` are no longer read: take the new `tablist` section from the jar and put your lines in `hcf` or `classic`.*
- Staff mode: the Random Teleport is an eye of ender, and the Vanish switch a grey dye that turns green while vanished (`vanished-material`, a new toolbar setting). *An existing `staff.yml` keeps its items: set `material: ENDER_EYE` in slot 1, and `material: GRAY_DYE` with `vanished-material: LIME_DYE` in slot 7.*
- Strikes: the ladder (`strikes.ladder`, a sanction by number of strikes) is replaced by offences and `disband-at`. *A `staff.yml` with a `ladder` keeps working with the shipped offences, and says so: move its numbers to `strikes.offences` and `disband-at`. `/strike add` now takes an offence before the details.*
- Every shipped message, menu, scoreboard row, hologram line and chat format uses the theme. The Lunar nametags' team line uses the theme too. *An existing `lang/en.yml`, `ui.yml` and the other files keep their look: take the new ones from the jar for the theme's, then put your changes back. The Pocket Bard menu is 27 slots, its sets at 10, 12, 14 and 16. `staff.ticket.menu.more` is gone.*

### Fixed
- `%online%` counted vanished staff, so the scoreboard and the tab list gave a vanished player away. It now counts the players each viewer can see; a vanished teammate also shows offline in the tab list's members to whoever cannot see them.

## [0.6.0] - 2026-09-19

Partner items, and the cooldowns of an HCF server: the ender pearl, golden apples and the like, all on the scoreboard and on Lunar Client. The scoreboard can be trimmed by each player in `/settings`.

*Tried in game on Paper 26.2 with two players, except the damage a sword block takes off, which still awaits a two-player test.*

### Added
- **Partner items**: 42 abilities built in (`abilities.yml`) — Switcher, Thunderbolt, Combo, Lucky Mode, Rage Ball, Crafting Chaos, Focus Mode, Ninja Track, Portable Archer, Invisibility, Time Warp, Pocket Bard, Berserk, Close Call, Switch Stick, Belch Bomb, Reverse Ninja Track, Rose Thorn, Pumpkin Reaper, Hulk Smash, Sticky Web, Med Kit, Grappling Hook, Nausea Axe, Bunny Hop, Ice Berg, Antidote, Golden Head, Grabber, Poisonous Potato, Fake Pearl, Rocket, Combo Fish, Anti-Build Bone, Rotten Egg, Rage Strength, Olympia, Baguette, Sun, Scrambler, Lucky Bard, Disarmer Wand — and a `commands` type for your own. 38 types, several generic: `effects`, `hit-effects`, `thrown-effects`. An item may stay and count its uses on its durability bar instead of being used up (`uses`): the Olympia, 30 shots, the Pumpkin Reaper and the Nausea Axe, 10 each, spent only when they work, and the Portable Archer, 5. The Pocket Bard has no cooldown; the items it gives wait 60 seconds between two uses, each set apart, and the shared cooldown. The Ninja Track goes to the last player you hit, the Reverse Ninja Track to the last who hit you, 10 seconds back at most (types `ninja-track` and `reverse-ninja-track`). The Sun catches and counts only the enemies in range. A chance that misses (Nausea Axe, Pumpkin Reaper, Disarmer Wand) starts the cooldown but keeps the item and its uses. A shared cooldown after any ability (10 s), and zones where none works (a safe zone, a Citadel's claim, a running event's zone, the Nether, the End). `/ability` shows them with your cooldowns; `/ability give` hands one out.
- **Ender pearl cooldown**: 15 seconds between two pearls (`pvp.yml`, `ender-pearl-cooldown`), shown on the scoreboard (`%pearl_line%`), on the pearls in the hotbar and on Lunar Client (`apollo.yml`, `ender-pearl`). A death ends it. Until it is over, teleport commands are refused as in combat — `/spawn`, `/team hq`, `/team stuck`, `/top`, `/world` (`block-teleport`). *An existing `ui.yml` needs `"%pearl_line%"` added to its lines to show it; `pvp.yml` and `apollo.yml` work without the new keys, with the defaults.*
- **Item cooldowns** (`pvp.yml`, `item-cooldowns`): the Gapple waits 1 hour, the Crapple 10 seconds, the chorus fruit 15, the totem 2 minutes — any item can be added. A use meanwhile is refused; a totem on cooldown saves nobody. Kept on the player, so the Gapple's hour survives logouts and restarts; shown on the scoreboard (`%cooldown_<id>_line%`), on the item and on Lunar Client. *An existing `ui.yml` needs the `%cooldown_<id>_line%` rows added to show them.*
- **`/cooldown reset <player> [what]`** ends a player's cooldowns — `all` (the default), `abilities`, `global`, `pearl`, `items`, or one ability's or item's id (`hcfcore.cooldown.admin`).
- **The scoreboard, section by section, in `/settings`**: team, statistics, balance, combat tag, cooldowns, class, events, timers. A row of `ui.yml` belongs to a section by a tag at its start — `[team]&cTeam: &f%team%` — placed as you like. *In an existing `ui.yml`, tag the rows; in `settings.yml`, add the `scoreboard-<section>` switches to `offered`.*
- **Lunar Client shows every cooldown**: besides the combat tag, countdowns and partner items, now the ender pearl, the item cooldowns, the shared partner item cooldown, the Pocket Bard's sets, a class's click items and backstab, and the crowbar — each switchable in `apollo.yml` (`ability-global-icon`, `classes`, `crowbar`). *An existing `apollo.yml` works without the new keys.*

### Changed
- A class's held effect ignores an item carrying this plugin's data — a Pocket Bard's blaze powder in a Bard's hand gives the Pocket Bard's effect on a right-click, never the class's held Strength too, as its clicks already did.
- Partner items moved from `kits.yml` to `abilities.yml`, and `/kit ability` became `/ability give` (permission `hcfcore.ability.admin`). *A `commands` ability of your `kits.yml` goes under `abilities:` in `abilities.yml` with `type: commands`; items already handed out keep working if their id is kept. A killstreak or redeem command using `kit ability` becomes `ability give`.*

## [0.5.0] - 2026-09-18

Everyday commands, and the fixes of a first round of in-game tests: classes, the Bard, classic combat, effect commands.

*Tried in game on Paper 26.2 with two players, except the damage a sword block takes off, which awaits a two-player test.*

### Added
- **Everyday commands**, as an essentials plugin gives them: `/clearinventory` (`/ci`, `/clearinv`), `/feed` (`/eat`), `/fly`, `/god`, `/flyspeed`, `/walkspeed`, `/hat`, `/suicide`, `/extinguish` (`/ext`), `/workbench` (`/wb`, `/craft`), `/anvil`, `/enderchest` (`/ec`, `/echest`), `/i` (`/giveitem`), `/tphere` (`/s`), `/tppos`, `/gmc`, `/gms`, `/gma`, `/gmsp`, `/day`, `/night`, `/sun`, `/rain`. One permission each, `hcfcore.general.<command>`, and `.others` to act on another player; operators by default. `/tpa`, `/home`, `/back` and `/near` are left out on purpose.
- A test reads every shipped YAML file with the parser the server uses: a file that does not parse fails the build.
- Classes: a class may have its own `warmup-seconds`. The shipped Diamond has none — it gives no effect, so there is nothing to wait for. *In an existing `classes.yml`, add `warmup-seconds: 0` under `diamond`.*
- Classes: `include-self` on a held or click effect decides whether a team effect reaches its user too. The shipped Bard gives Strength I to the team but not to itself; its Strength II burst reaches it as well. *In an existing `classes.yml`, add `include-self: false` under the Bard's held `BLAZE_POWDER`.*

### Changed
- Classes: the archer tag adds 15% damage as shipped, not 25% (`damage-multiplier: 1.15`). *An existing `classes.yml` keeps its value: set it under the Archer's `archer-tag`.*
- Every name and lore line the plugin writes on an item is upright, no longer in the game's italics: `/dyes`, `/settings`, the ticket menu, the staff toolbar, partner items, the King's kit, the crowbar, custom enchant books, `/rename`.
- Classes: held effects follow the scroll wheel — an item's effect comes the moment it is in hand, and is renewed four times a second instead of once (`held-effect-interval-ticks` in `classes.yml`, 5 ticks). *Existing files do without the setting: the default applies.*
- Classes: an effect a class gives its user alone (the Rogue's jump, the Archer's speed) says how long it lasts — *Used Jump Boost V - applied for 5s* — rather than how many players it reached, which is for the Bard's team effects.
- Classes: the warmup and the energy show on the scoreboard only (`%class_line%`, `%class_energy_line%` in `ui.yml`); the warmup chat message ships empty. A burst says the energy it cost and what is left. *An existing `lang/en.yml` keeps its warmup message: set `classes.warmup: ""` to silence it; an existing `ui.yml` needs the two lines added to its scoreboard to show them.*

### Fixed
- Classic combat: the modern regeneration still spent a hurt player's hunger fast — the game charges it even when its heal is refused. The food bar's own regeneration is now held off while classic regeneration is on, and given back otherwise.
- Classic combat: a rod's bobber stayed on the player it hit until reeled in, pulling them. It comes back at once (`fishing-rod.remove-hook`, on as shipped), and reeling in a hooked player is refused, so a right-click at the moment of the hit pulls nobody.
- The effect commands' messages showed their raw key (`effect-commands.on`): YAML reads a key `on` or `off` as a boolean. They are `given` and `taken` now, and a test refuses such a key in every shipped file.

### Documentation
- The documentation reviewed for consistency: effect caps and commands in every summary, the `classes` messages, effect commands in their own FEATURES section, and which modules still await their in-game test.

## [0.4.0] - 2026-09-18

Potions, effects and the Bard.

### Added
- **Effect caps** (`limiters.yml`, `effects.caps`): the highest level an effect may have on a player, whatever gives it — potion, class, custom enchant, the King, golden apple, beacon, command, another plugin. `/hcf reload` and joining bring down what players already have.
- **Effect commands** (`effect-commands.yml`): `/speed`, `/strength` and your own give an effect at a configured level until death; typing the command again takes it off. Shipped: `/speed` (`/sp`, Speed II), `/strength` (`/str`, Strength I), `/resistance` (`/res`), `/regeneration` (`/regen`), `/jumpboost` (`/jb`, Jump Boost II), `/fireresistance` (`/fres`, `/fr`), `/invisibility` (`/invis`, `/invi`), `/haste` (`/hst`, Haste II), `/nightvision` (`/nv`). Permission `hcfcore.effect.<command>`, operators only by default.
- Versioning: releases with their jar, this changelog, and a release workflow that refuses a tag not matching the plugin's version.

### Changed
- A forbidden potion (capped at `0` in `potions.caps`) is no longer brewed, and drinking, throwing or shooting one is refused with a message, the potion kept, instead of being used up for nothing.
- The shipped Bard: holding **gunpowder** gives the team Invisibility I. Every held effect lasts **8 seconds** after the item is put away, 5 before. The **spider eye** Wither II burst is removed; a click effect with `targets: enemies` still does it. *An existing `classes.yml` is not changed: add the `GUNPOWDER` block under `held-effects`, set `seconds: 8` on each held effect, and remove `SPIDER_EYE` from `click-effects` by hand.*

### Fixed
- A class turning off could take an infinite effect it had not given.

## [0.3.0] - 2026-09-18

Classic 1.7.10 combat.

### Added
- **Classic combat** (`combat: classic` in `config.yml`, tuned in `pvp.yml`'s `legacy-combat`): no attack cooldown or sweep, criticals while sprinting, sword blocking, 1.7 knockback, no off-hand or shields, 1.7 potion and pearl throws, no pearl cooldown, 1.7 regeneration and golden apples, rodding.
- 1.7 weapon damage — swords harder, axes softer, shown in the tooltip — and 1.7 Sharpness, +1.25 per level. A blow is rebuilt the 1.7 way: weapon, Strength, critical, Sharpness, so a critical or Strength never multiplies Sharpness.
- 1.7 Strength, a percentage of the weapon's damage (+130% per level), with its own nerf, a percentage too (`legacy-combat.strength.nerf`, +65%).

### Fixed
- The Strength nerf took the Strength bonus out of arrows, which never carried it.

## [0.2.0] - 2026-09-18

Classes and events.

### Added
- **Classes**: Diamond, Bard, Archer, Rogue, Miner, and your own in `classes.yml` — armour set, passive, held and click effects, energy, archer tag, backstab. `/class`.
- Dyed sets (`dye-effects`): an Archer's arrows have a 20% chance to give an effect by the dye colour of the set — green Poison I, black Wither I, blue Slowness I. `/dyes` lists them.
- Citadels in a claimed Citadel (`citadels:` in `events.yml`): on its land, ender pearls, partner items, chorus fruit, elytra and Riptide are refused at all times; class abilities keep working.

### Changed
- Kill the King: the King's position is a scoreboard row (`%king_location_line%`), and the chat says where the King is once a minute with their health (`announce-interval-seconds`). *`coordinates-interval-ticks` is no longer used.*
- Every configuration example in the documentation is quoted from the shipped files.

## [0.1.0] - 2026-09-18

### Added
- First public version: teams, territory and DTR, combat and deathbans, lives, capture events (KOTH, Citadel, Conquest, Kill the King), Mountains, SOTW/EOTW/Purge, economy with Vault, kits and abilities, custom enchants and limiters, moderation tools, scoreboard and chat, holograms, redeem codes, the Lunar Client integration, and this documentation.

[Unreleased]: https://github.com/Lawkeys-dev/hcf-core/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.6.0
[0.5.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.5.0
[0.4.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.4.0
[0.3.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.3.0
[0.2.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.2.0
[0.1.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.1.0
