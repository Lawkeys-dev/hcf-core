# phases.yml

The map phases: SOTW length and start date, EOTW start date, and the Purge.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/map-phases.md)

Every example on this page is **taken from the shipped `phases.yml`**. Changes apply with `/hcf reload`. Nothing happens until a phase starts — by command, or at its date.

## Time zone

```yaml title="phases.yml"
--8<-- "src/main/resources/phases.yml:time-zone"
```

The zone dates and times are read in. Write yours: `"Europe/Paris"`.

## SOTW

```yaml title="phases.yml"
--8<-- "src/main/resources/phases.yml:sotw"
```

| Key | As shipped | What it does |
|---|---|---|
| `duration-seconds` | `7200` | A SOTW's length: a scheduled one, and `/sotw start` without a duration |
| `start-at` | empty | `"yyyy-MM-dd HH:mm"`: the SOTW runs from then to then plus its duration. A date left from an earlier map does nothing |
| `announce-at-seconds` | `[3600, 1800, 600, 300, 60, 10]` | Remaining-time marks |

## EOTW

```yaml title="phases.yml"
--8<-- "src/main/resources/phases.yml:eotw"
```

| Key | As shipped | What it does |
|---|---|---|
| `start-at` | empty | `"yyyy-MM-dd HH:mm"` at which EOTW starts by itself; it waits for a running SOTW to end |
| `start-window-seconds` | `3600` | How late a scheduled EOTW may still start after the server was down |

## The Purge

```yaml title="phases.yml"
--8<-- "src/main/resources/phases.yml:purge"
```

| Key | As shipped | What it does |
|---|---|---|
| `duration-seconds` | `1800` | A Purge's length |
| `schedule` | `[]` | Local times at which it starts by itself, e.g. `["20:00"]` |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/phases.yml).

<div class="hcf-shipped" markdown>

```yaml title="phases.yml"
--8<-- "src/main/resources/phases.yml"
```

</div>
