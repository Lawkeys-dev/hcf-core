<div align="center">

# HCFCore

**An open source, fully configurable Hardcore Factions core for Paper.**
One plugin drives both **HCF** and **Kitmap**, and every rule, number and message is yours to change without touching the code.

[![Build](https://github.com/Lawkeys-dev/hcf-core/actions/workflows/build.yml/badge.svg)](https://github.com/Lawkeys-dev/hcf-core/actions/workflows/build.yml)
[![Docs](https://github.com/Lawkeys-dev/hcf-core/actions/workflows/docs.yml/badge.svg)](https://lawkeys-dev.github.io/hcf-core/)
![Paper 26.2](https://img.shields.io/badge/Paper-26.2-blue)
![Java 25](https://img.shields.io/badge/Java-25-orange)
[![License: MIT + attribution](https://img.shields.io/badge/license-MIT%20%2B%20attribution-green)](LICENSE)

### 📖 [Read the documentation](https://lawkeys-dev.github.io/hcf-core/) · 🎨 [Design your theme](https://lawkeys-dev.github.io/hcf-core/tools/theme-builder.html)

</div>

---

## Features

| | |
|---|---|
| **Teams** | Roles, invitations, alliances, a bank, points and ranking, focus, rally, team and ally chat |
| **Territory** | Chunk claims with buffers and connected land, full protection (blocks, entities, pistons, liquids, explosions), HQ and base, `/team stuck`, server land for spawn and roads, a warzone, a claim lock for SOTW, elevator signs |
| **DTR and raids** | The classic scale, computed from time so it never drifts; raids open land to pillage and protection returns on its own — land never changes hands |
| **Combat** | Deathbans with rank tiers, combat tag and combat logging, safe zones, friendly fire rules, loot protection, strength nerf, ender pearl and item cooldowns (Gapple, Crapple…) — and a **classic 1.7.10 combat** mode: no attack cooldown, sword blocking, 1.7 weapon damage, Sharpness and knockback, regeneration, golden apples and pots |
| **Classes** | Diamond, Bard, Archer, Rogue and Miner, chosen by the armour worn: Bard buffs and energy, archer tag, backstab, effects by dye colour for the Archer — and your own classes, written from scratch in `classes.yml` |
| **Lives** | Revive friends, or spend your own life by logging in (HCF mode) |
| **Events** | KOTH, Citadel (fought in a claimed Citadel with no pearls or partner items), Conquest and Kill the King on daily schedules, with a hologram above every zone; Mountains that refill on a clock |
| **Map phases** | SOTW, EOTW and the Purge, by command or by date, surviving restarts |
| **Kits and items** | Kits saved from an inventory, refill signs, a layout editor, 42 partner items built in (Switcher, Ninja Track, Rage Ball, Pocket Bard, Grappling Hook…) and your own, killstreak rewards, custom enchants, effect commands (`/speed`, `/strength`…), enchantment, potion and effect caps, block limits per claim, a crowbar |
| **Moderation** | Staff mode with a configurable toolbar, vanish, freeze, invsee, last inventories, a ticket queue, staff chat, strikes against teams |
| **Everyday commands** | What an essentials plugin gives — `/ci`, `/feed`, `/fly`, `/god`, `/hat`, `/ec`, `/wb`, `/i`, `/tphere`, `/gmc`, `/day`… — each with its own permission, without what breaks HCF (`/tpa`, `/home`, `/back`, `/near`) |
| **Interface** | One theme for the whole plugin (`theme.yml`: colours by role, prefix, framed menus), flicker-free scoreboard from template lines, each player hiding the sections they want in `/settings`, tab list, chat format with LuckPerms prefixes and kill counts, stats and leaderboards, holograms, player settings |
| **Integrations** | Vault, LuckPerms and Lunar Client (Apollo) — all optional |

## Quick start

1. Put the jar in `plugins/` of a **Paper 26.2** server running **Java 25**, and start it. It runs out of the box on SQLite.
2. Claim spawn, set the warzone, move the example events onto your map, create your kits — [Setting up a map](https://lawkeys-dev.github.io/hcf-core/getting-started/setup-guide/).
3. Switch to MySQL for production, and open the map with `/sotw start`.

**Getting the jar**: download it from the [releases](https://github.com/Lawkeys-dev/hcf-core/releases) — every version, with what changed in [`CHANGELOG.md`](CHANGELOG.md); HCFCore is in pre-release (`0.x`) until `1.0.0` — or build it:

```bash
git clone https://github.com/Lawkeys-dev/hcf-core.git
cd hcf-core
./gradlew build        # needs a JDK 17+ to run Gradle; the jar is in build/libs/
```

## Documentation

The full documentation is at **https://lawkeys-dev.github.io/hcf-core/**:

- [Getting started](https://lawkeys-dev.github.io/hcf-core/getting-started/) — installation, setting up a map, game modes, upgrading
- [How the game works](https://lawkeys-dev.github.io/hcf-core/gameplay/) — every rule, system by system, with its default values
- [Running a server](https://lawkeys-dev.github.io/hcf-core/server/) — moderation, holograms, redeem codes, schedules, integrations, troubleshooting
- [Reference](https://lawkeys-dev.github.io/hcf-core/reference/) — commands, permissions, placeholders, messages, every configuration file
- [Developers](https://lawkeys-dev.github.io/hcf-core/developers/) — the public API, building, architecture, contributing

## Design principles

- **Nothing hardcoded.** Every gameplay value is a setting, `/hcf reload` applies it, and balance decisions (points scale, enchantment caps, rewards) ship empty or neutral.
- **The game never waits on the database.** Memory is the source of truth; SQLite or MySQL is written in the background. Nobody plays on a half-loaded server.
- **Rules are plain Java.** Every rule engine is free of the server API and unit-tested — 1069 tests, including real SQLite round trips.
- **Official documentation only.** Every technical choice is checked against Paper's documentation and the libraries' own sources.

The design documents for contributors: [`ARCHITECTURE.md`](ARCHITECTURE.md), [`FEATURES.md`](FEATURES.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Status

**Pre-release.** HCFCore compiles against the real Paper 26.2 API and its unit tests pass in CI. It has been tried in game module by module on a local Paper 26.2 server — SQLite and MySQL 8.4, LuckPerms, Vault, Lunar Client through Apollo, kitmap mode — classes, classic combat, effect caps, effect commands, partner items and cooldowns included, with two players; only the damage a sword block takes off awaits its test. It has not yet run a production map under load. Test it on your own setup before deploying it to a server you care about, and please [report](https://github.com/Lawkeys-dev/hcf-core/issues) what you find.

## Contributing

Issues and pull requests are welcome — read [`CONTRIBUTING.md`](CONTRIBUTING.md) first.

## License

[MIT with an attribution requirement](LICENSE): any redistribution or public deployment, modified or not, must visibly credit this repository.
