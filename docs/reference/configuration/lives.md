# lives.yml

Lives, the way back from a deathban. **HCF mode only**: this file is not used in kitmap.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/deathbans-and-lives.md#lives)

Every example on this page is **taken from the shipped `lives.yml`**. Changes apply with `/hcf reload`.

## Settings

```yaml title="lives.yml"
--8<-- "src/main/resources/lives.yml:general"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Lives at all |
| `starting-lives` | `0` | Lives a new player starts with; they come from staff, redeem codes or a store |
| `use-on-login` | `true` | A banned player who joins holding a life spends it and comes in |
| `allow-send` | `true` | `/lives send` |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/lives.yml).

<div class="hcf-shipped" markdown>

```yaml title="lives.yml"
--8<-- "src/main/resources/lives.yml"
```

</div>
