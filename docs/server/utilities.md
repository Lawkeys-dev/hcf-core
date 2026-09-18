# Utility commands

*Configured in [`general.yml`](../reference/configuration/general.md).*

The everyday commands an HCF server needs. Nothing here is HCF-specific — so if your server already runs an essentials plugin that provides them, turn the whole module off with `enabled: false`: two plugins fighting over `/spawn` help nobody. `/settings` and `/cobble` keep working either way.

## For players

| Command | Aliases | Does |
|---|---|---|
| `/spawn` | | Teleport to spawn after a **5-second** countdown |
| `/logout` | | Disconnect safely after a **30-second** countdown |
| `/msg <player> <message>` | `/tell`, `/w`, `/whisper`, `/m` | Private message |
| `/reply <message>` | `/r` | Answer the last private message |
| `/togglepm` | | Turn private messages on or off |
| `/ignore [player]` | | Ignore a player for this session, or list who you ignore |
| `/ping [player]` | | Ping |

### Countdowns

`/spawn` and `/logout` are countdowns that **any damage or movement cancels** — leaving your block, not turning your head. One countdown at a time.

- **`/spawn`** goes to the world's spawn point (set it with the vanilla `/setworldspawn`), in your current world unless `spawn.world` names another. A combat tag refuses it. `spawn.warmup-seconds: 0` teleports at once; `spawn.enabled: false` turns `/spawn` alone off.
- **`/logout`** exists because closing the client mid-fight is how a player escapes one, and the combat tag answers that by killing them — so there has to be a way to leave that is visibly not an escape. It is refused while combat-tagged, at the start and at the end (`logout-seconds`).

`private-messages: false` turns off the four private-message commands alone.

## For staff

| Command | Aliases | Does | Permission |
|---|---|---|---|
| `/heal [player]` | | Full health, read from the max-health attribute | `hcfcore.general.admin` |
| `/kill [player]` | | Kill — the death follows the normal path: deathban, DTR, stats | `hcfcore.general.admin` |
| `/gamemode <mode> [player]` | `/gm` | `survival`/`s`/`0`, `creative`/`c`/`1`, `adventure`/`a`/`2`, `spectator`/`sp`/`3` | `hcfcore.general.admin` |
| `/world [world]` | | Go to a world's spawn point | `hcfcore.general.world` |
| `/top` | | Teleport to the highest block above you | `hcfcore.general.top` |
| `/rename <name>` | | Rename the held item (32 characters at most, `rename-max-length`) | `hcfcore.general.rename` |
| `/more` | | Fill the held stack | `hcfcore.general.more` |
| `/repair` · `/repair all` | `/fix` | Repair the held item; everything worn and carried | `hcfcore.general.repair`, `hcfcore.general.repair.all` |

`/world` and `/top` are refused while combat-tagged.

!!! warning "`/top` is a staff command"
    `/top` has no countdown — only a combat tag refuses it — so given to players it lifts them out of any trap and onto any roof. Grant `hcfcore.general.top` to players deliberately if your server wants it.

`/repair` and `/repair all` are two permissions because they are two different perks. Neither has a cost or cooldown.
