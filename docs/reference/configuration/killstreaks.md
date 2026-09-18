# killstreaks.yml

Killstreak rewards.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/kits.md#killstreaks)

Every example on this page is **taken from the shipped `killstreaks.yml`**. Changes apply with `/hcf reload`.

`enabled: true` at the top of the file switches rewards on and off.

## Rewards

**Ships empty.** The example is the commented one in the file:

```yaml title="killstreaks.yml"
--8<-- "src/main/resources/killstreaks.yml:rewards"
```

| Key | What it does |
|---|---|
| `rewards.<streak>` | Fires at **exactly** that streak |
| `broadcast` | A line to everybody, with `%player%` and `%streak%` |
| `commands` | Console commands, with `%player%` and `%streak%` |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/killstreaks.yml).

<div class="hcf-shipped" markdown>

```yaml title="killstreaks.yml"
--8<-- "src/main/resources/killstreaks.yml"
```

</div>
