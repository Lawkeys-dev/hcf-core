# ui.yml

The scoreboard and the tab list.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/interface.md#scoreboard)

## What matters

- A scoreboard line whose placeholders all come out empty is dropped — that is how conditional lines work.
- 15 lines at most are shown.
- The tab list ships off.
- Every placeholder is in [Placeholders](../placeholders.md#scoreboard-and-tab-list).

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/ui.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/ui.yml).

<div class="hcf-shipped" markdown>

```yaml title="ui.yml"
--8<-- "src/main/resources/ui.yml"
```

</div>
