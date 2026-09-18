# claims.yml

Territory: claim limits, placement rules, protection, HQ and base, the `/team hq` and `/team stuck` countdowns, and the warzone.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/territory.md)

## What matters

- `limits` and `placement` decide how much land a team holds and where.
- `protection.block-explosions` should stay on — without it, TNT does what a pickaxe is refused.
- `warzone.worlds` is empty: no world has a warzone until you list one. Kill the King needs it.
- `enabled: false` turns off every claim and protection, and keeps who owns what.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/claims.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/claims.yml).

<div class="hcf-shipped" markdown>

```yaml title="claims.yml"
--8<-- "src/main/resources/claims.yml"
```

</div>
