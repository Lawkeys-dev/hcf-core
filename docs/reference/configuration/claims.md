# claims.yml

Territory: claim limits, placement rules, protection, HQ and base, the `/team hq` and `/team stuck` countdowns, and the warzone.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/territory.md)

Every example on this page is **taken from the shipped `claims.yml`**. Changes apply with `/hcf reload`. A limit of `0` means no limit.

## Switch

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:enabled"
```

`enabled: false` turns off every claim and every protection — player land, server land, the warzone — and keeps who owns what.

## Claiming wand

Claims are rectangles drawn block by block with the wand — left-click one corner, right-click the other, sneak + left-click to claim, drop it to give up. See [Territory](../../gameplay/territory.md#claiming-with-the-wand).

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:wand"
```

| Key | As shipped | What it does |
|---|---|---|
| `material` | `GOLDEN_HOE` | The item |
| `name`, `lore` | see above | Its name and description |
| `pillar-material` | `GLASS` | The columns shown, to the holder only, on the corners |
| `pillar-height` | `12` | How high they rise, 1 to 64 |
| `pillar-marker-material` | `GLOWSTONE` | One block in every few is this instead — a column of glass alone is invisible to a player using a clear-glass resource pack |
| `pillar-marker-every` | `6` | Which block: `6` gives a marker, then five of `pillar-material`, and so on (`0111110111110`) |

## Price

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:price"
```

| Key | As shipped | What it does |
|---|---|---|
| `per-block` | `0.25` | Price of one block of surface, paid from the team bank; `0` is free |
| `refund-percent` | `75` | Share of what a claim cost that unclaiming it gives back |

Staff claims (`/team forceclaim`) and server land are free, and a staff unclaim refunds nothing.

## Sizes

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:sizes"
```

| Key | As shipped | What it does |
|---|---|---|
| `min-side` | `5` | Shortest side of a claim, in blocks |
| `max-side` | `128` | Longest side; `0` = none |
| `max-claims` | `0` | Separate claims one team may hold; `0` = no limit |
| `max-total-area` | `0` | Blocks of surface one team may hold in all; `0` = no limit |

Staff claims and server land follow none of these.

## Placement

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:placement"
```

| Key | As shipped | What it does |
|---|---|---|
| `require-connected` | `true` | A new claim must share an edge with the team's land in that world |
| `buffer-blocks` | `8` | Blocks of land between two teams' claims; `0` lets them touch. Server land is no neighbour |
| `allow-disconnecting` | `false` | Whether unclaiming may split a territory in two |

## Map

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:map"
```

| Key | As shipped | What it does |
|---|---|---|
| `style` | `pillars` | How `/team map` is drawn: `pillars` in the world, or `chat`. `/team map pillars` and `/team map chat` ask for one either way |
| `pillars.radius-chunks` | `2` | How far the pillars look for claims, 1 to 16 |
| `pillars.height` | `12` | How high a column rises above the ground, 1 to 64 |
| `pillars.seconds` | `20` | How long the pillars stay, 1 to 600 |
| `pillars.materials` | 12 concretes | The blocks the columns may be made of; each team drawn takes one at random. Anything without collision is left out with a warning |
| `chat.cell-blocks` | `8` | Blocks one character of the chat map stands for, 1 to 64; each is judged by the block in its middle |
| `chat.radius-x` · `radius-z` | `12` · `6` | How many cells the chat map draws each way, 1 to 32 |

## Lock wall

The wall a locked claim shows the players it refuses — red glass on its border, sent to that player alone, like the wand's columns. Members see nothing.

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:lock"
```

| Key | As shipped | What it does |
|---|---|---|
| `wall.enabled` | `true` | Whether a locked claim shows the wall at all |
| `wall.material` | `RED_STAINED_GLASS` | The block it is drawn in |
| `wall.height` | `3` | How high it rises above the ground, 1 to 32 |
| `wall.radius-blocks` | `24` | How much of the border is drawn around the player, 4 to 128 |
| `wall.refresh-seconds` | `1` | How often it is redrawn as players move, 1 to 60 |

## Protection

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:protection"
```

| Key | As shipped | What it does |
|---|---|---|
| `allow-ally-build` | `false` | Allies may build and use things in the team's land |
| `allow-raid-building` | `true` | Anyone but allies may build in a raidable team's land — the pillage window. `false` protects claims whatever the DTR |
| `announce-territory` | `true` | Say when a player crosses a border |
| `block-explosions` | `true` | Explosions do not break protected blocks. Leave it on |

## HQ, base and stuck

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:homes"
```

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:stuck"
```

| Key | As shipped | What it does |
|---|---|---|
| `homes.enabled` | `true` | HQ and second base at all |
| `homes.require-inside-territory` | `true` | A home must stand on the team's land |
| `homes.warmup-seconds` | `10` | Countdown of `/team hq` and `/team base`; damage or moving cancels it |
| `stuck.warmup-seconds` | `60` | Countdown of `/team stuck` |

## Worlds

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:claimable-worlds"
```

An empty list allows every world.

## Roles

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:required-roles"
```

The minimum role for each territory action: `leader`, `co-leader` or `member`. `lock-claim` is `/team lockclaim`, during SOTW only.

## Warzone

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:warzone"
```

| Key | As shipped | What it does |
|---|---|---|
| `display-name` | `&cWarzone` | Its name in messages and on `/team map` |
| `allow-building` | `false` | Whether players may build on unclaimed warzone land |
| `worlds.<world>.radius` | none | Blocks from the centre to each edge, exactly |
| `worlds.<world>.center-x`, `center-z` | `0`, `0` | The centre |

No world is listed as shipped: no warzone until you add one. Kill the King needs it.

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/claims.yml).

<div class="hcf-shipped" markdown>

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml"
```

</div>
