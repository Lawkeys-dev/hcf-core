# chat.yml

The public chat format, the kill count, local chat, and team chat logging.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/interface.md#public-chat)

## What matters

- `format` needs `%message%` — a format without it is refused.
- `range-blocks: 0` is server-wide chat; anything else makes chat local.
- Team chat keeps working with `enabled: false`.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/chat.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/chat.yml).

<div class="hcf-shipped" markdown>

```yaml title="chat.yml"
--8<-- "src/main/resources/chat.yml"
```

</div>
