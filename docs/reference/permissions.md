# Permissions

Every check goes through Bukkit's standard permission API, so LuckPerms or any other permission plugin manages these nodes; HCFCore has no ranks of its own.

**`hcfcore.admin`** (default: operators) is the umbrella: it grants every node marked ✓ in the last column, and `/hcf` itself. The nodes without a ✓ are deliberately left out of it.

Commands that need no node — `/team` and its player subcommands, `/pay`, `/balance`, `/stats`, `/kit`, `/msg`, `/report`... — are open to everyone; see [Commands](commands.md). A command whose every use needs one node declares it in `plugin.yml`, so the server's `/help` hides it from players who lack it.

## Staff and administration

| Node | Default | Grants | In `hcfcore.admin` |
|---|---|---|---|
| `hcfcore.admin` | op | `/hcf reload` and `/hcf version`, and every ✓ node below | — |
| `hcfcore.staff` | op | Staff mode and its toolbar, `/vanish`, `/staffchat`, `/staffbuild` (which lifts protection only with `hcfcore.claim.bypass`), the `/staff` teleports and lists, `/freeze`, `/invsee` (read-only), `/lastinv`, `/tickets` | ✓ |
| `hcfcore.staff.broadcast` | op | `/broadcast` | ✓ |
| `hcfcore.staff.clearchat` | op | `/clearchat` | ✓ |
| `hcfcore.staff.strike` | op | `/strike add` and `/strike pardon` | ✓ |
| `hcfcore.team.admin` | op | `/team` staff verbs: `createsystem`, `setzone`, `force*`, `setpoints`, `addpoints`, `resetkoth`, `setdtr`, `setregen` | ✓ |
| `hcfcore.claim.admin` | op | `/team forceclaim` and `/team forceunclaim` | ✓ |
| `hcfcore.claim.bypass` | op | Build, break and interact in any territory — player land, server land, the warzone — **while `/staffbuild` is on**; walk into claims locked during SOTW | ✓ |
| `hcfcore.pvp.admin` | op | `/pvp check`, `/pvp lift`, `/pvp ban` | ✓ |
| `hcfcore.deathban.bypass` | op | Never deathbanned. Read when the player dies: granting it to somebody already banned does not let them in | ✓ |
| `hcfcore.economy.admin` | op | `/eco` | ✓ |
| `hcfcore.events.admin` | op | `/events start` and `/events stop` | ✓ |
| `hcfcore.resourcenode.admin` | op | `/resourcenode refill` | ✓ |
| `hcfcore.resourcenode.bypass` | op | Build and mine freely inside a Mountain's region | ✓ |
| `hcfcore.phase.admin` | op | `start` and `stop` of `/sotw`, `/eotw` and `/purge` | ✓ |
| `hcfcore.schedule.admin` | op | `/timer start` and `/timer stop`, `/keyall` | ✓ |
| `hcfcore.kit.admin` | op | `/kit create`, `delete`, `give`, `resetcooldown` | ✓ |
| `hcfcore.ability.admin` | op | `/ability give` | ✓ |
| `hcfcore.cooldown.admin` | op | `/cooldown reset`: end a player's cooldowns | ✓ |
| `hcfcore.kit.sign` | op | Create kit refill signs | ✓ |
| `hcfcore.enchant.admin` | op | `/cenchant apply`, `remove`, `give` | ✓ |
| `hcfcore.hologram.admin` | op | `/hologram` | ✓ |
| `hcfcore.redeem.admin` | op | `/redeemadmin`, `/resetredeem` | ✓ |
| `hcfcore.crowbar.admin` | op | `/crowbar give` | ✓ |
| `hcfcore.lives.admin` | op | `/lives give`, `take`, `set` | ✓ |
| `hcfcore.limiter.bypass` | op | Place blocks past `limiters.yml`'s blocks-per-claim limits — only while `/staffbuild` is on | ✓ |
| `hcfcore.general.admin` | op | `/heal`, `/kill`, `/gamemode`, `/gmc`, `/gms`, `/gma`, `/gmsp` | ✓ |
| `hcfcore.general.world` | op | `/world` | ✓ |
| `hcfcore.general.top` | op | `/top`. It has no countdown — only a combat tag refuses it — so given to players it lifts them out of any trap and onto any roof | ✓ |
| `hcfcore.general.rename` | op | `/rename` | ✓ |
| `hcfcore.general.more` | op | `/more` | ✓ |
| `hcfcore.general.repair` | op | `/repair` | ✓ |
| `hcfcore.general.repair.all` | op | `/repair all`; includes `hcfcore.general.repair` | ✓ |
| `hcfcore.general.clearinventory` · `.others` | op | `/clearinventory` for yourself · for others | ✓ |
| `hcfcore.general.feed` · `.others` | op | `/feed` | ✓ |
| `hcfcore.general.fly` · `.others` | op | `/fly` | ✓ |
| `hcfcore.general.god` · `.others` | op | `/god` | ✓ |
| `hcfcore.general.speed` · `.others` | op | `/flyspeed` and `/walkspeed` | ✓ |
| `hcfcore.general.hat` | op | `/hat` | ✓ |
| `hcfcore.general.suicide` | op | `/suicide` | ✓ |
| `hcfcore.general.extinguish` · `.others` | op | `/extinguish` | ✓ |
| `hcfcore.general.workbench` | op | `/workbench` | ✓ |
| `hcfcore.general.anvil` | op | `/anvil` | ✓ |
| `hcfcore.general.enderchest` · `.others` | op | `/enderchest`; another player's, which can be changed | ✓ |
| `hcfcore.general.item` | op | `/i` | ✓ |
| `hcfcore.general.tphere` | op | `/tphere` | ✓ |
| `hcfcore.general.tppos` | op | `/tppos` | ✓ |
| `hcfcore.general.time` | op | `/day`, `/night` | ✓ |
| `hcfcore.general.weather` | op | `/sun`, `/rain` | ✓ |
| `hcfcore.chat.color` | op | `&` colour and format codes in chat, in private messages (`/msg`, `/reply`), in the staff channel and in `/broadcast`. Without it, what a player types is shown as typed | ✓ |

