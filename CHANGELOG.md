# Changelog

Every change to HCFCore, newest first. Versions follow [Semantic Versioning](https://semver.org/): **every commit on `main` is a version**, tagged `v<version>` and published on the [releases page](https://github.com/Lawkeys-dev/hcf-core/releases) with its jar.

**HCFCore is in pre-release** (`0.x`): a minor version (`0.x.0`) adds a feature or changes how something plays; a patch (`0.x.y`) changes default values, fixes a defect or documents. Until `1.0.0`, a minor version may change a setting's name or meaning — the notes below say so, and what to do.

## [0.13.2] - 2026-09-18

### Added
- Versioning: every commit on `main` is a version, tagged and released with its jar — this file, and a release workflow that builds the jar of each tag and checks the tag matches the plugin's version.

## [0.13.1] - 2026-09-18

### Changed
- The shipped Bard: holding **gunpowder** gives the team Invisibility I, for 10 seconds after it leaves the hand, with no burst. The **spider eye** Wither II burst is removed; a click effect with `targets: enemies` still does it for a server that wants one. *Existing `classes.yml` files are not changed: add or remove the lines by hand.*
- `effect-commands.yml` ships three more commands: `/invisibility` (`/invis`, `/invi`), `/haste` (`/hst`, Haste II), `/nightvision` (`/nv`).

## [0.13.0] - 2026-09-18

### Added
- **Effect commands** (`effect-commands.yml`): `/speed`, `/strength` and your own give an effect at a configured level until death; typing the command again takes it off. Shipped at a Bard's held effects: Speed II, Strength I, Resistance I, Regeneration I, Jump Boost II, Fire Resistance I, with aliases. Permission `hcfcore.effect.<command>`, operators only by default.

### Fixed
- A class turning off could take an infinite effect it had not given.

## [0.12.0] - 2026-09-18

### Added
- **Effect caps** (`limiters.yml`, `effects.caps`): the highest level an effect may have on a player, whatever gives it — potion, class, custom enchant, the King, golden apple, beacon, command, another plugin. `/hcf reload` and joining bring down what players already have.

## [0.11.0] - 2026-09-18

### Changed
- A forbidden potion (capped at `0` in `potions.caps`) is no longer brewed, and drinking, throwing or shooting one is refused with a message, the potion kept, instead of being used up for nothing.

## [0.10.0] - 2026-09-18

### Added
- Classic combat: a Strength nerf of its own, a percentage (`legacy-combat.strength.nerf`, +65% per level instead of 1.7's +130%).

## [0.9.0] - 2026-09-18

### Added
- Classic combat: 1.7 Sharpness, +1.25 per level (`legacy-combat.enchantments`). A blow is rebuilt the 1.7 way — weapon, Strength, critical, Sharpness — so a critical or Strength never multiplies Sharpness.

### Fixed
- The Strength nerf took the Strength bonus out of arrows, which never carried it.

## [0.8.0] - 2026-09-18

### Added
- Classic combat: 1.7 weapon damage (`legacy-combat.weapon-damage`) — swords harder, axes softer, shown in the tooltip.

## [0.7.0] - 2026-09-18

### Added
- **Classic 1.7.10 combat** (`combat: classic` in `config.yml`, tuned in `pvp.yml`'s `legacy-combat`): no attack cooldown or sweep, criticals while sprinting, sword blocking, 1.7 knockback, no off-hand or shields, 1.7 potion and pearl throws, no pearl cooldown, 1.7 regeneration, golden apples and Strength, rodding.

## [0.6.2] - 2026-09-18

### Documentation
- Every configuration example is quoted from the shipped files, so the documentation cannot drift from what the plugin installs.

## [0.6.1] - 2026-09-18

### Documentation
- Every class setting documented, quoting the shipped `classes.yml`.

## [0.6.0] - 2026-09-18

### Added
- `/dyes`: a menu of the effects each dye colour gives a class's arrows.

## [0.5.0] - 2026-09-18

### Changed
- Kill the King: the King's position is a scoreboard row (`%king_location_line%`), and the chat says where the King is once a minute with their health (`announce-interval-seconds`). *`coordinates-interval-ticks` is no longer used.*

## [0.4.0] - 2026-09-18

### Added
- Citadels in a claimed Citadel (`citadels:` in `events.yml`): on its land, ender pearls, partner items, chorus fruit, elytra and Riptide are refused at all times; class abilities keep working.

## [0.3.1] - 2026-09-18

### Changed
- The Archer's dyed sets: black gives Wither I, blue gives Slowness I (20% per hit, 10 seconds).

## [0.3.0] - 2026-09-18

### Added
- Dyed sets (`dye-effects`): an Archer's arrows have a chance to give an effect by the dye colour of the set. The Archer ships with green: Poison I.

## [0.2.0] - 2026-09-18

### Added
- **Classes**: Diamond, Bard, Archer, Rogue, Miner, and your own in `classes.yml` — armour set, passive, held and click effects, energy, archer tag, backstab. `/class`.

## [0.1.0] - 2026-09-18

### Added
- First public version: teams, territory and DTR, combat and deathbans, lives, capture events (KOTH, Citadel, Conquest, Kill the King), Mountains, SOTW/EOTW/Purge, economy with Vault, kits and abilities, custom enchants and limiters, moderation tools, scoreboard and chat, holograms, redeem codes, the Lunar Client integration, and this documentation.

[0.13.2]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.13.2
[0.13.1]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.13.1
[0.13.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.13.0
[0.12.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.12.0
[0.11.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.11.0
[0.10.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.10.0
[0.9.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.9.0
[0.8.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.8.0
[0.7.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.7.0
[0.6.2]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.6.2
[0.6.1]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.6.1
[0.6.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.6.0
[0.5.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.5.0
[0.4.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.4.0
[0.3.1]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.3.1
[0.3.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.3.0
[0.2.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.2.0
[0.1.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.1.0
