# schedule.yml

Things on a clock: tips, daily announcements and commands, custom timers, and the key-all.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../server/schedules.md)

Every example on this page is **taken from the shipped `schedule.yml`**. Changes apply with `/hcf reload`. **Nothing ships switched on**: tips are off and every list is empty. `enabled: true` at the top switches the module.

## Time zone

```yaml title="schedule.yml"
--8<-- "src/main/resources/schedule.yml:time-zone"
```

## Tips

```yaml title="schedule.yml"
--8<-- "src/main/resources/schedule.yml:tips"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `false` | Tips at all; players can switch them off with `/settings` |
| `interval-seconds` | `300` | Time between two tips; at least 10 |
| `order` | `in-order` | `in-order` or `random` (never the same twice running) |
| `prefix` | `&8[&eTip&8] &7` | Put before each tip |
| `messages` | three examples | The tips |

## Daily schedules

**Ships empty.** The example is the commented one in the file:

```yaml title="schedule.yml"
--8<-- "src/main/resources/schedule.yml:schedules"
```

Each entry needs `times` and a `broadcast` or `commands` (from the console).

## Custom timers

**Ships empty.** The example is the commented one in the file:

```yaml title="schedule.yml"
--8<-- "src/main/resources/schedule.yml:timers"
```

A name listed here gets its `label`, and may say something (`end-broadcast`) and run `end-commands` (with `%timer%`) when it ends. Any other name works with the label typed in `/timer start`.

## Key-all

```yaml title="schedule.yml"
--8<-- "src/main/resources/schedule.yml:key-all"
```

| Key | As shipped | What it does |
|---|---|---|
| `commands` | `[]` | Run once per online player, from the console, with `%player%`. Empty, `/keyall` refuses |
| `broadcast` | a line | Said once done; `%count%` is how many received it |
| `label` | `&6Key-All` | The countdown's label on the scoreboard |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/schedule.yml).

<div class="hcf-shipped" markdown>

```yaml title="schedule.yml"
--8<-- "src/main/resources/schedule.yml"
```

</div>
