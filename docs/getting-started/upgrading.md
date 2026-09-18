# Upgrading

1. Stop the server with `stop`.
2. Back up `plugins/HCFCore/` — your configuration, and `data.db` on SQLite — and, on MySQL, the database:

    ```bash
    mysqldump hcfcore > hcfcore.sql
    ```

3. Replace the jar and start the server.

Read the [Changelog](../changelog.md) first: every version says what changed, and what to do when a setting changes. **Your configuration files are never rewritten** — a new default (a Bard's new item, a new command) reaches an existing file only if you add it; the changelog says when. Until `1.0.0`, a minor version (`0.x.0`) may rename a setting.

## Database changes apply by themselves

Schema changes run at startup, module by module, and each one is logged. They only go forward: **keep the backup** if you might have to go back to the previous jar.

## Your files are never overwritten

A configuration file you already have stays as you left it; a setting it lacks takes its built-in default. To see what a new version added, compare your files with the ones in the new jar — or in [`src/main/resources/`](https://github.com/Lawkeys-dev/hcf-core/tree/main/src/main/resources) at that version of the repository.

A message missing from your edited `lang/en.yml` falls back to the English bundled in the jar. A custom language (`lang/<code>.yml`) has no such fallback: add the new keys to it.

!!! tip "Read the console after the first start"
    A value the new version cannot use is reported and replaced by its default, and `ConfigTypeCheck` reports every setting whose type does not match the shipped file. A clean console means your files are understood as written.

## Following Paper

HCFCore follows the latest stable Paper release. A new Paper version may need a new HCFCore: check the [releases](https://github.com/Lawkeys-dev/hcf-core/releases) before upgrading Paper itself.
