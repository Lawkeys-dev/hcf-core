# Effect commands

*Configured in [`effect-commands.yml`](../reference/configuration/effect-commands.md).*

`/speed`, `/strength`, `/fireresistance`… — rank perks, as on most kitmaps. **Each command gives its effect until the player dies**; typing it again takes it off.

```yaml title="effect-commands.yml"
--8<-- "src/main/resources/effect-commands.yml:commands"
```

**Shipped at what a Bard gives by holding an item**, without its burst:

| Command | Aliases | Effect |
|---|---|---|
| `/speed` | `/sp` | Speed II |
| `/strength` | `/str` | Strength I |
| `/resistance` | `/res` | Resistance I |
| `/regeneration` | `/regen` | Regeneration I |
| `/jumpboost` | `/jb` | Jump Boost II |
| `/fireresistance` | `/fres`, `/fr` | Fire Resistance I |

## Your own commands

Every command is a block: its name, the `effect`, its `level` (`1` is level I), `aliases`, and optionally a `permission`. Add, remove or rename freely:

```yaml title="effect-commands.yml — your own"
commands:
  speed:
    effect: speed
    level: 3
    aliases: [sp]
  nightvision:
    effect: night_vision
    level: 1
    aliases: [nv]
    permission: myserver.rank.vip
```

- **Permission**: `hcfcore.effect.<command>` unless the block names another — **operators only by default**. Give the node to the ranks that should have the command; a player without it does not see the command at all.
- **`/hcf reload`** applies a new effect, level or permission. **Adding, removing or renaming a command or an alias needs a restart**: the server builds its list of commands once.
- **An alias never takes over another plugin's command** — if an essentials plugin already has `/fr`, it keeps it, and the console says so at startup. The command's own name does take over: `/speed` is this plugin's, even with an essentials plugin that has one.

## How the effect behaves

- **Until death**, then gone like every effect. It survives logging out.
- **Typing the command again takes it off** — only the effect the command gave: a potion or a Bard's effect is never taken.
- **A stronger effect wins while it lasts**: with `/speed` on, a Bard's Speed III burst takes over for its seconds, then Speed II comes back.
- **The effect caps hold** ([`effects.caps`](items.md#effect-caps) of `limiters.yml`): capped at Speed II, a `/speed` configured at level III gives Speed II; an effect capped at `0` is refused.
- The potion caps do not apply: the command is not a potion. With `strength: 0` under `potions.caps` — Strength from the Bard only — `/strength` still gives Strength to whoever holds its permission. Remove the block, or give its permission to nobody, to keep Strength to the Bard.
