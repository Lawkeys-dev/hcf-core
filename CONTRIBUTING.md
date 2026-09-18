# Contributing to HCFCore

Thanks for taking the time to look at the code. This file sets the technical frame of the project: the stack, the conventions, and the rules every change follows. Read it before opening a pull request, together with [`ARCHITECTURE.md`](ARCHITECTURE.md) (how the code is built) and [`FEATURES.md`](FEATURES.md) (what each feature does, and why).

The user documentation — installation, setting up a map, commands, permissions, configuration — lives in [`docs/`](docs/index.md) and is published at **https://lawkeys-dev.github.io/hcf-core/**.

Comments in the source refer to the numbered sections below (for example *"CONTRIBUTING.md section 5"*); the numbering is kept stable for that reason.

## 1. The project

HCFCore is a **Hardcore Factions core plugin for Paper**, following the **latest stable Minecraft release**. It drives two game modes from the same code — **HCF** and **Kitmap** — and aims to fit any HCF server without its operator touching the code.

The plugin is written from scratch. Commercial HCF cores are a common reference for the *scope* of the genre (what a server expects to find), never a source of code.

## 2. Stack

| Element | Choice | Why |
|---|---|---|
| Minecraft / Paper | **26.2** (latest stable) | A moving target: the project follows the latest stable Paper, not a frozen version |
| Java | **JDK 25** | Required by Paper 26.1 and later |
| Build | **Gradle (Kotlin DSL)**, shaded jar | Recommended by the Paper documentation; handles Java toolchains and versioned dependencies well |
| Database | **MySQL** (production) through **HikariCP**, **SQLite** (development, solo servers) | A standard, fast connection pool; SQLite needs no setup |
| Runtime data | **In-memory cache (`ConcurrentHashMap`) is the source of truth**, persisted **asynchronously** | An HCF server is PvP-critical: no blocking I/O is acceptable on the main thread |
| Scoreboard | **Paper's own scoreboard API** | Flicker and the red numbers are both solved by the documented API, without a shaded packet library |
| Claim protection | **Built in** | Full control over how claims and DTR interact |

**Following Paper releases.** On each new stable Paper version, check (1) the Java version it requires, (2) the breaking changes of the Paper API, (3) the compatibility of the dependencies, before bumping the target. Since year-based versioning (26.x), `api-version` in `plugin.yml` takes the year.drop value directly (`'26.2'`), and the `paper-api` Maven coordinate follows `{VERSION}.build.+` (`26.2.build.+`) rather than the older `{VERSION}-R0.1-SNAPSHOT`. Re-check both on every major bump.

## 3. Code conventions

- **Root package**: `com.lawkeys.hcfcore`.
- **Modular architecture**: one module per system (`team`, `claim`, `dtr`, `economy`, `pvp`, `staff`, `kit`, `events`...). See [`ARCHITECTURE.md`](ARCHITECTURE.md).
- **No game logic in `Command` or `Listener` classes.** They validate input and delegate to the module's manager; the rules live in plain Java (ARCHITECTURE.md section 13).
- **Maximum configurability.** Every behaviour is configurable in YAML — every number, delay, threshold, message and on/off switch — except the plugin's root command (`/hcf`).
- **Async first.** Every database, HTTP or file operation runs off the main thread (`BukkitScheduler#runTaskAsynchronously` or an equivalent).
- **No `Thread.sleep`** in a server context.
- **Tests** cover the pure logic (DTR, cooldowns, economy, every rule engine) without a Bukkit server. JUnit 5, with real round trips on SQLite for the storage layer.
- **Language**: English everywhere — code, comments, configuration, `lang/`, documentation.

## 4. Module order

The order the modules were built in, which is also the order they depend on one another:

1. Plugin skeleton (bootstrap, configuration loader, command system)
2. **Teams / factions** (creation, roles, basic claims)
3. **Territory / claims** and **DTR (Deaths Till Raidable)**
4. **PvP core** (deathban, strength nerf, combat tag)
5. **Economy** (balance, pay)
6. **Faction events** (KOTH, Conquest, SOTW/EOTW)
7. **Kits, killstreaks, custom enchants**
8. **Staff tools** (vanish, freeze, invsee, reports)
9. **UI** (scoreboard, tab list, leaderboards)
10. **Anti-exploit / limiters**

Modules 2, 3 and 4 are tightly coupled: a team has a DTR, a DTR allows a raid, a raid means PvP.

**Startup order.** `HCFCore#onEnable` is the reference: `warmup` and `stats` first, then `team` → `claim` → `dtr` → `pvp` → `economy` → `events` → `resourcenode` → `phase`, the newer modules after them, `settings` last; then the Vault and Lunar integrations, and `startupGate.seal()`. The rule: a module starts **after** the ones whose seams it fills (an interface with a neutral default, declared by the module written first) — full table in ARCHITECTURE.md section 14.

## 5. Non-negotiables

- **Never block the main thread** with a synchronous database query.
- **Never store a password or a database key in a versioned file.** The MySQL password comes from the `HCFCORE_MYSQL_PASSWORD` environment variable, which wins over `config.yml`.
- **No code copied from a commercial resource.** Other plugins are a reference for scope, not for code.

## 6. Official documentation only

**No invented technical solution.** For anything touching:

- the Paper API (methods, events, the scheduler, how a class behaves),
- a third-party library (HikariCP, a JDBC driver, Vault, LuckPerms, Apollo...),
- a version constraint (required Java, breaking changes between Paper versions),

