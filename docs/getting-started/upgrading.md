# Upgrading

1. Stop the server with `stop`.
2. Back up `plugins/HCFCore/` — your configuration, and `data.db` on SQLite — and, on MySQL, the database:

    ```bash
    mysqldump hcfcore > hcfcore.sql
    ```

3. Replace the jar and start the server.

Read the [Changelog](../changelog.md) first: every release says what changed, and what to do when a setting changes. **Your configuration files are never rewritten by an upgrade** — a new default (a Bard's new item, a new command) reaches an existing file only if you add it; the changelog says when. Until `1.0.0`, a minor version (`0.x.0`) may rename a setting.

The **one exception**: staff typing the `/events` setup commands — `create`, `claim`, `setzone`, `delzone`, `setblock`, `delete` — and `/schedule add|remove` write into `events.yml` themselves, on purpose, and only the one section of the one event they name. Your comments are kept, but the file is saved the way the server writes YAML: `"double"` quotes may become `'single'`, a list like `[300, 120]` is written one item per line, and a comment ending a block may move to the start of its line. If `events.yml` does not parse, these commands change nothing and say so. See [Capture events](../gameplay/events.md#setting-up-an-event-in-game).

## Database changes apply by themselves

Schema changes run at startup, module by module, and each one is logged. They only go forward: **keep the backup** if you might have to go back to the previous jar.

## Your files are never overwritten

A configuration file you already have stays as you left it; a setting it lacks takes its built-in default. To see what a new version added, compare your files with the ones in the new jar — or in [`src/main/resources/`](https://github.com/Lawkeys-dev/hcf-core/tree/main/src/main/resources) at that version of the repository. The exception is `events.yml`'s `/events create|setzone|setcore|delete`, which staff ask for explicitly — see above.

A message missing from your edited `lang/en.yml` falls back to the English bundled in the jar. A custom language (`lang/<code>.yml`) has no such fallback: add the new keys to it.

!!! tip "Read the console after the first start"
    A value the new version cannot use is reported and replaced by its default, and `ConfigTypeCheck` reports every setting whose type does not match the shipped file. A clean console means your files are understood as written.

## Following Paper

HCFCore follows the latest stable Paper release. A new Paper version may need a new HCFCore: check the [releases](https://github.com/Lawkeys-dev/hcf-core/releases) before upgrading Paper itself.
