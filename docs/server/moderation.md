# Moderation

*Configured in [`staff.yml`](../reference/configuration/staff.md). Most tools need `hcfcore.staff`.*

## Staff mode

```text
/staff          # enter or leave (aliases /mod, /staffmode)
/staff list     # who is in staff mode
```

Entering staff mode:

1. **puts your own inventory aside in the database** — written before your inventory is emptied, and given back when you leave. A crash gives it back at your next login;
2. gives you a **toolbar** of items bound to commands;
3. turns on **vanish**, **flight** and **invulnerability** — no damage taken or dealt, no mob targeting, nothing picked up (you would otherwise collect the loot of the fight you are watching into an inventory that is emptied when you leave).

Building is **not** part of it: that is [`/staffbuild`](#staffbuild). Other staff are told when you enter or leave (`staff-mode.announce`).

### The toolbar

Each slot of `staff-mode.items` is an item that runs a command. The shipped bar:

| Slot | Item | Command |
|---|---|---|
| 0 | Player Tracker (compass) | `staff tpnearest` |
| 1 | Random Teleport (ender pearl) | `staff randomtp` |
| 2 | Inspect (book) — right-click a player | `invsee %player%` |
| 3 | Freeze (packed ice) — right-click a player | `freeze %player%` |
| 7 | Vanish (lime dye) | `vanish` |
| 8 | Leave Staff Mode (barrier) | `staff` |

??? example "The toolbar as shipped in `staff.yml`"

    ```yaml
    --8<-- "src/main/resources/staff.yml:toolbar"
    ```

Adding a slot — here, the last deaths of the player clicked:

```yaml title="staff.yml — a slot of your own"
staff-mode:
  items:
    4:
      material: BLAZE_ROD
      name: "&6&lLast Deaths"
      command: "lastinv %player%"
      needs-target: true
```

- `command` has no leading `/`, and **runs as you**, so your own permissions apply. Any command works, another plugin's included.
- `%player%` is the player you right-clicked, `%staff%` is you.
- `needs-target: true` makes the item work only by right-clicking a player.

## Vanish

`/vanish` (alias `/v`) hides you from players — and from the player list (`vanish.hide-from-tab`). Vanished staff are not found by player commands either: `/msg`, `/ping`, `/team invite`, `/report` and the others answer "not found", as for somebody offline, and no name completion lists them.

**`hcfcore.staff.vanish.see`** lets a player see vanished staff, and reach them by name with `/invsee`, `/freeze` and the `/staff` teleports. **Nobody holds it by default, operators included**: given to everyone, staff would all see each other and vanish would be useless among them. Grant it to your senior ranks.

A vanish you turned on by hand survives leaving staff mode; one set by staff mode leaves with it.

## Staffbuild

`/staffbuild` (alias `/sb`) lets you build, break and interact through territory protection — player land, server land, the warzone — **only if you also hold `hcfcore.claim.bypass`**. Operators included: the toggle alone lifts nothing, and so does the permission alone.

The permission says a rank may; the toggle says you are choosing to, right now. That way nobody breaks a wall by accident while looking around, and an operator in normal play is stopped like anybody else. Watching a raid and changing the world are two different intentions — which is why building is not part of staff mode.

The same pair applies to the [blocks-per-claim limits](../gameplay/items.md#blocks-per-claim) with `hcfcore.limiter.bypass`, and to the crowbar.

## Freeze

```text
/freeze <player>        # hold a player for a check, or release them (alias /ss)
/freeze list            # who is frozen
/freeze unban <player>  # lift a ban for leaving while frozen
```

A frozen player cannot move from their block, go through a portal, build, break, use a block, an item (an ender pearl, a partner item, a refill sign) or an entity (a boat, a horse, a villager), drop items or fight, and cannot run commands other than private messages (`freeze.allowed-commands`). They **can still look around and talk** — a check is a conversation.

A reminder is repeated every 10 seconds (`reminder-seconds`), so a player who cannot move does not think their game froze and restart their client.

!!! danger "Disconnecting while frozen bans"
    Leaving while frozen records a ban with no expiry (`ban-on-logout`), lifted only with `/freeze unban <player>`. It is a ban of its own, not a deathban — turning deathbans off in `pvp.yml` does not turn it off.

## Invsee and lastinv

```text
/invsee <player>             # their live inventory (alias /inv)
/lastinv <player> [number]   # what they carried at one of their last deaths (alias /li)
```

- **Invsee opens the real, live inventory** — not a copy that would need writing back. It is **read-only** unless you hold `hcfcore.staff.invsee.edit`, which nobody holds by default: on a server where items are the economy, an accidental drag cannot be undone and looks exactly like staff theft. The window shows the 36 storage slots; armour and off-hand are shown as text.
- **Lastinv** keeps each player's **last 3 deaths** (`last-inventory.keep`), always read-only.

## Reports and requests

Players raise tickets; staff work through one queue.

```text
/report <player> <reason>    # players
/request <message>           # players
/tickets                     # staff: the queue as a menu (alias /reports)
/tickets list | view <id> | claim <id> | close <id>
```

- Reports and requests share **one queue**, oldest first — the same act with a different word on the front.
- A player waits **60 seconds** between tickets (`tickets.cooldown-seconds`).
- In the menu, **left-click** takes a ticket and teleports you to it (to the reported player, or else to whoever asked); **right-click** closes it. The `list`/`view`/`claim`/`close` subcommands do the same by id, from the console too.
- One staff member holds a ticket at a time.
- Whoever raised a ticket is told when it is taken and closed — **without the staff member's name**, who may be vanished while watching.
- A player cannot report someone they cannot see: the answer is the same as for an offline player, so `/report` cannot detect vanished staff.

## Staff chat

`/staffchat` (alias `/sc`) toggles the staff channel; `/staffchat <message>` sends one line without toggling — from the console too. Format: `staff-chat.format`. Staff chat wins over team chat.

## Teleports

Subcommands of `/staff`, so they never fight another plugin over `/tp` or `/near`:

| Command | Does |
|---|---|
| `/staff tp <player>` | Teleport to a player |
| `/staff tphere <player>` | Bring a player to you |
| `/staff tpall` | Bring everyone |
| `/staff tploc <x> <y> <z> [world]` | Go to coordinates |
| `/staff tpnearest` | Go to the nearest player |
| `/staff randomtp` | Go to a random player |
| `/staff near [radius]` | Players within a radius (50 blocks by default) |
| `/staff telllocation <player>` | A player's coordinates |
| `/staff end` · `/staff nether` | Who is in The End; in the Nether — by dimension, whatever your worlds are called |

## Broadcast and clear chat

```text
/broadcast <message>    # hcfcore.staff.broadcast (alias /bc)
/clearchat              # hcfcore.staff.clearchat (alias /cc)
```

`/clearchat` pushes 100 blank lines (`broadcast.clear-chat-lines`) — the chat history lives on the client, so there is no other way. **Staff are skipped**: they usually need to read what caused the clear.

## Strikes

Strikes punish a **team** for what its members did. **A strike is given for an offence**, and each offence takes its own share of the team's points; **whatever the offences, a team is disbanded at its third active strike**.

```text
/strike add <team|player> <offence> [details]   # hcfcore.staff.strike
/strike pardon <id>                             # hcfcore.staff.strike
/strike offences                                # hcfcore.staff.strike: what a team can be struck for
/strike list [team|player]                      # everyone; your own team by default (alias /strikes)
```

As shipped:

| Offence | `/strike add … <offence>` | Points lost |
|---|---|---|
| Cheating | `cheating` | 50% |
| Bug abuse | `bug-abuse` | 50% |
| Ban evasion | `ban-evasion` | 50% |
| Kill boosting | `boosting` | 40% |
| Teaming | `teaming` | 35% |
| Alt abuse | `alt-abuse` | 35% |
| Claim abuse | `claim-abuse` | 25% |
| Other | `other` | 25% |

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:strike-offences"
```

- **Who sees what.** Every strike is announced to the whole server, with the team's count — `STRIKE » Wizards received a strike (1/3)` — but not what it was for. `/team show` gives a struck team a `Strikes: 1` line, as a statistic. `/strike list` shows each strike's offence; the **details** staff add (`/strike add Wizards teaming allied with Raiders at the Citadel`) are for staff only.
- Striking a player's name strikes their team and records the member.
- The points are taken first, then the offence's `commands` run (with `%team%`, `%strikes%`, `%offence%`), then the team is disbanded if it reached `disband-at` — as by `/team forcedisband`.
- A pardon does not give back points taken or undo a disband.
- A strike outlives its team: a disbanded team is found by the name it had.
- No strike is added automatically — the plugin cannot know that a ban made by another plugin was for cheating.
