# Territory

*Configured in [`claims.yml`](../reference/configuration/claims.md). Commands: [`/team`](../reference/commands.md#team).*

Land is claimed **by chunk** (16 × 16 blocks, the full height of the world). Inside a team's land, nobody else builds, breaks or opens anything — until the team becomes [raidable](dtr-and-raids.md).

## Claiming

```text
/team claim        # the chunk you stand in
/team claim 2      # a 5 × 5 square of chunks around you
/team unclaim      # release the chunk you stand in
/team unclaimall   # release everything
```

- **How much**: 16 chunks plus 4 per member (`limits.base`, `limits.per-member`), with an optional hard cap (`limits.maximum`). One command claims at most 64 chunks (`max-per-command`), so a typo does not swallow the map; the radius goes up to 8.
- **Connected**: new land must touch the team's existing land in that world (`require-connected`). A team may still open a first territory in a world where it holds nothing.
- **Buffer**: at least 2 chunks from another team's land (`minimum-distance-to-others`, `0` to allow claiming right up against it).
- **No splitting**: unclaiming may not cut the territory in two (`allow-disconnecting`).
- **Where**: every world, unless `claimable-worlds` lists some. Never on server land or in the warzone.
- **Who**: co-leaders claim, the leader unclaims (`required-roles`).

`/team here` (alias `claiminfo`) tells who owns the chunk you stand in, whether it is protected or raidable, and your team's territory count. `/team map` draws the territory around you in chat, coloured by relation — yours, an ally's, an enemy's, server land, warzone, wilderness — readable on every client.

!!! info "Who owns a chunk never changes by raiding"
    A raid only opens a pillage window. The land stays with its team, and its protection comes back the moment its DTR climbs back above zero. **No team can ever claim another team's land**, raidable or not.

## Protection

In a protected team's land, nobody else:

- builds, breaks, or uses a bucket;
- uses doors, chests, buttons, levers and the like — **allies included** (`allow-ally-build: false`);
- sets off pressure plates or tripwires, or tramples crops.

What a player **holds** still works there, whatever block they are looking at: eating, drinking, throwing a potion or a pearl.

**Explosions** do not break blocks there either (`block-explosions`) — otherwise every refused block break would simply become a stick of TNT. The explosion is not cancelled: a charge on a border takes out the unprotected side and leaves the protected side standing.

**Crossing a border** is announced (`announce-territory`), once per chunk change.

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

— or `Down` — is an elevator: **right-click it and you are taken to the next floor that way**, straight up or down the sign's column. A floor is a firm block with two free blocks above it: no lava, fire or water. The finished sign turns gold, with an arrow.

- Write one wherever you may build — your land, or the wilderness. Using one is touching a block: refused on an enemy's land, unless that team is raidable.
- The sign is marked as an elevator when it is written: a sign that merely reads `[Elevator]` does nothing, and one keeps working if the words change in `elevators.yml`.
- A second each between rides (`cooldown-seconds`). An elevator can be kept to its team's own land (`own-territory-only`) and refused in combat (`blocked-in-combat`) — both off as shipped.

## Stuck

`/team stuck` gets a player out of land they cannot leave. After **60 seconds** (`stuck.warmup-seconds`), with the same cancelling rules, they are moved to the nearest free land — never to a random spot on the map. It works without a team, since a teamless player walled into somebody's base is the one who needs it most. A player who is not stuck is told so instead of being moved.

The countdown matters: without it, `/team stuck` would be a free exit from any trap and any raid.

## Lock (SOTW only)

During SOTW, `/team lockclaim` (alias `lock`) closes the team's land to every non-member, **allies included**:

- nobody can walk in, pearl in, chorus in or come through a portal — someone a portal still lands inside is moved out on arrival;
- anyone already inside is moved to the nearest free land.

Run it again to unlock. Every lock lapses when SOTW ends, and a restart releases them. Staff with `hcfcore.claim.bypass` walk in regardless. It exists so nobody camps inside a claim until PvP starts.

## Server land

A *server team* holds land that belongs to the server:

| Kind | Fighting | Typical use |
|---|---|---|
| **safe** | no | spawn |
| **combat** | yes | roads, event grounds |

```text
/team createsystem Spawn safe
/team forceclaim Spawn 3
/team setzone Spawn combat      # switch an existing server team
```

Either way, only staff build or interact on server land — **kit refill signs excepted** — and no player team claims it. Keep what players must use off server land. `/team forceclaim <team> [radius]` claims up to 32 chunks' radius around you for any team; `/team forceunclaim <team> [all]` releases the chunk you stand in, or everything.

## Warzone

The square around each listed world's centre, set in `claims.yml`:

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:warzone"
```

- Fighting allowed, no player claims.
- No building unless `warzone.allow-building` — explosions follow the same rule.
- Doors and chests stay usable.
- The radius is rounded outwards to whole chunks, so the warzone shares the territory border.
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
