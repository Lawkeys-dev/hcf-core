# chat.yml

The public chat format, the kill count, local chat, and team chat logging.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/interface.md#public-chat)

Every example on this page is **taken from the shipped `chat.yml`**. Changes apply with `/hcf reload`.

## Switch

```yaml title="chat.yml"
--8<-- "src/main/resources/chat.yml:enabled"
```

Off, chat is left as the server renders it. Team chat keeps working either way.

## Format

```yaml title="chat.yml"
--8<-- "src/main/resources/chat.yml:format"
```

| Key | As shipped | What it does |
|---|---|---|
| `format` | `%kills%%prefix%&f%player%%suffix%&7: &f%message%` | The public line. `%prefix%` and `%suffix%` come from LuckPerms. **Must contain `%message%`**, or it is refused |
| `kills-format` | `&7[&c%value%&7]&r ` | What `%kills%` becomes; empty for a player with no kills |

## Range and logging

```yaml title="chat.yml"
--8<-- "src/main/resources/chat.yml:range-blocks"
```

```yaml title="chat.yml"
--8<-- "src/main/resources/chat.yml:log-team-chat"
```

| Key | As shipped | What it does |
|---|---|---|
| `range-blocks` | `0` | How far public chat carries; `0` is the whole server |
| `log-team-chat` | `true` | Copy team and ally chat to the console and log |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/chat.yml).

<div class="hcf-shipped" markdown>

```yaml title="chat.yml"
--8<-- "src/main/resources/chat.yml"
```

</div>
