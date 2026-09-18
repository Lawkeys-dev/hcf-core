# kits.yml

Kits: how they are given, the layout editor, and refill signs. The kits themselves are made in game with `/kit create`.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/kits.md)

Every example on this page is **taken from the shipped `kits.yml`**. Changes apply with `/hcf reload`.

## Switch

```yaml title="kits.yml"
--8<-- "src/main/resources/kits.yml:enabled"
```

## Giving a kit

```yaml title="kits.yml"
--8<-- "src/main/resources/kits.yml:giving"
```

| Key | As shipped | What it does |
|---|---|---|
| `clear-before-giving` | `true` | Empty the inventory first — you get exactly the kit. `false` adds to it and drops the overflow |
| `layout-editor` | `true` | `/kit layout` |

## Refill signs

```yaml title="kits.yml"
--8<-- "src/main/resources/kits.yml:refill-signs"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Signs whose first line is `[Kit]` hand out the kit named on the second |
| `line` | `Kit` | The word in the brackets |
| `cooldown-seconds` | `3` | Anti-spam wait, separate from the kit's cooldown |

Partner items are in [`abilities.yml`](abilities.md).

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/kits.yml).

<div class="hcf-shipped" markdown>

```yaml title="kits.yml"
--8<-- "src/main/resources/kits.yml"
```

</div>
