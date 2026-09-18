# Messages and translation

**Every message a player reads is in `plugins/HCFCore/lang/en.yml`** — none is written in the code. Edit it and run `/hcf reload`.

```yaml title="lang/en.yml"
team:
  create:
    success: "&aTeam &f%team% &acreated. Invite members with &f/team invite <player>&a."
```

- **Colours** use `&` codes.
- **Placeholders** are words between `%` signs, substituted by name — so their order in a sentence is free to change. Every message already contains the placeholders it receives; move or remove them freely. A placeholder a message does not receive is shown as typed.
- **An empty message is not sent.** That is how you silence one:

    ```yaml
    economy:
      pay:
        received: ""
    ```

- **A message missing from your file** falls back to the English bundled in the jar — so after an upgrade, new messages appear in English until you add them.

## How the file is organised

Keys follow `<module>.<context>.<message>`, fully nested — a `team:` section holding a `create:` section holding `success:` — never written as a dotted key on one line.

| Section | Messages of |
|---|---|
| `general` | `/hcf`, permissions, unknown commands |
| `startup` | the refusals while the plugin loads its data |
| `team` | teams, alliances, focus, rally, bank, points, team chat |
| `claim` | territory, protection, HQ, stuck, lock, the map |
| `dtr` | DTR, raidable announcements |
| `pvp` | combat tag, deathbans, friendly fire, loot |
| `economy` | balances, `/pay`, `/eco` |
| `events` | KOTH, Citadel, Conquest, Kill the King, zone holograms |
| `resourcenode` | Mountains |
| `phase` | SOTW, EOTW, the Purge |
| `staff` | staff mode, vanish, freeze, invsee, tickets, strikes, staff chat |
| `stats` | `/stats`, leaderboards |
| `ui` | the scoreboard's ready-made lines (`%combat_line%`, `%event_line%`...) |
| `general-commands` | `/spawn`, `/logout`, `/msg`, `/heal`... |
| `kit` | kits, layouts, refill signs, abilities |
| `schedule` | tips, timers, key-all |
| `settings` | `/settings`, `/cobble` |
| `redeem` | redeem codes |
| `crowbar` | the crowbar |
| `lives` | lives and revives |
| `hologram` | `/hologram` |
| `enchants` | custom enchants |
| `apollo` | Lunar Client waypoint names |
| `limiter` | caps and block limits |
| `effect-commands` | `/speed` and the other effect commands |

The full shipped file is on [GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/lang/en.yml).

## Translating the plugin

1. Copy `plugins/HCFCore/lang/en.yml` to `plugins/HCFCore/lang/<code>.yml` — for example `lang/fr.yml`.
2. Translate the texts. Keep the keys and the placeholders.
3. Set the language in `config.yml` and reload:

    ```yaml title="config.yml"
    language: fr
    ```

!!! warning "Keep every key"
    A custom language has no bundled copy to fall back on: a key missing from it shows as the raw key in game (`team.create.success`), with a warning in the console. After an upgrade, add the new keys to your translation.

!!! tip "Pronouns"
    The shipped messages never use a gendered pronoun for a player — "they", or a phrasing that needs none. Keep that in a translation where the language allows it.
