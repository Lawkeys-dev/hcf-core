# Territory

*Configured in [`claims.yml`](../reference/configuration/claims.md). Commands: [`/team`](../reference/commands.md#team).*

Land is claimed **block by block**, the traditional HCF way: a claim is a rectangle drawn with the **claiming wand**, the full height of the world, paid from the team bank. Inside a team's land, nobody else builds, breaks or opens anything — until the team becomes [raidable](dtr-and-raids.md).

## Claiming with the wand

```text
/team claim        # hands you the claiming wand (a golden hoe)
/team unclaim      # release the claim you stand in - the whole claim
/team unclaimall   # release everything
```

With the wand in hand:

| Gesture | Does |
|---|---|
| **Left-click** a block | First corner |
| **Right-click** a block | Second corner |
| **Sneak + left-click** | Claim the rectangle between them |
| **Drop** the wand | Give up — the wand vanishes |

Both corners are part of the claim: from `0, 0` to `4, 4` is 5 × 5. Each corner shows as a column of glass **only you see**, then all four once both are picked, and the chat tells you the size, the **price** and — before you confirm — anything that would refuse it. A refused claim costs nothing and keeps the wand, to draw again. The wand cannot be put in a chest and is not dropped on death.

- **Price**: `0.25` per block of surface (`price.per-block`) — a 20 × 20 claim is 400 blocks, 100 — taken from the **team bank** (`/team deposit <amount>`). Money is what limits a team's land.
- **Refund**: `/team unclaim` gives back 75 % of what that claim cost (`price.refund-percent`) — of what was paid, never of today's price.
- **Size**: at least 5 × 5 (`sizes.min-side`), at most 128 blocks a side (`sizes.max-side`); optionally a cap on the number of claims and on the total surface (`max-claims`, `max-total-area`, `0` = none).
- **Connected**: a new claim must share an edge with the team's land in that world (`require-connected`) — touching at a corner is not enough. A team may still open a first territory in a world where it holds nothing.
- **Buffer**: at least 8 blocks from another team's land (`buffer-blocks`, `0` to allow claiming right up against it). Server land — spawn, roads — is no neighbour.
- **No splitting**: unclaiming may not cut the territory in two (`allow-disconnecting`).
- **Where**: every world, unless `claimable-worlds` lists some. Never over anybody's land, a Mountain's region or the warzone.
- **Who**: co-leaders claim, the leader unclaims (`required-roles`).

`/team here` (alias `claiminfo`) tells who owns the land you stand on, the claim's size and corners, whether it is protected or raidable, and the team's territory. `/team map` marks the territory around you **in the world**: a column on each corner of every claim within `map.pillars.radius-chunks` (2) chunks, one kind of block per team, drawn at random each time and named in the chat under the map. The columns are sent to you alone — nothing is placed — and go after `map.pillars.seconds` (20). `/team map chat` draws the old grid instead: one character per `map.chat.cell-blocks` (8) blocks, coloured by relation — yours, an ally's, an enemy's, server land, warzone, wilderness — and `map.style` says which of the two `/team map` gives by default.

!!! note "Upgrading from 0.7"
    Chunk claims of an earlier version are **converted automatically** on the first start: each team's chunks become rectangles covering exactly the same land, paid nothing (so they refund nothing). The console says how many.

!!! info "Who owns land never changes by raiding"
    A raid only opens a pillage window. The land stays with its team, and its protection comes back the moment its DTR climbs back above zero. **No team can ever claim another team's land**, raidable or not.

## Protection

In a protected team's land, nobody else:

- builds, breaks, or uses a bucket;
- uses doors, chests, buttons, levers and the like — **allies included** (`allow-ally-build: false`);
- sets off pressure plates or tripwires, or tramples crops.

What a player **holds** still works there, whatever block they are looking at: eating, drinking, throwing a potion or a pearl.

**Explosions** do not break blocks there either (`block-explosions`) — otherwise every refused block break would simply become a stick of TNT. The explosion is not cancelled: a charge on a border takes out the unprotected side and leaves the protected side standing.

**Crossing a border** is announced (`announce-territory`) the moment you step over it — borders run between blocks.

### Entities are protected like blocks

Item frames (and the item in them), paintings, armour stands, chest and hopper minecarts and chest boats cannot be taken from, broken or opened where their block could not be broken — by hand, by arrow or by blast.

!!! warning "Vehicles can be moved out"
    A minecart or a boat can still be *moved* out of a claim — walked into, reeled in with a rod, blown by a wind charge — and is fair game once outside: the server reports no event for a blast pushing it, so no plugin can hold it in place. **Keep valuables in chests, not in vehicles.**

### Machines are judged by the land they stand on

A piston, flowing water or lava, spreading fire, a dispenser, a growing tree or a sponge acting across a border is judged as if the owner of the land it starts from had done it:

- a piston in the wilderness cannot push into or pull out of a protected team's land;
- lava poured outside does not flow in, and fire lit outside does not spread in;
- a team's own machines work anywhere its members may build;
- server land may reach the warzone and other server land, never a player's base.

A dispenser may still fire arrows, snowballs and potions across a border, as a player could throw them.

### Raidable land

When a team is raidable — DTR at 0 or below, or during EOTW and the Purge — its land can be built in, broken and used by anyone but its allies (`allow-raid-building`), and explosions go through. Turning `allow-raid-building` off makes claims protected permanently, whatever the DTR.

## HQ and base

```text
/team sethq        /team hq
/team setbase      /team base
```

The HQ and a second base are set where you stand, inside the team's land (`homes.require-inside-territory`), by a co-leader. Going there takes a **10-second countdown** (`homes.warmup-seconds`) that damage or moving cancels — an ender pearl, a chorus fruit or a portal counts as moving. A combat tag refuses it at the start and again at the end.

## Elevators

*Configured in [`elevators.yml`](../reference/configuration/elevators.md).*

A sign written

```text
[Elevator]
Up
```

— or `Down` — is an elevator: **right-click it and you go up or down the sign's column.**

- **Linked elevators**: with another elevator sign up or down the same column, you go straight to it — nothing is needed between them, no pillar of blocks. You arrive in front of it with the sign at eye level — or at your feet, if that is where the room is — and never inside a wall: two free blocks, or the elevator refuses.
- **A sign alone in its column** takes you to the next floor that way, counted from the sign — where you stand does not matter: a block you can stand on with two free blocks above it — no lava, fire or water.

The finished sign turns gold.

- Write one wherever you may build — your land, or the wilderness. Using one is touching a block: refused on an enemy's land, unless that team is raidable.
- The sign is marked as an elevator when it is written: a sign that merely reads `[Elevator]` does nothing, and one keeps working if the words change in `elevators.yml`.
- A second each between rides (`cooldown-seconds`). An elevator can be kept to its team's own land (`own-territory-only`) and refused in combat (`blocked-in-combat`) — both off as shipped.

## Stuck

`/team stuck` gets a player out of land they cannot leave. After **60 seconds** (`stuck.warmup-seconds`), with the same cancelling rules, they are moved to the nearest free land — never to a random spot on the map. It works without a team, since a teamless player walled into somebody's base is the one who needs it most. A player who is not stuck is told so instead of being moved.

The countdown matters: without it, `/team stuck` would be a free exit from any trap and any raid.

## Lock (SOTW only)

During SOTW, `/team lockclaim` (alias `lock`) closes the team's land to every non-member, **allies included**:

- nobody can walk in, pearl in, chorus in or come through a portal — someone a portal still lands inside is moved out on arrival;
- anyone already inside is moved to the nearest free land;
- the players it refuses see a **wall of red glass** around the claim, the part of it near them, sent to each of them alone as the wand's columns are — never placed in the world. Members see nothing. It is `lock.wall` in `claims.yml`: the block, its height, how much of the border is drawn, or `enabled: false` for no wall at all.

Run it again to unlock. Every lock lapses when SOTW ends, and a restart releases them. Staff with `hcfcore.claim.bypass` walk in regardless. It exists so nobody camps inside a claim until PvP starts.

## Server land

A *server team* holds land that belongs to the server:

| Kind | Fighting | Typical use |
|---|---|---|
| **safe** | no | spawn |
| **combat** | yes | roads, event grounds |

```text
/team createsystem Spawn safe   # hands over the claiming wand, drawing Spawn's land
/team forceclaim Spawn          # the wand again, later
/team setzone Spawn combat      # switch an existing server team
```

Either way, only staff build or interact on server land — **kit refill signs excepted** — and no player team claims it. Keep what players must use off server land. `/team forceclaim <team>` hands staff the wand for any team — **free, and outside the size and placement rules**, since a road is three blocks wide and spawn may border the warzone; it still never draws over anybody's land. `/team forceunclaim <team> [all]` releases the claim you stand in, or everything, refunding nothing.

## Warzone

The square around each listed world's centre, set in `claims.yml`:

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:warzone"
```

- Fighting allowed, no player claims.
- No building unless `warzone.allow-building` — explosions follow the same rule.
- Doors and chests stay usable.
- The radius is exact, block for block, like claims.
- Off by default: a world not listed has no warzone.

The warzone only governs land **nobody owns**: spawn at its centre stays safe, a road claimed across it keeps its own rules, and a team that held land there before keeps it.

## Which rule wins

From most to least specific:

1. land **claimed by a team** follows that team's rules (server teams included);
2. on unclaimed land, a **Mountain's region** follows the Mountain's rules;
3. the rest of the **warzone** follows the warzone's;
4. beyond it is the **wilderness**, where anything goes.

## Switching territory off

`enabled: false` in `claims.yml` turns off every claim and every protection — player land, server land and the warzone — and keeps who owns what for when you turn it back on. PvP on safe zones is a separate switch, in `pvp.yml` (`safe-zones.enabled`).

## Staff

- **Building through protection** takes both `hcfcore.claim.bypass` **and** `/staffbuild` turned on — operators included. The permission says a rank may; the toggle says the staff member is choosing to, right now. See [Moderation](../server/moderation.md#staffbuild).
- `/team forceclaim` and `/team forceunclaim` need `hcfcore.claim.admin`.
