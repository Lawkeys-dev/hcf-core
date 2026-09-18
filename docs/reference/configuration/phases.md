# phases.yml

The map phases: SOTW length and start date, EOTW start date, and the Purge.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/map-phases.md)

## What matters

- Nothing happens until a phase starts — by command, or at its date.
- Dates are `"yyyy-MM-dd HH:mm"` in the file's `time-zone`; a date left over from an earlier map is harmless.
- The Purge's `schedule` is empty: when a server purges is its own call.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/phases.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/phases.yml).

<div class="hcf-shipped" markdown>

```yaml title="phases.yml"
--8<-- "src/main/resources/phases.yml"
```

</div>
