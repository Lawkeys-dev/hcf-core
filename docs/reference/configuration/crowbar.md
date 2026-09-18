# crowbar.yml

The crowbar that takes End portal frames out: its item, uses, cooldown and cost.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/items.md#crowbar)

Every example on this page is **taken from the shipped `crowbar.yml`**. Changes apply with `/hcf reload`. `enabled: true` at the top switches the module.

## The item

```yaml title="crowbar.yml"
--8<-- "src/main/resources/crowbar.yml:item"
```

| Key | As shipped | What it does |
|---|---|---|
| `material` | `GOLDEN_HOE` | The item |
| `name` | `&6&lCrowbar` | Its name |
| `lore` | three lines | Its lines; `%uses%` is the uses left |

## Uses, cooldown and cost

```yaml title="crowbar.yml"
--8<-- "src/main/resources/crowbar.yml:limits"
```

| Key | As shipped | What it does |
|---|---|---|
| `uses` | `0` | Frames one crowbar takes out before it breaks; `0` no limit |
| `cooldown-seconds` | `0` | Wait between two uses |
| `cost` | `0` | Money one use costs; nothing is taken for a refused use |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/crowbar.yml).

<div class="hcf-shipped" markdown>

```yaml title="crowbar.yml"
--8<-- "src/main/resources/crowbar.yml"
```

</div>
