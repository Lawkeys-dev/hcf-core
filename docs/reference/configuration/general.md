# general.yml

The utility commands: `/spawn`, `/logout`, `/rename` and private messages.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../server/utilities.md)

Every example on this page is **taken from the shipped `general.yml`**. Changes apply with `/hcf reload`.

## Switch

```yaml title="general.yml"
--8<-- "src/main/resources/general.yml:enabled"
```

Turn the whole module off if your essentials plugin already provides these commands. `/settings` and `/cobble` keep working.

## /spawn

```yaml title="general.yml"
--8<-- "src/main/resources/general.yml:spawn"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | `/spawn` alone |
| `world` | empty | Whose spawn point; empty for the player's current world |
| `warmup-seconds` | `5` | Countdown that damage or movement cancels; `0` at once |

## /logout, /rename and private messages

```yaml title="general.yml"
--8<-- "src/main/resources/general.yml:logout"
```

```yaml title="general.yml"
--8<-- "src/main/resources/general.yml:rename"
```

```yaml title="general.yml"
--8<-- "src/main/resources/general.yml:private-messages"
```

| Key | As shipped | What it does |
|---|---|---|
| `logout-seconds` | `30` | `/logout`'s countdown; refused while combat-tagged |
| `rename-max-length` | `32` | Longest name `/rename` accepts |
| `private-messages` | `true` | `/msg`, `/reply`, `/togglepm`, `/ignore` |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/general.yml).

<div class="hcf-shipped" markdown>

```yaml title="general.yml"
--8<-- "src/main/resources/general.yml"
```

</div>
