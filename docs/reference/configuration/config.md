# config.yml

Server-wide settings: the language, the game mode and the storage. Every gameplay setting lives in the module files instead.

## What matters

- `language` picks `lang/<language>.yml` — see [Messages and translation](../messages.md).
- `kitmap-mode` chooses HCF (`false`) or Kitmap (`true`) — see [Game modes](../../getting-started/game-modes.md).
- `storage` chooses SQLite or MySQL, and how often data is saved — see [Installation](../../getting-started/installation.md#storage).
- Keep the MySQL password in the `HCFCORE_MYSQL_PASSWORD` environment variable, not in this file.

**`kitmap-mode` and the whole `storage` section are read at startup only**: changing them needs a restart. `language` applies with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/config.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/config.yml).

<div class="hcf-shipped" markdown>

```yaml title="config.yml"
--8<-- "src/main/resources/config.yml"
```

</div>
