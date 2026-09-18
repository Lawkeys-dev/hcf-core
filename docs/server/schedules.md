# Schedules, timers and key-alls

*Configured in [`schedule.yml`](../reference/configuration/schedule.md).*

Things that happen on a clock. **Nothing here ships switched on**: tips are off and every list is empty — what a server announces, and what its keys are, belong to it.

Times are read in the file's `time-zone` — `"system"` follows the host's clock; write yours (`"Europe/Paris"`) so a host move does not shift everything.

## Tips

A line of advice every few minutes:

```yaml title="schedule.yml"
--8<-- "src/main/resources/schedule.yml:tips"
```

Set `enabled: true` to start them.

In `random` order, the same tip never shows twice running. Players can switch tips off for themselves with `/settings`.

## Daily schedules

Announcements and console commands at fixed times, every day:

```yaml title="schedule.yml"
--8<-- "src/main/resources/schedule.yml:schedules"
```

Each entry needs at least one time and a broadcast or a command. A time that passed while the server was off is not replayed.

## Custom timers

Countdowns staff start by hand, shown on everyone's scoreboard (`%timer_1%` to `%timer_3%`, the one ending soonest first) and ending with a line in chat:

```text
/timer start double-points 1h
/timer start event 30m &cEvent starts
/timer stop double-points
/timer                          # the timers running (aliases /timers, /customtimer)
```

Starting and stopping needs `hcfcore.schedule.admin`. A name described in `schedule.yml` gets its label, and can say and do something when it ends:

```yaml title="schedule.yml"
--8<-- "src/main/resources/schedule.yml:timers"
```

Any other name works too, with the label typed in the command. A running timer is not restarted by mistake. Timers live in memory: a restart ends them. The name `keyall` belongs to `/keyall`.

## Key-all

`/keyall` runs the `key-all` commands once for every player online, from the console — **now**, or after a countdown everybody sees on their scoreboard:

```text
/keyall          # now
/keyall 5m       # in five minutes
```

```yaml title="schedule.yml"
--8<-- "src/main/resources/schedule.yml:key-all"
```

**HCFCore has no crates**: point the commands at your crate plugin, economy or kits. With no command configured, `/keyall` refuses rather than announcing a key-all that gives nothing. Needs `hcfcore.schedule.admin`.
