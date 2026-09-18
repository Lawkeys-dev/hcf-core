# dtr.yml

DTR (Deaths Till Raidable): the maximum, the cost of a death, the floor, regeneration and announcements.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/dtr-and-raids.md)

Every example on this page is **taken from the shipped `dtr.yml`**. Changes apply with `/hcf reload`.

## Switch

```yaml title="dtr.yml"
--8<-- "src/main/resources/dtr.yml:enabled"
```

`enabled: false`: no team is ever raidable, and deaths cost nothing.

## Maximum

```yaml title="dtr.yml"
--8<-- "src/main/resources/dtr.yml:maximum"
```

| Key | As shipped | What it does |
|---|---|---|
| `base` | `0.0` | DTR every team has, whatever its size |
| `per-member` | `1.1` | DTR added per member |
| `cap` | `6.6` | The ceiling whatever the size; `0` uncapped |

## Deaths

```yaml title="dtr.yml"
--8<-- "src/main/resources/dtr.yml:loss"
```

| Key | As shipped | What it does |
|---|---|---|
| `loss-per-death` | `1.0` | DTR a member's death costs |
| `minimum` | `-5.0` | The floor; zero or negative, or no team could become raidable |

## Regeneration

```yaml title="dtr.yml"
--8<-- "src/main/resources/dtr.yml:regeneration"
```

| Key | As shipped | What it does |
|---|---|---|
| `freeze-seconds` | `2700` | How long regeneration waits after a death (45 minutes) |
| `amount` | `0.1` | DTR given per step; `0` stops regeneration |
| `interval-seconds` | `180` | Length of a step. A partial step gives nothing |

## Announcements

```yaml title="dtr.yml"
--8<-- "src/main/resources/dtr.yml:announcements"
```

| Key | As shipped | What it does |
|---|---|---|
| `on-death` | `true` | Tell the team what a death cost |
| `on-raidable-change` | `true` | Announce to the server when a team becomes raidable, and protected again |
| `poll-seconds` | `60` | How often a return to protection is checked, for the announcement only |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/dtr.yml).

<div class="hcf-shipped" markdown>

```yaml title="dtr.yml"
--8<-- "src/main/resources/dtr.yml"
```

</div>
