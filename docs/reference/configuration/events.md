# events.yml

Capture events: KOTH zones, Citadels (a zone to hold inside a claimed Citadel with its restrictions), Kill the King, Conquest, their schedules and rewards, and the zone holograms.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/events.md)

## What matters

- **The shipped events are examples at made-up coordinates**, with no schedule: move them onto your map, then give them times.
- Ids are shared by every kind of event and must be unique.
- `contest-policy`: `RESET` or `PAUSE` — the setting that changes most how a KOTH plays.
- Kill the King's arena is the warzone of its world, set in `claims.yml`.
- Write `time-zone`, e.g. `"Europe/Paris"`.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/events.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/events.yml).

<div class="hcf-shipped" markdown>

```yaml title="events.yml"
--8<-- "src/main/resources/events.yml"
```

</div>
