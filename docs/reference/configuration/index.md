# Configuration

Everything is configured in `plugins/HCFCore/`: `config.yml` for the server-wide settings, then one file per module. **Every setting is commented in its file**, and each page of this section shows the complete shipped file, comments included, with a summary of what matters.

The files are written on the first start and **never overwritten** afterwards.

## Rules that hold everywhere

- **`/hcf reload` applies changes without a restart** — every file below, and `lang/en.yml`. Only `kitmap-mode` and the `storage` section of `config.yml` need a restart.
- **A bad value never stops the server.** A value that cannot be used — a number where true/false is expected, an unknown block name, a malformed time — is reported in the console and replaced by its default. A typo costs you that one setting.
- **A setting missing from your file takes its built-in default.** When a new version adds settings, your files keep working; compare them with the shipped ones to see what is new.
- **`0` usually means "no limit"** for limits and caps — each file says so where it applies.
- **A `*-seconds` setting is at most a hundred years** (3153600000); a longer one is reported and capped.
- **Times of day** (event schedules, refills, map phase dates, daily announcements) are read in the file's `time-zone`. The default `"system"` follows the host's clock; write a zone such as `"Europe/Paris"` so moving hosts does not shift everything.
- **Colours** use `&` codes (`&c`, `&l`...) in every text a player sees.

!!! warning "YAML 1.1"
    In YAML, unquoted `on`, `off`, `yes` and `no` are booleans. Quote them if you mean the words.

## The files

| File | Controls | Switch |
|---|---|---|
| [`config.yml`](config.md) | Language, game mode (`kitmap-mode`), storage (SQLite or MySQL, save interval) | — |
| [`teams.yml`](teams.md) | Team names, size, co-leaders, invitation expiry, the role needed for each action, alliances, focus, rally, bank, the Team Points scale, KOTH caps | always on |
| [`claims.yml`](claims.md) | Claim limits, placement rules, protection, HQ and base, `/team hq` and `/team stuck` countdowns, claimable worlds, roles for claiming, the warzone | `enabled` |
| [`dtr.yml`](dtr.md) | Maximum DTR, loss per death, floor, regeneration, announcements | `enabled` |
| [`pvp.yml`](pvp.md) | Deathban and its tiers, combat tag, strength nerf, knockback, attack speed, safe zones, loot protection, friendly fire | `enabled`, and one per part |
| [`classes.yml`](classes.md) | Classes: armour sets, warmup, passive, held and click effects, energy, archer tag, backstab, team limits | `enabled` |
| [`economy.yml`](economy.md) | Starting balance, ceiling, currency, `/pay` | `enabled` |
| [`events.yml`](events.md) | KOTH and Citadel zones, Kill the King, Conquest, schedules, rewards, zone holograms | `enabled` |
| [`resourcenodes.yml`](resourcenodes.md) | Mountains: regions, block palettes, refill times, protection, refill speed | `enabled` |
| [`phases.yml`](phases.md) | SOTW length and date, EOTW date, the Purge | always on — nothing happens until one starts |
| [`lives.yml`](lives.md) | Starting lives, using one at login, `/lives send` — HCF mode only | `enabled` |
| [`staff.yml`](staff.md) | Staff mode and its toolbar, vanish, staff chat, broadcast format, freeze, invsee, lastinv, tickets, the strike ladder | `enabled`, and one per part |
| [`chat.yml`](chat.md) | Public chat format, kill count, local chat range, team chat logging | `enabled` (team chat works either way) |
| [`ui.yml`](ui.md) | Scoreboard title, lines and refresh rate; tab list header and footer | `scoreboard.enabled`, `tablist.enabled` |
| [`general.yml`](general.md) | `/spawn`, `/logout`, `/rename`, private messages | `enabled` |
| [`kits.yml`](kits.md) | How kits are given, the layout editor, refill signs, abilities (partner items) | `enabled` |
| [`killstreaks.yml`](killstreaks.md) | Streak rewards | `enabled` |
| [`enchants.yml`](enchants.md) | Custom enchants | `enabled` |
| [`limiters.yml`](limiters.md) | Enchantment caps, potion caps, blocks per claim | `enabled` |
| [`schedule.yml`](schedule.md) | Tips, daily announcements and commands, custom timers, key-all | `enabled` |
| [`settings.yml`](settings.md) | Which settings players may switch, what `/cobble` drops | `enabled` |
| [`redeem.yml`](redeem.md) | Wait after a failed code | `enabled` |
| [`crowbar.yml`](crowbar.md) | Crowbar item, uses, cooldown, cost | `enabled` |
| [`holograms.yml`](holograms.md) | Leaderboard refresh | `enabled` |
| [`apollo.yml`](apollo.md) | Lunar Client waypoints, team view, cooldowns, nametags | `enabled`, and one per part |

Teams, player statistics and the map phases have no switch: the rest of the plugin is built on them.

## Made in game, not in a file

Some things live in the database because they are made in game:

- **kits** — `/kit create`;
- **redeem codes** — `/redeemadmin`;
- **holograms** — `/hologram`;
- **server land** — `/team createsystem`, `/team forceclaim`.

## Shipped switched off or empty

On purpose — each is a decision about your server that the plugin does not make for you. [Setting up a map, step 10](../../getting-started/setup-guide.md#10-what-the-plugin-leaves-to-you) goes through them. The example events and Mountains run only when staff start them, until you give them times.

## Messages

Every message is in `lang/en.yml` — see [Messages and translation](../messages.md).
