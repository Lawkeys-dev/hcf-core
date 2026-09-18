# enchants.yml

Custom enchants: nine well-known HCF and kitmap enchants, each editable, removable or copyable under a new id.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/items.md#custom-enchants)

Every example on this page is **taken from the shipped `enchants.yml`**. Changes apply with `/hcf reload`; lowering a `max-level` lowers items already made.

`enabled: true` at the top of the file switches every custom enchant on and off.

## Every enchant

```yaml title="enchants.yml"
--8<-- "src/main/resources/enchants.yml:enchants"
```

**Keys every enchant has:**

| Key | What it does |
|---|---|
| `name` | Shown in the item's lore, `&` colours |
| `type` | `effect`, `hellforged`, `implanted`, `recover` or `autosmelt` |
| `max-level` | The highest level |
| `applies-to` | `helmet`, `chestplate`, `leggings`, `boots`, `sword`, `axe`, `pickaxe`, `shovel`, `hoe`, `bow`, `crossbow`, `trident`, or the groups `armor`, `tool`, `weapon` |

## By type

**`effect`** — a potion effect while the piece is worn:

```yaml title="enchants.yml"
--8<-- "src/main/resources/enchants.yml:effect-enchant"
```

`effect` is the effect's name. Another effect enchant is one more block like this one.

**`hellforged`** — repairs the worn piece each time its wearer takes damage:

```yaml title="enchants.yml"
--8<-- "src/main/resources/enchants.yml:hellforged"
```

`repair-per-level`: durability restored per level, per hit.

**`implanted`** — keeps its wearer fed:

```yaml title="enchants.yml"
--8<-- "src/main/resources/enchants.yml:implanted"
```

`food-per-level` every `interval-seconds`.

**`recover`** — Regeneration when health falls low:

```yaml title="enchants.yml"
--8<-- "src/main/resources/enchants.yml:recover"
```

Regeneration at the enchant's level for `regeneration-seconds` when health falls to `below-health` or less (20 is full), then `cooldown-seconds` before it can fire again.

**`autosmelt`** — a tool that smelts what it mines, as a furnace would:

```yaml title="enchants.yml"
--8<-- "src/main/resources/enchants.yml:autosmelt"
```

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/enchants.yml).

<div class="hcf-shipped" markdown>

```yaml title="enchants.yml"
--8<-- "src/main/resources/enchants.yml"
```

</div>
