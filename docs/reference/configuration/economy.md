# economy.yml

The economy: starting balance, ceiling, currency, and `/pay`.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/economy.md)

## What matters

- Players start with 100.
- `maximum-balance: 0` means no ceiling; a credit that would cross one is refused, never clamped.
- `enabled: false` also disables the team bank commands, and Vault reports the economy as disabled.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/economy.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/economy.yml).

<div class="hcf-shipped" markdown>

```yaml title="economy.yml"
--8<-- "src/main/resources/economy.yml"
```

</div>
