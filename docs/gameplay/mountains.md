# Mountains

*Configured in [`resourcenodes.yml`](../reference/configuration/resourcenodes.md). Command: `/resourcenode` (aliases `/node`, `/mountain`).*

A Mountain is a region that **refills with blocks on a clock** — a Glowstone Mountain in the Nether, an Ore Mountain in the warzone. Nothing is captured and nobody wins: the server announces the refill, several teams show up at once, and the fight is the point.

```text
/resourcenode                 # when each Mountain refills
/resourcenode refill <id>     # staff: refill one now (hcfcore.resourcenode.admin)
```

Refills also appear in `/events`, next to the capture events.

## Defining one

```yaml
nodes:
  glowstone-mountain:
    display-name: "&eGlowstone Mountain"
    world: world_nether
    corner-1: {x: 40, y: 40, z: 40}
    corner-2: {x: 70, y: 80, z: 70}
    blocks:
      GLOWSTONE: 1
      NETHERRACK: 4
    refill:
      interval-seconds: 14400
      times: []
    announce-before-seconds: [300, 60]
```

- **The region** is the box between the two corners, bounds included, in any order. Keep it to the mountain itself: every position inside is examined on each refill.
- **The palette** is weighted: `GLOWSTONE: 1` beside `NETHERRACK: 4` means one block in five is glowstone. Weights are relative — nothing has to add up to 100. A plain list (`blocks: [GLOWSTONE, NETHERRACK]`) gives equal weights. An unknown block name is reported at startup and skipped, never guessed.

## When it refills

Refill times are **absolute**, not "last refill plus the interval":

- `interval-seconds` is **anchored to local midnight** — `14400` means 00:00, 04:00, 08:00... every day, whatever the server did in between. A restart cannot shift the hours, and players can learn them.
- `times` adds explicit local times of day. Both sources merge.
- Both empty: only staff refill it.

The shipped examples are at `0`, staff only, until you move them onto your map.

**Warnings** go out ahead of each refill (`announce-before-seconds`) — that is what turns a refill into a rendez-vous rather than a surprise: teams need time to cross the map. The refill itself is announced **when it is done**, not when it starts (`announce-refill`).

## What a refill does

- **It repairs, it does not rebuild**: by default it fills only **air** — what players mined out. `replace.blocks` lists extra blocks it may also replace, to repair a region players filled with something of their own.
- **It never walls a player in**: the blocks a player's body occupies stay empty until the next refill (`skip-occupied-blocks`), instead of the resource closing over them. Spectators are not counted.
- **It is spread over several ticks** (`blocks-per-tick`, 4000 shared between running refills) so a mountain of tens of thousands of blocks does not freeze the server. Its chunks are loaded in the background and held until it finishes.
- `fill-on-start` fills the region once when the server starts — off by default.

!!! tip "A refill changed nothing?"
    A refill fills only air: a region under water or inside solid rock has nothing to fill. Move the region, or list the blocks it may replace under `replace.blocks`.

## Protection

Each Mountain protects its region (`protection`):

| Setting | Default | Effect |
|---|---|---|
| `prevent-build` | on | No building — no towering over it, no walling it off, no lava. Pistons cannot push blocks in or pull the structure out, and liquid poured outside does not flow in |
| `break-policy` | `PALETTE_ONLY` | Only the palette's blocks can be mined, so the structure survives for the next refill. `ANY` lets players mine anything — and eventually flatten it |
| `prevent-claim` | on | No team can claim land overlapping the region |
| `prevent-explosions` | on | Explosions cannot break the region — otherwise the break policy would be decorative |

Inside the warzone, a Mountain's own rules apply in its region, so its resources stay minable. Staff with `hcfcore.resourcenode.bypass` build and mine freely inside a region.
