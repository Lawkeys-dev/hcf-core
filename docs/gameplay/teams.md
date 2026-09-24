# Teams

*Configured in [`teams.yml`](../reference/configuration/teams.md). Commands: [`/team`](../reference/commands.md#team).*

A team (or faction) is the unit of HCF: it owns land, shares a DTR, a bank and a chat, and wins events. `/team` — aliases `/f` and `/faction` — holds everything; `/team help` lists the subcommands you are allowed to use.

**Shortcuts** (`teams.yml`, `shortcuts`): `/hq`, `/base`, `/stuck` and `/fc` (team chat) are commands of their own, and `/f i` is `/f info` — `h`/`home` for `hq`, `sh` for `sethq`, `d`/`w` for `deposit`/`withdraw`, `m` for `map`, `k` for `kick`, `s` for `settings`. The server chooses them; a name another plugin already has is left to it.

## Creating a team

```text
/team create Vikings
```

A name is 3 to 16 characters, letters, digits and underscores (`names.pattern`), and cannot be one of the blacklisted words (`spawn`, `warzone`, `wilderness`, `staff`, `admin` by default, compared without case). `/team rename <name>` changes it, `/team disband` ends the team and releases its land.

`/team info [team]` (aliases `who`, `show`) shows a team: its members by role and how many are online, its balance, points, counted KOTH captures, allies and rally point - and, from its first strike, how many strikes count against it (`Strikes: 1`), never what for ([Strikes](../server/moderation.md#strikes)). `/team dtr [team]` shows its DTR.

## Roles

Leader, co-leader (**2 at most**, `max-co-leaders`), **officer** (no limit, `max-officers`) and member. What each role may do is set per action under `required-roles` in `teams.yml` and `claims.yml` — and **each team may choose its own** in `/team settings`:

| Leader only | Co-leader and above | Officer and above | Every member |
|---|---|---|---|
| disband, rename, promote, demote, hand over leadership, ally, unally, withdraw from the bank, unclaim, open the settings | kick, claim, set HQ and base, lock the claim | invite, revoke an invite, focus, rally | deposit in the bank |

- `/team promote <player>` and `/team demote <player>` move a member one rank: member → officer → co-leader. **Nobody makes somebody their equal**: a co-leader a team lets promote raises members to officer, never to co-leader.
- `/team transfer <player>` (alias `setleader`) hands leadership over; the old leader becomes a **co-leader** (`role-after-leadership-transfer`).

## Joining and leaving

- `/team invite <player>` invites; the invitation lasts **5 minutes** (`invite-expiry-seconds`, `0` = never expires). `/team uninvite <player>` withdraws it.
- The invited player types `/team join <team>`. **An open team** takes anybody, invited or not; **a closed one** nobody (`/team settings`).
- A team holds **20 members** (`max-members`).
- `/team leave` leaves; `/team kick <player>` removes a member.
- **When the last member leaves, the team is disbanded** (`disband-on-last-member-leave`) and its land released.

**Looking for a team?** `/lff [note]` tells the whole server, with a few words about yourself if you like — `/lff PvP main, online evenings`. Once every 5 minutes, and only without a team (`teams.yml`, `lff`).

## Team settings

`/team settings` (alias `options`; `/f s`) opens a window to **set the team up with no command to type** — every member opens it, and each button says under it which rank may use it:

- **Profile** — the team's name, its **description** and its **Discord invitation**, typed in the game's own window (a text field for each, Save or Cancel). `/team info` shows the description, and the Discord link, which a click opens. The link must be a Discord invitation (`discord.gg/...`); colour codes are not kept in a description.
- **Joining** — **closed** (nobody joins; only staff put a player in), **on invitation** (as shipped) or **open** (anybody, with `/team join`). A click moves to the next.
- **Permissions** — the lowest rank that may do each thing: every action above, and opening every subclaim. Left click raises it a rank, right click lowers it, shift-click gives it back to the server's setting. Whoever changes a permission must hold it, and cannot set it above their own rank; the leader changes everything. Some stay the server's for every team (`team-settings.locked`: disbanding and handing over leadership, as shipped).
- **Members** — left click promotes, right click demotes, shift + right click kicks, with the same rules as the commands.
- **Invitations** — **invite a player** by typing their name, and the pending invitations (a click takes one back).
- **Claim lock** (during SOTW), **set the HQ here**, **set the base here**.

The profile, the joining and the permissions are the leader's to change, as shipped (`required-roles.settings`). `team-settings.enabled: false` keeps every team on the server's roles and joining.

## Alliances

```text
/team ally Samurai        # from both leaders: a request, then an acceptance
/team unally Samurai
```

An alliance takes a request and an acceptance. **One ally per team** by default (`alliances.max-allies`), checked on both sides. Allies:

- share a chat channel (`/team chat ally`);
- **cannot build in each other's land** (`claims.yml`, `allow-ally-build`);
- **cannot hurt each other outside an event area** — see [Combat](combat.md#friendly-fire);
- still **contest each other** in captures: a capture belongs to one team.

## Focus and rally

- **Focus** marks an enemy player or team for the whole team: `/team focus <player|team>`, `/team unfocus <player|team>`. It shows on the scoreboard (`%focus_line%`) and, for Lunar Client players, as a waypoint and a nametag colour. No limit on targets by default (`focus.max-targets`).
- **Rally** marks the point where you stand for your team, for **5 minutes** (`rally.duration-seconds`, `0` = until cleared): `/team rally`, `/team unrally`. It shows on the scoreboard and as a Lunar waypoint.

## Bank

A team has its own balance:

```text
/team deposit 500      # every member
/team withdraw 200     # the leader
```

Money moves between the player's balance and the team bank. Turning the economy off in `economy.yml`, or `bank.enabled: false`, disables both commands. The bank is never exposed to other plugins through Vault — see [Economy](economy.md).

## Points and ranking

`/team list [limit]` (alias `top`) ranks teams by points. Points are a score separate from the balance, starting at `points.starting` and never below `points.minimum`.

Where points come from is entirely configurable. The shipped scale: **a kill +1, a death −2**, and an event worth far more than the kills fought for it along the way — so the event, not the farm, decides the ranking.

| Setting (`teams.yml`) | As shipped | Given to | When |
|---|---|---|---|
| `points.per-kill` | `1` | the killer's team | killing a player of another team, or of none |
| `points.per-death` | `-2` | the victim's team | any death of a member |
| `points.raidable-loss-percent` | `50` | the team | its DTR has just made it raidable: it **loses that share of its points** — half, as shipped |
| `points.per-raidable` | `0` | the team | the same, as a fixed number (negative), when the share is `0` |
| `koth.points-per-capture` | `100` | the capturing team | capturing a KOTH |
| `points.per-citadel-capture` | `300` | the capturing team | capturing a Citadel |
| `points.per-conquest-win` | `250` | the winning team | winning a Conquest |
| `points.per-dtc-win` · `per-slide-win` | `200` | the winning team | winning a DTC, a Slide |
| `points.per-king-win` | `150` | the winner's team | winning Kill the King |
| `points.per-last-break-win` | `150` | the winning team | winning a Last Break |
| `points.per-totem-win` | `150` | the winning team | winning a Totem |
| `points.per-mini-totem-win` | `80` | the winning team | winning a Mini Totem (a column of 3 blocks or fewer) |

Killing a teammate, or yourself, earns nothing.

!!! warning "Farming"
    With `per-kill` above 0, a player with no team can be an alt: watch for teams farming kills on their own alts.

**KOTH captures** are counted per team, up to `koth.max-counted-captures` (`0` = uncapped). Staff reset the counts for a new map or ranking period with `/team resetkoth`.

Staff set a team's points with `/team setpoints <team> <points>`, or add to them (a negative number takes) with `/team addpoints <team> <points>`.

## Team chat

`/team chat` (alias `c`) cycles your channel from public to team to ally; `/team chat team` picks one. A player with no team talks in public. Team and ally chat are written to the console and the server log with the team's name (`chat.yml`, `log-team-chat`), so staff can read a conversation back. More in [Chat, scoreboard and settings](interface.md#team-and-ally-chat).

## Stuck

`/team stuck` gets a player out of land they cannot leave — walled into someone else's base, for instance. It works **without a team**. After a **60-second countdown** that damage or moving cancels, the player is moved to the nearest free land. A player who is not stuck is told so. See [Territory](territory.md#stuck).

## Server teams

A *server team* holds server land — spawn, roads, event grounds. Staff create one with `/team createsystem <name> <safe|combat>`: a **safe** zone has no PvP, a **combat** zone does, and the command hands over the claiming wand to draw its land. An event's territory is a server team too, made by `/events create` or `/events claim` ([Capture events](events.md#setting-up-an-event-in-game)). Server teams are excluded from rankings, have no members, and cannot be changed by players. See [Territory](territory.md#server-land).

## Staff commands

Staff with `hcfcore.team.admin` can act on any team:

| Command | Does |
|---|---|
| `/team forcedisband <team>` | Disband any team |
| `/team forcejoin <player> <team>` | Put a player in a team without an invite (the member cap still applies) |
| `/team forcekick <player>` | Remove a player, online or not. A kicked leader is succeeded by a co-leader, otherwise by a member; kicking the last member disbands the team |
| `/team forcepromote <player>` · `forcedemote <player>` | Change a player's role, online or not |
| `/team setpoints` · `addpoints` · `resetkoth` | Points and KOTH captures |
| `/team setdtr` · `setregen` | DTR overrides — see [DTR and raids](dtr-and-raids.md#staff-overrides) |

## For developers

Every team change fires a public Bukkit event — creation, disband, rename, joins, leaves, role changes, alliances, raidable state. See the [Developer API](../developers/api.md).
