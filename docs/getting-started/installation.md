# Installation

## Requirements

- **Paper 26.2** — tested on build 123. The project follows the latest stable Paper release, so a newer Paper may need a newer HCFCore. Spigot and Folia are not supported.
- **Java 25**, which Paper 26.1 and later require.
- Optional, each a soft dependency the plugin works fully without: **Vault**, **LuckPerms**, and **Apollo-Bukkit** for the Lunar Client features. The HCF tab list needs nothing. See [Integrations](../server/integrations.md).

One installation drives one server. There is no synchronisation between servers behind a proxy: an HCF server and a kitmap server are two installations, each with its own data.

## Getting the jar

=== "Download a release"

    Every version is on the [releases page](https://github.com/Lawkeys-dev/hcf-core/releases), with its jar — `hcf-core-<version>.jar` — and what changed ([Changelog](../changelog.md)). HCFCore is in **pre-release** (`0.x`) until `1.0.0`: GitHub marks those versions *Pre-release*, and the newest one is the one to take.

=== "Build it yourself"

    You need git and a JDK 17 or later to run Gradle. The Gradle wrapper downloads Gradle itself, and Gradle downloads the Java 25 toolchain it compiles with.

    ```bash
    git clone https://github.com/Lawkeys-dev/hcf-core.git
    cd hcf-core
    ./gradlew build
    ```

    The plugin is `build/libs/hcf-core-<version>.jar`. `build` also runs the unit tests; `./gradlew shadowJar` produces the same jar without them. More in [Building from source](../developers/building.md).

## First start

1. Stop the server, put the jar in `plugins/`, add whichever of Vault, LuckPerms and Apollo-Bukkit you want, and start it.
2. The console says `Starting in game mode: HCF` (or `KITMAP`), then each module reports what it loaded (`Loaded 12 balances.`, `Loaded 2 capture event(s).`...).
3. `plugins/HCFCore/` now holds `config.yml`, one file per module (see [Configuration](../reference/configuration/index.md)), `lang/en.yml` and, with the default storage, the SQLite database `data.db`.

The plugin runs out of the box, but a map needs setting up before players arrive — spawn, the warzone, events. See [Setting up a map](setup-guide.md).

### Nobody plays on a half-loaded server

Everything the plugin stores — teams, claims, DTR, balances, deathbans and the rest — is loaded in the background at startup. Until every load has finished:

- connections are refused with *"The server is still starting up. Try again in a few seconds."*;
- the commands that touch that data (`/team`, `/pay`, `/balance`, `/eco`, `/pvp`...) answer *"HCFCore is still loading its data."*

It usually lasts a second or two.

!!! danger "If a load fails, the server stays closed"
    If any load fails, connections are refused with *"The server could not load its data"* until the server is restarted, and the console names the load that failed. A player who got in could only play on data that is not saved anywhere, and whatever they did would be lost. The database connection itself counts as a load: if MySQL is unreachable at startup, nobody gets in. This applies to operators too; the console always works.

## Storage

=== "SQLite (default)"

    `storage.type: sqlite` keeps everything in `plugins/HCFCore/data.db`, through a single connection. It needs no setup and is meant for development, testing and solo servers. **Use MySQL in production.**

=== "MySQL"

    Tested on MySQL 8.4. Create a database and a user first — the plugin creates its tables, not the database:

    ```sql
    CREATE DATABASE hcfcore;
    CREATE USER 'hcfcore'@'localhost' IDENTIFIED BY 'a-long-random-password';
    GRANT ALL PRIVILEGES ON hcfcore.* TO 'hcfcore'@'localhost';
    ```

    Replace `localhost` with the host the Minecraft server connects from if the database runs elsewhere. The plugin needs full rights on its database: its migrations create, alter and drop tables. Every table it owns is prefixed `hcf_`.

    Then, in `config.yml`:

    ```yaml
    storage:
      type: mysql
      mysql:
        host: localhost
        port: 3306
        database: hcfcore
        username: hcfcore
        password: ""        # leave empty, see below
        properties:
          sslMode: PREFERRED
    ```

### Keep the password out of the file

Set the `HCFCORE_MYSQL_PASSWORD` environment variable for the server process instead. When it is set and not blank, it wins over `storage.mysql.password`, and the console says which of the two was used — never the password itself.

=== "Start script"

    ```bash
    export HCFCORE_MYSQL_PASSWORD='a-long-random-password'
    java -jar paper.jar
    ```

=== "systemd"

    ```ini
    [Service]
    Environment=HCFCORE_MYSQL_PASSWORD=a-long-random-password
    # or keep it in a file only root can read:
    # EnvironmentFile=/etc/hcfcore.env
    ```

### Connection properties

`storage.mysql.properties` is handed to MySQL Connector/J as it is. The default `sslMode: PREFERRED` encrypts whenever the server offers TLS, which MySQL 8 does out of the box, and it is also what lets MySQL 8's default login work. A server without TLS needs `sslMode: DISABLED` **and** `allowPublicKeyRetrieval: true`; with only the first, every connection fails with *"Public Key Retrieval is not allowed"*.

### Switching from one to the other

Changing `storage.type` starts from an empty database: nothing copies data from SQLite to MySQL or back. **Choose before the map opens.**

### When data is written

The plugin keeps its data in memory and writes it to the database in the background, so the game never waits on a query. Changes are written every `storage.save-interval-seconds` (300 by default) and in full on a clean shutdown.

!!! warning "Stop the server cleanly"
    **A crash loses what changed since the last save**, so stop the server with `stop` rather than killing the process. A few things are written at once because losing them would lose items or undo a sanction: the inventory held while a player is in staff mode, the items put aside while a player is the King in Kill the King, and freeze bans.

## What needs a restart

`/hcf reload` re-reads every configuration and language file and applies it to the running server. Two things in `config.yml` are read only at startup and need a restart:

- **`kitmap-mode`**, which decides which modules exist at all ([Game modes](game-modes.md));
- **the whole `storage` section**, including the save interval.

## Next

[:octicons-arrow-right-24: Setting up a map](setup-guide.md)
