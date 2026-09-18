# Deathbans and lives

*Configured in [`pvp.yml`](../reference/configuration/pvp.md) (`deathban`) and [`lives.yml`](../reference/configuration/lives.md). Commands: `/pvp`, `/lives`, `/revive`.*

## Deathbans

**A death bans the player for one hour** (`deathban.duration-seconds`). They are disconnected a moment after dying and refused at login until the ban ends. The ban is stored as an end time, so **it survives restarts** — a ban that paused while the server was down would be trivially bypassed.

`/pvp` shows your own status.

### Shorter bans by rank

`permission-tiers` lists permission nodes with their ban length in seconds. **The shortest one a player holds wins**, so granting a rank is always a reduction, never accidentally a punishment. Only the nodes listed there are checked, so an unrelated wildcard cannot silently shorten a ban. Add your own freely — they need not be declared anywhere else:

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:deathban"
```

Your own tiers go under `permission-tiers`, next to the shipped one:

```yaml title="pvp.yml — your own tiers"
deathban:
  permission-tiers:
    hcfcore.deathban.tier.short: 900
    myserver.rank.vip: 1800
    myserver.rank.mvp: 600
```

### Staff bypass

**Staff with `hcfcore.deathban.bypass` are never banned** — operators hold it by default. The node is read **when they die**, not when they log in: the connection carries a profile, not a player, so no permission can be read at login.

!!! note
    Granting the node to somebody **already banned** does not let them in. Lift the ban with `/pvp lift <player>` — from the console too.

### Staff commands

| Command | Does |
|---|---|
| `/pvp check <player>` | Someone's combat tag and deathban |
| `/pvp lift <player>` | Lift a deathban |
| `/pvp ban <player> <seconds>` | Set a deathban — this one applies even to a bypass holder |

All need `hcfcore.pvp.admin` and work from the console.

### During SOTW and EOTW

- **SOTW**: no deathbans at all.
- **EOTW**: **a death bans until the end of the map**. No life can lift it; only staff, with `/pvp lift`, after the reset.

With `pvp.yml` or its deathbans off, nobody is banned in EOTW either: EOTW's ban is still a deathban.

## Lives

*HCF mode only — the module does not start in [kitmap](../getting-started/game-modes.md).*

A life brings a deathbanned player back. Players start with **none** (`starting-lives: 0`): lives come from staff, a [redeem code](../server/redeem.md), a store — any plugin that can run `/lives give`.

| Command | Does |
|---|---|
| `/lives` | How many you have |
| `/lives check <player>` | How many someone else has |
| `/lives send <player> <amount>` | Give some of yours (`allow-send`) |
| `/lives revive <player>` · `/revive <player>` | Spend one of yours to lift a friend's deathban |
| `/lives give\|take\|set <player> <amount>` | Staff (`hcfcore.lives.admin`) |

### Using your own life

On a single server, a banned player cannot log in to type a command. So **a banned player who tries to join while holding a life spends it and comes in**, and is told so (`use-on-login`). Turn it off and only a friend's `/revive` brings them back.

!!! warning "EOTW is final"
    A deathban until the end of the map — EOTW's — can never be bought back with a life.

Two revives at the same moment cannot spend the same life: every spend is atomic.
