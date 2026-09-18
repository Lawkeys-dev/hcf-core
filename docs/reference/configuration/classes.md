# classes.yml

Classes: Diamond, Bard, Archer, Rogue and Miner as shipped, and any class you write — armour set, passive effects, energy, held and right-click effects, archer tag, backstab, invisibility.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/classes.md)

## What matters

- A class is its **armour set**; every other part is optional, and any class may use any part — see [Creating your own](../../gameplay/classes.md#creating-your-own).
- `warmup-seconds` (10): how long a whole set is worn before its class turns on.
- `max-per-team` is `0` (no limit) for every shipped class.
- `abilities-in-safe-zones: false`: held and click effects do not work at spawn. Passive effects always do.
- A debuff (`targets: enemies`) reaches only players the user could hit.

Changes apply with `/hcf reload`, to classes already on too.

## The shipped file

This is `plugins/HCFCore/classes.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/classes.yml).

<div class="hcf-shipped" markdown>

```yaml title="classes.yml"
--8<-- "src/main/resources/classes.yml"
```

</div>
