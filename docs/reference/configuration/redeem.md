# redeem.yml

Redeem codes. The codes themselves are made in game with `/redeemadmin` and stored in the database.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../server/redeem.md)

Every example on this page is **taken from the shipped `redeem.yml`**. Changes apply with `/hcf reload`. `enabled: true` at the top switches the module.

## Failed attempts

```yaml title="redeem.yml"
--8<-- "src/main/resources/redeem.yml:failed-attempt"
```

| Key | As shipped | What it does |
|---|---|---|
| `failed-attempt-cooldown-seconds` | `3` | Wait after a failed code, so codes cannot be guessed by trying names |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/redeem.yml).

<div class="hcf-shipped" markdown>

```yaml title="redeem.yml"
--8<-- "src/main/resources/redeem.yml"
```

</div>
