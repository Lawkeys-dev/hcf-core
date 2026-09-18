# resourcenodes.yml

Mountains: regions, weighted block palettes, refill times anchored to midnight, protection, and refill speed.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/mountains.md)

Every example on this page is **taken from the shipped `resourcenodes.yml`**. Changes apply with `/hcf reload`.

!!! warning "The shipped Mountains are examples"
    Their regions sit at made-up coordinates, and they refill only when staff ask (`/resourcenode refill <id>`) until you give them `interval-seconds` or `times`.

## General settings

```yaml title="resourcenodes.yml"
--8<-- "src/main/resources/resourcenodes.yml:general"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Every Mountain |
| `tick-seconds` | `5` | How often the refill schedule is examined |
| `time-zone` | `system` | The zone refill times are read in |
| `blocks-per-tick` | `4000` | Block positions examined per tick, shared by running refills |
| `apply-physics` | `false` | Physics updates on placed blocks — only for sand, gravel and the like |
| `skip-occupied-blocks` | `true` | A refill leaves empty the blocks a player's body is in |

## A Mountain

Under `nodes:`, one entry per Mountain. The Glowstone Mountain as shipped:

```yaml title="resourcenodes.yml — the Glowstone Mountain"
--8<-- "src/main/resources/resourcenodes.yml:glowstone-mountain"
```

**The region**, the box between two corners, bounds included, in any order:

```yaml title="resourcenodes.yml"
--8<-- "src/main/resources/resourcenodes.yml:node-region"
```

**What it refills with**, a weighted palette (or a plain list for equal weights):

```yaml title="resourcenodes.yml"
--8<-- "src/main/resources/resourcenodes.yml:node-blocks"
```

**What a refill may overwrite:**

```yaml title="resourcenodes.yml"
--8<-- "src/main/resources/resourcenodes.yml:node-replace"
```

**When it refills, and the announcements:**

```yaml title="resourcenodes.yml"
--8<-- "src/main/resources/resourcenodes.yml:node-refill"
```

**Its protection:**

```yaml title="resourcenodes.yml"
--8<-- "src/main/resources/resourcenodes.yml:node-protection"
```

| Key | As shipped | What it does |
|---|---|---|
| `display-name` | an example | Its name in messages |
| `world`, `corner-1`, `corner-2` | an example | The region |
| `blocks` | glowstone 1, netherrack 4 | Block name to weight; relative, not percentages |
| `replace.air` | `true` | Refill what was mined out |
| `replace.blocks` | `[]` | Extra blocks a refill may replace |
| `refill.interval-seconds` | `0` | Every N seconds, anchored to local midnight; `0` for none |
| `refill.times` | `[]` | Explicit local times |
| `announce-before-seconds` | `[300, 60]` | Warnings before a refill |
| `announce-refill` | `true` | Announce the refill once done |
| `fill-on-start` | `false` | Fill the region when the server starts |
| `protection.prevent-build` | `true` | No building inside, by hand, piston or liquid |
| `protection.break-policy` | `PALETTE_ONLY` | What can be mined: `PALETTE_ONLY` or `ANY` |
| `protection.prevent-claim` | `true` | No claim overlapping the region |
| `protection.prevent-explosions` | `true` | Explosions cannot break the region |

A second Mountain, with another palette:

```yaml title="resourcenodes.yml — the Ore Mountain"
--8<-- "src/main/resources/resourcenodes.yml:ore-mountain"
```

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/resourcenodes.yml).

<div class="hcf-shipped" markdown>

```yaml title="resourcenodes.yml"
--8<-- "src/main/resources/resourcenodes.yml"
```

</div>
