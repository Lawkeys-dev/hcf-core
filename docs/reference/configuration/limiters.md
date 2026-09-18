# limiters.yml

Enchantment caps, potion caps, and the most of each block a team's territory may hold.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/items.md#enchantment-and-potion-caps)

Every example on this page is **taken from the shipped `limiters.yml`**. Changes apply with `/hcf reload`, to what players already carry too. **Every list ships empty**: which levels a map allows is its defining choice. `enabled: true` at the top switches everything on and off.

## Enchantments

```yaml title="limiters.yml"
--8<-- "src/main/resources/limiters.yml:enchantments"
```

| Key | As shipped | What it does |
|---|---|---|
| `caps` | `{}` | Enchantment id to its highest level; `0` forbids it. A cap above vanilla's maximum is reached at the anvil |
| `fix-existing-items` | `true` | Also lower items that arrive otherwise — loot, trades, other plugins — on join, on closing an inventory and in combat |

## Potions

```yaml title="limiters.yml"
--8<-- "src/main/resources/limiters.yml:potions"
```

| Key | As shipped | What it does |
|---|---|---|
| `caps` | `{}` | Effect to its highest level **from a potion** (drunk, splashed, lingering, tipped arrow); `0` forbids it: not brewed, drinking, throwing or shooting refused. Above the cap, the effect is brought down, not refused. A Bard's effects are not potions |

## Blocks per claim

```yaml title="limiters.yml"
--8<-- "src/main/resources/limiters.yml:claim-blocks"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | The block limits |
| `limits` | `{}` | Block name to the most a team's whole territory may hold; `0` forbids it in claims |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/limiters.yml).

<div class="hcf-shipped" markdown>

```yaml title="limiters.yml"
--8<-- "src/main/resources/limiters.yml"
```

</div>
