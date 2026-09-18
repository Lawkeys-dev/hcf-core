# config.yml

Server-wide settings: the language, the game mode and the storage. Every gameplay setting lives in the module files instead.

Every example on this page is **taken from the shipped `config.yml`**, the file the plugin writes on its first start.

!!! warning "Read at startup only"
    `kitmap-mode` and the whole `storage` section need a **restart**. `language` applies with `/hcf reload`.

## Language

```yaml title="config.yml"
--8<-- "src/main/resources/config.yml:language"
```

| Key | As shipped | What it does |
|---|---|---|
| `language` | `en` | Which `lang/<language>.yml` the messages come from. See [Messages and translation](../messages.md) |

## Game mode

```yaml title="config.yml"
--8<-- "src/main/resources/config.yml:kitmap-mode"
```

| Key | As shipped | What it does |
|---|---|---|
| `kitmap-mode` | `false` | `false` is HCF, `true` is Kitmap — the lives module does not start. See [Game modes](../../getting-started/game-modes.md) |

## Storage

```yaml title="config.yml"
--8<-- "src/main/resources/config.yml:storage"
```

| Key | As shipped | What it does |
|---|---|---|
| `storage.type` | `sqlite` | `sqlite` (a file, for development and solo servers) or `mysql` (production) |
| `storage.save-interval-seconds` | `300` | How often changes are written to the database. A full save also runs on a clean shutdown. `0` leaves only that one |
| `storage.mysql.host`, `port`, `database`, `username` | `localhost`, `3306`, `hcfcore`, `root` | Where MySQL is, and who connects |
| `storage.mysql.password` | empty | Better left empty: the `HCFCORE_MYSQL_PASSWORD` environment variable wins over it |
| `storage.mysql.properties` | `sslMode: PREFERRED` | Connector/J properties, passed as they are |

Switching `type` starts from an empty database. See [Installation](../../getting-started/installation.md#storage).

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/config.yml).

<div class="hcf-shipped" markdown>

```yaml title="config.yml"
--8<-- "src/main/resources/config.yml"
```

</div>
