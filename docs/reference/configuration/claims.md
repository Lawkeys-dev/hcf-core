# claims.yml

Territory: claim limits, placement rules, protection, HQ and base, the `/team hq` and `/team stuck` countdowns, and the warzone.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/territory.md)

Every example on this page is **taken from the shipped `claims.yml`**. Changes apply with `/hcf reload`. A limit of `0` means no limit.

## Switch

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:enabled"
```

`enabled: false` turns off every claim and every protection — player land, server land, the warzone — and keeps who owns what.

## Limits

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:limits"
```

| Key | As shipped | What it does |
|---|---|---|
| `base` | `16` | Chunks every team may claim |
| `per-member` | `4` | Extra chunks per member |
| `maximum` | `0` | A hard cap whatever the size |
| `max-per-command` | `64` | Chunks one command may claim; staff overrides are exempt |

## Placement

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml:placement"
```

| Key | As shipped | What it does |
|---|---|---|
| `require-connected` | `true` | New chunks must touch the team's land in that world |
| `minimum-distance-to-others` | `2` | Chunks between two teams' land; `0` lets them touch |
| `allow-disconnecting` | `false` | Whether unclaiming may split a territory in two |

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
| `worlds.<world>.radius` | none | Blocks from the centre to each edge, rounded outwards to whole chunks |
| `worlds.<world>.center-x`, `center-z` | `0`, `0` | The centre |

No world is listed as shipped: no warzone until you add one. Kill the King needs it.

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/claims.yml).

<div class="hcf-shipped" markdown>

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml"
```

</div>
