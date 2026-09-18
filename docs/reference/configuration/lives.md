# lives.yml

Lives, the way back from a deathban. **HCF mode only**: this file is not used in kitmap.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/deathbans-and-lives.md#lives)

## What matters

- Players start with 0 lives: they come from staff, a redeem code or a store.
- `use-on-login` lets a banned player spend their own life by trying to join.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/lives.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/lives.yml).

<div class="hcf-shipped" markdown>

```yaml title="lives.yml"
--8<-- "src/main/resources/lives.yml"
```

</div>