**check the official documentation** before writing the code or settling an architecture decision:

- Paper: https://docs.papermc.io and https://jd.papermc.io (javadocs)
- Paper versions: https://papermc.io/downloads/paper
- Spigot/Bukkit API (the parts still shared): https://hub.spigotmc.org/javadocs/spigot/
- Java: https://docs.oracle.com/en/java/javase/

When something cannot be confirmed there — or in the library's own sources — say so explicitly in the code comment or the commit ("not confirmed in the official documentation") rather than presenting a plausible guess as fact. This rule comes before speed.

## 7. Durable rules

Each of these comes from a real defect. The rules for listeners are detailed in ARCHITECTURE.md section 13, the startup rule in section 3.

**Code**

- A module that loads data calls `startup.expect(...)` in its `enable`, signals from its async task, and guards its commands with `refuseCommand`.
- Never resolve a name with `Bukkit#getOfflinePlayer(String)` (a blocking web request) nor by walking `getOfflinePlayers()` (it reads the `playerdata` folder): use `getOfflinePlayerIfCached`. Every player lookup by name goes through `command/VisiblePlayers`, which respects vanish; a staff command that may also target an offline player goes through `command/KnownPlayers`.
- A manager with a "dirty" flag clears the mark **before** reading the row, and sets it again if the save fails.
- An exception inside a `CompletableFuture` callback is swallowed without a trace: guard every stage.
- A typed duration is bounded by `Durations.MAX_SECONDS` (beyond it, `now + seconds × 1000` overflows into the past); a `*-seconds` key read from configuration goes through `Durations.capSeconds`. Milliseconds left become seconds through `Durations.secondsLeft`; a wait between two uses goes through `util/Cooldowns`.
- Free text a player sends to another (chat, `/msg`, the staff channel, `/broadcast`) goes through `ChatModule.typedText`: `&` codes only with `hcfcore.chat.color`, raw `§` stripped.
- No gendered pronoun for a player — not in the code, the messages or the configuration: "they", or a phrasing that needs none.

**Listeners**

- A subclass that declares its own `HandlerList` is not seen by a listener of its parent class (`PlayerPortalEvent`, `PlayerTeleportEvent` against `PlayerMoveEvent`, `PlayerArmorStandManipulateEvent`, `InventoryDragEvent`...): check the 26.2 sources for every event type listened to.
- `PlayerInteractEvent`: refuse the **block** (`setUseInteractedBlock(DENY)`), not the item in hand. Order: refusals (freeze) at `LOWEST`, handlers that act (signs, abilities) at `LOW`, protection at `NORMAL`. A click in the air arrives already "cancelled": an `ignoreCancelled` handler never sees it.
- `ignoreCancelled` on every handler that draws a consequence (`EntityDeathEvent` is cancellable in 26.2). Repeated refusals go through `util/RefusalThrottle`.

**Data and configuration**

- YAML 1.1: `on`, `off`, `yes` and `no` become booleans — never use them as keys.
- Every language key the code uses exists in `lang/en.yml`. `TeamMessagesTest` checks it, and a new `*Messages` class must be added to its list, which fails otherwise.
- SQL names: `MysqlReservedWordsTest` checks every migration against MySQL 8.4's reserved words. Serialized inventories are `MEDIUMBLOB` (`BLOB` stops at 65,535 bytes in MySQL).
- Migrations: a statement that cannot be replayed (`ALTER TABLE`, `CREATE INDEX`) is alone in its migration, because MySQL commits each DDL statement as it goes (`MigrationRetryTest`). A write over several rows goes through `database/dao/Transactions`.

**Method**

- Where a value is a balance decision for the server, the mechanism is the deliverable: values, scales and lists ship empty or neutral, and nothing is presented as a decision.
- Whoever changes a command, a permission, a setting or a placeholder updates `docs/` in the same commit.
- A test whose name promises more than its assertion is worse than no test: for a key rule, check that removing the rule makes the test fail.

## Reviewing a change

Look first for:

- the Bukkit API touched off the main thread, or I/O on the main thread;
- a write lost between cache and database;
- an item or money duplicated or lost;
- a protection bypassed through another path;
- something not cleaned up on disconnect or in `onDisable`.

Then for inconsistencies between modules:

- the same need solved twice, a helper rewritten instead of using `util/`;
- a javadoc that no longer describes the code;
- a code default that differs from the shipped YAML;
- a rule of this file followed in one module and not in another.

A finding cites `file:line` and a concrete scenario.

## Building

```bash
./gradlew build      # compile, run the unit tests, build the shaded jar
./gradlew test       # the tests alone
```

Gradle needs a JDK 17 or later to run, and provisions the Java 25 toolchain it compiles with. The build compiles with `-Xlint:all`, and should stay silent. **Never relocate `org.sqlite`** in the shaded jar: JNI binds a native method to the full name of its class (Paper 26.2 ships sqlite-jdbc and mysql-connector-j itself).

GitHub Actions runs the same build on every push to `main` and on every pull request.

## Pull requests

- Branch from `main`, one topic per pull request, and target `main`.
- Keep commits focused, with a message that says what changed and why.
- `./gradlew build` passes locally, with no new warning.
- `docs/` is updated when a command, permission, setting or placeholder changes.
- Try in game what the unit tests cannot see — listeners, commands, anything that talks to Bukkit — and say what you tried in the pull request.
