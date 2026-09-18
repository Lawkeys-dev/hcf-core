# Changelog

Every release of HCFCore, newest first. Versions follow [Semantic Versioning](https://semver.org/), and each is on the [releases page](https://github.com/Lawkeys-dev/hcf-core/releases) with its jar.

A release gathers several changes: they collect under **Unreleased** as they reach `main`, and are published together. **HCFCore is in pre-release** (`0.x`) until `1.0.0`: a minor version may still change a setting's name or meaning — the notes say so, and what to do, since your configuration files are never rewritten.

## [Unreleased]

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

[Unreleased]: https://github.com/Lawkeys-dev/hcf-core/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.4.0
[0.3.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.3.0
[0.2.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.2.0
[0.1.0]: https://github.com/Lawkeys-dev/hcf-core/releases/tag/v0.1.0
