# dtr.yml

DTR (Deaths Till Raidable): the maximum, the cost of a death, the floor, regeneration and announcements.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/dtr-and-raids.md)

## What matters

- The defaults are the classic HCF scale: 1.1 per member capped at 6.6, a death costs 1.0.
- Regeneration is frozen 45 minutes after a death, then gives 0.1 every 3 minutes — and keeps running while the server is down.
- `enabled: false` means no team is ever raidable.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/dtr.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/dtr.yml).

<div class="hcf-shipped" markdown>

```yaml title="dtr.yml"
--8<-- "src/main/resources/dtr.yml"
```

</div>
