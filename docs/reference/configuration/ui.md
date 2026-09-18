# ui.yml

The scoreboard and the tab list.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/interface.md#scoreboard)

Every example on this page is **taken from the shipped `ui.yml`**. Changes apply with `/hcf reload`, which rebuilds every board. Every placeholder is in [Placeholders](../placeholders.md#scoreboard-and-tab-list).

## Scoreboard

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml:scoreboard"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | The scoreboard |
| `update-ticks` | `20` | How often it is redrawn; 20 is once a second |
| `title` | `&c&lHCF` | Its title |
| `lines` | see below | The rows, top to bottom |

**The rows.** A row whose placeholders all come out empty is dropped, so the conditional rows — combat, events, class, timers — appear only when they have something to say. At most 15 are shown.

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml:scoreboard-lines"
```

## Tab list

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml:tablist"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `false` | The header and footer |
| `header`, `footer` | lines | With the scoreboard's placeholders |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/ui.yml).

<div class="hcf-shipped" markdown>

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml"
```

</div>
