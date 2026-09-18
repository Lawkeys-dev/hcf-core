# Commands

`<argument>` is required, `[argument]` optional, `a|b` one of the listed words. **Permission** is the node needed, or *everyone*; see [Permissions](permissions.md) for defaults. Durations are typed `90` (seconds), `45s`, `30m`, `2h`, `1d`, or combined like `1h30m`, wherever this page says *duration*.

While the plugin is still loading its data at startup, the commands that touch that data answer that it is loading ([Installation](../getting-started/installation.md#nobody-plays-on-a-half-loaded-server)).

## `/team`

Aliases `/f`, `/faction`. **`/team` or `/team help` lists every subcommand you are allowed to use.**

### For players

The **role** column is the default minimum role in the player's team, set under `required-roles` in `teams.yml` and `claims.yml`.

| Subcommand | Aliases | Does | Role |
|---|---|---|---|
| `create <name>` | | Found a team | — |
| `disband` | | Disband your team | leader |
| `rename <name>` | | Rename your team | leader |
| `invite <player>` | `inv` | Invite a player | co-leader |
| `uninvite <player>` | `revoke` | Withdraw an invitation | co-leader |
| `join <team>` | | Accept an invitation | — |
| `leave` | `quit` | Leave your team | — |
| `kick <player>` | | Remove a member | co-leader |
| `promote <player>` | | Member → co-leader | leader |
| `demote <player>` | | Co-leader → member | leader |
| `transfer <player>` | `setleader` | Hand over leadership | leader |
| `info [team]` | `who`, `show` | A team's details | — |
| `list [limit]` | `top` | Teams ranked by points (up to 100 lines) | — |
| `chat [public\|team\|ally]` | `c` | Pick a chat channel; no argument cycles through them | — |
| `ally <team>` | | Offer an alliance, or accept one offered | leader |
| `unally <team>` | | Break an alliance | leader |
| `focus <player\|team>` / `unfocus <player\|team>` | | Mark or clear a target for your team | co-leader |
| `rally` / `unrally` | | Set or clear a rally point where you stand | co-leader |
| `dtr [team]` | | A team's DTR | — |
| `claim [radius]` | | Claim the chunk you stand in, or a square of up to 8 chunks' radius | co-leader |
| `unclaim` | | Release the chunk you stand in | leader |
| `unclaimall` | | Release all your land | leader |
| `here` | `claiminfo` | Who owns the chunk you stand in | — |
| `map` | | Territory around you | — |
| `sethq` / `setbase` | | Set the HQ or the second base where you stand | co-leader |
| `hq` / `base` | | Go there, after a countdown (10 s) | — |
| `stuck` | | Get out of land you cannot leave, after a countdown (60 s) | — |
| `lockclaim` | `lock` | Close your land to non-members until SOTW ends (SOTW only) | co-leader |
| `deposit <amount>` | | Money from you to the team bank | member |
| `withdraw <amount>` | | Money from the team bank to you | leader |
| `limits` | `blocks` | How much of each limited block your land holds | — |

### For staff

| Subcommand | Does | Permission |
|---|---|---|
| `createsystem <name> <safe\|combat>` | Create a server team — a safe zone like spawn, or a combat zone like a road | `hcfcore.team.admin` |
| `setzone <team> <safe\|combat>` | Switch a server team between the two | `hcfcore.team.admin` |
| `forceclaim <team> [radius]` | Claim for any team, server teams included, a square of up to 32 chunks' radius around you | `hcfcore.claim.admin` |
| `forceunclaim <team> [all]` | Release that team's chunk you stand in, or all its land | `hcfcore.claim.admin` |
| `forcedisband <team>` | Disband any team | `hcfcore.team.admin` |
| `forcejoin <player> <team>` | Put a player in a team without an invite (the member cap still applies) | `hcfcore.team.admin` |
| `forcekick <player>` / `forcepromote <player>` / `forcedemote <player>` | Change a player's membership or role, online or not. A kicked leader is succeeded by a co-leader, otherwise by a member; kicking the last member disbands the team | `hcfcore.team.admin` |
| `setpoints <team> <points>` / `addpoints <team> <points>` | Set or add to (negative: take from) a team's points | `hcfcore.team.admin` |
| `resetkoth` | Reset every team's counted KOTH captures | `hcfcore.team.admin` |
| `setdtr <team> <value>` | Set a team's DTR | `hcfcore.team.admin` |
| `setregen <team> <seconds>` | Set how long until a team's DTR starts regenerating | `hcfcore.team.admin` |

## Combat, economy and statistics

| Command | Aliases | Does | Permission |
|---|---|---|---|
| `/pvp` | | Your combat tag and deathban | everyone |
| `/pvp check <player>` · `lift <player>` · `ban <player> <seconds>` | | Someone's status; lift or set a deathban (works from the console) | `hcfcore.pvp.admin` |
| `/lives` | | Your lives (HCF mode only) | everyone |
| `/lives check <player>` · `send <player> <amount>` · `revive <player>` | | Someone's lives; give some of yours; spend one to lift a friend's deathban | everyone |
| `/lives give\|take\|set <player> <amount>` | | Change a player's lives | `hcfcore.lives.admin` |
| `/revive <player>` | | Same as `/lives revive` | everyone |
| `/balance [player]` | `/bal`, `/money` | A balance | everyone |
| `/pay <player> <amount>` | | Send money | everyone |
| `/eco give\|take\|set <player> <amount>` | `/economy` | Change a player's balance | `hcfcore.economy.admin` |
| `/stats [player]` | | Kills, deaths, killstreak, playtime | everyone |
| `/leaderboard [kills\|deaths\|kdr\|killstreak\|playtime]` | `/lb`, `/top10` | The leaderboards | everyone |

## Events and map phases

| Command | Aliases | Does | Permission |
|---|---|---|---|
| `/events` | `/event`, `/koth` | What runs and what is coming, refills and map phases included | everyone |
| `/events start <id>` · `stop <id>` | | Open or end a KOTH, Citadel, Conquest or Kill the King | `hcfcore.events.admin` |
| `/resourcenode` | `/node`, `/mountain` | When each Mountain refills | everyone |
| `/resourcenode refill <id>` | | Refill one now | `hcfcore.resourcenode.admin` |
| `/sotw` | | SOTW status | everyone |
| `/sotw enable` | | Fight during SOTW, against others who did the same, for the rest of it | everyone |
| `/sotw start [duration]` · `stop` | | Start or end SOTW | `hcfcore.phase.admin` |
| `/eotw` · `/eotw start` · `stop` | | Status; start or end EOTW | status: everyone; start/stop: `hcfcore.phase.admin` |
| `/purge` · `/purge start [duration]` · `stop` | | Status; start or end the Purge | status: everyone; start/stop: `hcfcore.phase.admin` |
| `/timer` | `/timers`, `/customtimer` | The custom timers running | everyone |
| `/timer start <name> <duration> [label]` · `stop <name>` | | Start or stop a countdown shown on every scoreboard. The name `keyall` belongs to `/keyall` and is refused | `hcfcore.schedule.admin` |
| `/keyall [countdown]` | | Run the key-all commands for everybody online, now or after a countdown (a duration) | `hcfcore.schedule.admin` |

## Kits and items

| Command | Aliases | Does | Permission |
|---|---|---|---|
| `/kit` · `/kit <kit>` | `/kits` | List the kits you can take; take one | everyone (a kit may need its own permission) |
| `/kit layout <kit> [reset]` | | Arrange a kit's items; close the window to save | everyone |
| `/kit create <id> [cooldown-seconds] [permission]` | | Save your inventory as a kit | `hcfcore.kit.admin` |
| `/kit delete <id>` · `give <player> <kit>` · `resetcooldown <player> [kit]` · `ability <player> <ability> [amount]` | | Manage kits; hand out an ability item | `hcfcore.kit.admin` |
| `/cenchant list` | `/ce`, `/customenchant` | The custom enchants | everyone |
| `/cenchant apply <enchant> [level]` · `remove <enchant>` · `give <player> <enchant> [level] [amount]` | | On the held item; as books (works from the console) | `hcfcore.enchant.admin` |
| `/crowbar give <player> [amount]` | | Give crowbars | `hcfcore.crowbar.admin` |
| `/redeem <code>` | | Redeem a code | everyone |
| `/redeemadmin create <code> <max-uses\|unlimited> <command>` · `addcommand <code> <command>` · `removecommand <code> <number>` · `setuses <code> <max-uses\|unlimited>` · `info <code>` · `list` · `reset <code> [player]` · `delete <code>` | `/redeemcodes` | Manage codes | `hcfcore.redeem.admin` |
| `/resetredeem <code> [player]` | | Let one player, or everybody, use a code again | `hcfcore.redeem.admin` |
| `/hologram create <id> <text>` · `addline <id> <text>` · `setline <id> <line> <text>` · `removeline <id> <line>` · `movehere <id>` · `tp <id>` · `list` · `delete <id>` | `/holo`, `/holograms` | Manage holograms | `hcfcore.hologram.admin` |

## Chat and utilities

| Command | Aliases | Does | Permission |
|---|---|---|---|
| `/msg <player> <message>` | `/tell`, `/w`, `/whisper`, `/m` | Private message | everyone |
| `/reply <message>` | `/r` | Answer the last private message | everyone |
| `/togglepm` | | Turn private messages on or off | everyone |
| `/ignore [player]` | | Ignore a player for this session, or list who you ignore | everyone |
| `/settings` | `/options` | A menu of your settings; `/settings list`, `/settings <setting> [on\|off]` | everyone |
| `/cobble [on\|off]` | `/cobblestone` | Stop or start picking up cobblestone | everyone |
| `/spawn` | | Teleport to spawn after a countdown (5 s) | everyone |
| `/logout` | | Disconnect safely after a countdown (30 s); refused while combat-tagged, at the start and at the end | everyone |
| `/ping [player]` | | Ping | everyone |
| `/top` | | Teleport to the highest block above you | `hcfcore.general.top` |
| `/world [world]` | | Go to a world's spawn point | `hcfcore.general.world` |
| `/heal [player]` · `/kill [player]` · `/gamemode <mode> [player]` | `/gm` | Modes: `survival`/`s`/`0`, `creative`/`c`/`1`, `adventure`/`a`/`2`, `spectator`/`sp`/`3` | `hcfcore.general.admin` |
| `/rename <name>` | | Rename the held item | `hcfcore.general.rename` |
| `/more` | | Fill the held stack | `hcfcore.general.more` |
| `/repair [all]` | `/fix` | Repair the held item, or everything | `hcfcore.general.repair`, `hcfcore.general.repair.all` |

`general.yml`'s `enabled: false` turns off every command in this table except `/settings` and `/cobble` — they then answer that they are disabled — for servers whose essentials plugin already provides them. `private-messages: false` turns off the four private-message commands alone, and `spawn.enabled: false` `/spawn` alone.

## Moderation

| Command | Aliases | Does | Permission |
|---|---|---|---|
| `/report <player> <reason>` | | Report a player | everyone |
| `/request <message>` | | Ask staff for help | everyone |
| `/strike list [team\|player]` | `/strikes` | A team's strikes, your own by default | everyone |
| `/strike add <team\|player> <reason>` · `pardon <id>` | | Strike a team (naming a member records them too); pardon a strike | `hcfcore.staff.strike` |
| `/staff` | `/mod`, `/staffmode` | Enter or leave staff mode | `hcfcore.staff` |
| `/staff list` | | Who is in staff mode | `hcfcore.staff` |
| `/staff tp <player>` · `tphere <player>` · `tpall` · `tploc <x> <y> <z> [world]` | | Teleport to a player; bring one; bring everyone; go to coordinates | `hcfcore.staff` |
| `/staff tpnearest` · `randomtp` | | Go to the nearest player; to a random one | `hcfcore.staff` |
| `/staff near [radius]` | | Players within a radius (50 blocks by default) | `hcfcore.staff` |
| `/staff telllocation <player>` | | A player's coordinates | `hcfcore.staff` |
| `/staff end` · `nether` | | Who is in The End; in the Nether | `hcfcore.staff` |
| `/vanish` | `/v` | Hide from players | `hcfcore.staff` |
| `/staffchat [message]` | `/sc` | Toggle the staff channel, or send one line to it | `hcfcore.staff` |
| `/staffbuild` | `/sb` | Build through territory protection until toggled off — with `hcfcore.claim.bypass` | `hcfcore.staff` |
| `/freeze <player>` · `list` · `unban <player>` | `/ss` | Hold a player for a check; who is frozen; lift a ban for leaving while frozen | `hcfcore.staff` |
| `/invsee <player>` | `/inv` | A player's inventory | `hcfcore.staff`; changing it: `hcfcore.staff.invsee.edit` |
| `/lastinv <player> [number]` | `/li` | What they carried at one of their last deaths | `hcfcore.staff` |
| `/tickets` · `list` · `view <id>` · `claim <id>` · `close <id>` | `/reports` | The queue of reports and requests, as a menu or by id | `hcfcore.staff` |
| `/broadcast <message>` | `/bc` | One line to everybody | `hcfcore.staff.broadcast` |
| `/clearchat` | `/cc` | Push chat off every non-staff screen | `hcfcore.staff.clearchat` |

## Administration

| Command | Does | Permission |
|---|---|---|
| `/hcf` · `/hcf version` | Plugin version and game mode | `hcfcore.admin` |
| `/hcf reload` | Re-read every configuration and language file | `hcfcore.admin` |
