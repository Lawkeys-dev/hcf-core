# resourcenodes.yml

Mountains: regions, weighted block palettes, refill times anchored to midnight, protection, and refill speed.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/mountains.md)

## What matters

- **The shipped Mountains are examples at made-up coordinates**, refilled only by staff until you give them `interval-seconds` or `times`.
- A refill fills only air by default: a region under water or in solid rock refills nothing.
- `blocks-per-tick` spreads a refill over several ticks.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/resourcenodes.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/resourcenodes.yml).

<div class="hcf-shipped" markdown>

```yaml title="resourcenodes.yml"
--8<-- "src/main/resources/resourcenodes.yml"
```

</div>