## Nodes nobody holds by default

Operators included. Each is a choice, so it is granted on purpose or not at all.

| Node | Grants | Why it is not given |
|---|---|---|
| `hcfcore.staff.vanish.see` | See vanished staff, and reach them by name with `/invsee`, `/freeze` and the `/staff` teleports | Given by default, staff would all see each other and vanish would be useless among them. Grant it to senior ranks. The node's name is set in `staff.yml` (`vanish.see-permission`). |
| `hcfcore.staff.invsee.edit` | Change a player's inventory through `/invsee` | An accidental drag in somebody's inventory cannot be undone and looks exactly like staff theft |
| `hcfcore.events.king.exempt` | Never drawn as King in Kill the King | Grant it to staff who should not be picked |

## Players

| Node | Default | Grants |
|---|---|---|
| `hcfcore.deathban.tier.short` | nobody | A 15-minute deathban instead of the hour — the example tier of `pvp.yml` |
| `hcfcore.effect.<command>` | op | An effect command of `effect-commands.yml` — `hcfcore.effect.speed` for `/speed`; a command may name another node |

### Deathban tiers

`pvp.yml` lists permission nodes with a ban length in seconds; the shortest one a player holds wins, and only the nodes listed there are checked. Add your own freely — they need not be declared anywhere else:

```yaml
deathban:
  duration-seconds: 3600
  permission-tiers:
    hcfcore.deathban.tier.short: 900
    myserver.rank.vip: 1800
    myserver.rank.mvp: 600
```

### Class permissions

A class in `classes.yml` can name any node under `permission`; a player without it wearing that class's set simply gets no class. The shipped classes need none. See [Classes](../gameplay/classes.md#creating-your-own).

### Kit permissions

`/kit create <id> [cooldown-seconds] [permission]` can name any node; a player then needs it to take that kit.

## Building through protection: the permission *and* the toggle

The permission says a rank may; `/staffbuild` says the staff member is choosing to right now. Both are needed, so a staff member looking around does not break a wall by accident, and an operator in normal play is stopped like anybody else:

- **territory protection** gives way to a holder of `hcfcore.claim.bypass` with `/staffbuild` on — the crowbar too;
- **the blocks-per-claim limits** give way to a holder of `hcfcore.limiter.bypass` with `/staffbuild` on.

Walking into a claim locked during SOTW is not building: `hcfcore.claim.bypass` alone lets staff in. A Mountain's own protection has its own node, `hcfcore.resourcenode.bypass`, which works alone.
