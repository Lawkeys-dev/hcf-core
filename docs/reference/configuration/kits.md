# kits.yml

Kits: how they are given, the layout editor, refill signs, and abilities (partner items).

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/kits.md)

## What matters

- Kits themselves are made in game with `/kit create`, not here.
- `clear-before-giving: true` is the kitmap behaviour: you get exactly the kit.
- `abilities` ships empty.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/kits.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/kits.yml).

<div class="hcf-shipped" markdown>

```yaml title="kits.yml"
--8<-- "src/main/resources/kits.yml"
```

</div>
