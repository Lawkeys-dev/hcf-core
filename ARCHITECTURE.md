# Architecture

How HCFCore is built: module boundaries, persistence, threading, and the seams through which one module answers another's questions. This is the design document for contributors; the user documentation is in [`docs/`](docs/index.md). Read [`CONTRIBUTING.md`](CONTRIBUTING.md) first for the conventions.

Comments in the source refer to the numbered sections below (for example *"ARCHITECTURE.md section 14"*); the numbering is kept stable for that reason.

## 1. Project layout

The root package is `com.lawkeys.hcfcore`.

```
hcf-core/
├── build.gradle.kts, settings.gradle.kts, gradle/
├── .github/workflows/                  # CI (build, tests, shaded jar) and the documentation site
├── docs/                               # user documentation, published with MkDocs
├── mkdocs.yml
├── src/main/java/com/lawkeys/hcfcore/
│   ├── HCFCore.java                    # main class (JavaPlugin): module startup order, reload
│   ├── api/event/                      # public Bukkit events (Team*Event) - the contract with other plugins
│   ├── command/                        # /hcf (reload, version); VisiblePlayers, KnownPlayers
│   ├── config/                         # ConfigManager (one file per module), ConfigTypeCheck
│   ├── database/                       # DatabaseManager (HikariCP, MySQL/SQLite)
│   │   ├── dao/                        # one Jdbc*Store per module
│   │   └── migration/                  # SchemaMigrator, Migration (versions per module)
│   ├── lang/                           # LangManager (lang/en.yml, falls back on the jar's copy)
│   ├── mode/                           # GameMode: HCF or KITMAP
│   ├── startup/                        # StartupBarrier, StartupGate: nobody gets in before every load is done
│   ├── warmup/                         # shared countdowns (/spawn, /logout, /team hq|stuck)
│   ├── team/                           # teams, roles, alliances, focus, rally, bank, points
│   ├── claim/                          # territory, protection, HQ/base, server land, warzone, lockclaim
│   ├── dtr/                            # DTR, regeneration, raid announcements
│   ├── pvp/                            # deathban, combat tag, safe zones, strength nerf, knockback, attack speed, loot, friendly fire; legacy/ (classic 1.7.10 combat)
│   ├── pvpclass/                       # classes: Diamond, Bard, Archer, Rogue, Miner and those classes.yml defines
│   ├── effectcommand/                  # /speed and the like: an effect until death (effect-commands.yml)
│   ├── economy/                        # balances, /pay, /eco, /team deposit|withdraw
│   ├── events/                         # family A: KOTH/Citadel; conquest/; king/ (Kill the King)
│   ├── resourcenode/                   # family B: Mountains
│   ├── phase/                          # SOTW, EOTW, the Purge
│   ├── stats/                          # kills, deaths, killstreaks, playtime, leaderboards
│   ├── chat/                           # public chat format, team/ally routing, local chat
│   ├── ui/                             # scoreboard, tab list
│   ├── staff/                          # staff mode, vanish, freeze, invsee, lastinv; ticket/; strike/
│   ├── kit/                            # kits, layouts, refill signs, abilities
│   ├── killstreak/                     # rewards, plugged into stats/
│   ├── enchant/                        # custom enchants (effect, Hellforged, Implanted, Recover, Autosmelt), books
│   ├── limiter/                        # enchantment and potion caps, blocks per claim
│   ├── general/                        # utility commands
│   ├── schedule/                       # tips, scheduled announcements, custom timers, key-all
│   ├── settings/                       # player settings (/settings, /cobble)
│   ├── lives/                          # lives (absent in kitmap)
│   ├── redeem/                         # codes
│   ├── crowbar/                        # End portal frames
│   ├── hologram/                       # unsaved TextDisplays, stored in the database; leaderboard lines
│   ├── integration/                    # vault/ (Economy), luckperms/ (chat prefix), lunar/ (Apollo)
│   └── util/                           # Cuboid, ChunkPosition, WorldPosition, Durations, ColorCodes, TextWrap...
├── src/main/resources/
│   ├── plugin.yml
│   ├── config.yml + 24 module files (teams.yml, claims.yml, dtr.yml...)
│   └── lang/en.yml
└── src/test/java/...                   # unit tests (pure logic, SQLite round trips)
```

## 2. Guiding principle: maximum configurability, zero code changes

**The plugin must suit any HCF server without its operator touching the code.** This is not one constraint among others; it is the central goal of the project, and it wins over implementation convenience every time a trade-off comes up.

In practice:

- **Every number, delay, threshold, message, and on/off behaviour is configuration**, never hardcoded — even values that look obvious (the deathban length, chunks per member, kit cooldowns).
- **Every significant feature can be switched off** with a configuration flag, without a crash and without phantom behaviour when it is off (see the game modes, section 8 — the same logic, applied more widely).
- **No gameplay value needs a recompile** to be adjusted by the server operator.
- Before implementing a feature, ask: *"could an HCF server with different rules from mine want different behaviour here?"* If the answer is probably yes, it is a setting, not a constant.
- `/hcf reload` applies every configuration change without a restart (exceptions in section 6).

## 3. Data: in-memory cache, asynchronous persistence

**The runtime source of truth is memory.** Each manager (`TeamManager`, `ClaimManager`, `EconomyManager`...) keeps a `ConcurrentHashMap` loaded at startup or when the player connects.

The usual flow:

1. An action changes the in-memory state at once — the game never waits for the database.
2. The change is marked dirty, or pushed into a write queue.
3. An asynchronous task writes it to MySQL/SQLite, in batches or at once depending on how critical it is (a staff-mode inventory is written immediately; a cosmetic setting is batched).

This keeps the database out of PvP latency, at the price of losing what changed since the last save if the server crashes — mitigated by a periodic full save (every 5 minutes by default) and a full save on a clean shutdown.

**Startup: nothing touches a cache before it is loaded.** Each manager's initial load is asynchronous and starts by clearing the cache, so a change made before it finished would be erased — and since loads are independent, a move between two modules could be erased only halfway. The `startup/` package makes sure it cannot happen: `StartupBarrier` (plain Java) counts the declared loads and opens only once sealed and every load has succeeded; `StartupGate` refuses connections and data commands until then, **and for good if a load fails**, the database itself counting as a load. Every module that loads data must therefore:

1. declare its load with `startup.expect(...)` in its `enable`;
2. signal it (`succeeded()` / `failed()`) from its asynchronous task;
3. guard its commands with `StartupGate#refuseCommand`.

Its player listeners have nothing to do: no player is online before the gate opens. Scheduled tasks that act on loaded data wait for `isReady()`, like the DTR announcement poll.

## 4. Database

- **Connection**: HikariCP, configured in `config.yml` (host, port, user). **The password comes from the `HCFCORE_MYSQL_PASSWORD` environment variable**, which wins over `storage.mysql.password` when set. Connector/J properties go in `storage.mysql.properties`, passed through as they are — `sslMode: PREFERRED` by default, without which MySQL 8 refuses every connection ("Public Key Retrieval is not allowed"); no `autoReconnect`, which the Connector/J documentation advises against. Tested on MySQL 8.4.
- **Development / solo**: SQLite in a local file, selected with `storage.type: sqlite`, with no change to the application code (common DAO interfaces).
- **Schema**: one `hcf_`-prefixed table per need, created by the migrations of the module that owns it — `team` (`hcf_teams`, `hcf_team_members`, `hcf_team_alliances`), `claim` (`hcf_team_claims`, `hcf_team_homes`), `dtr` (`hcf_team_dtr`), `pvp` (`hcf_deathbans`), `economy` (`hcf_balances`), `king` (`hcf_king_stashes`), `phase` (`hcf_map_phase`, `hcf_sotw_pvp_enabled`), `staff` (`hcf_staff_stashes`, `hcf_staff_bans`, `hcf_staff_last_inventories`, `hcf_staff_tickets`, `hcf_team_strikes`), `stats` (`hcf_player_stats`), `kit` (`hcf_kits`, `hcf_kit_cooldowns`, `hcf_kit_layouts`), `limiter` (`hcf_claim_block_counts`), `lives` (`hcf_player_lives`), `settings` (`hcf_player_settings`), `redeem` (`hcf_redeem_codes`, `hcf_redeem_uses`), `hologram` (`hcf_holograms`). There is deliberately no catch-all `players` table and no DTR or balance column on the team row: each module keeps its own columns, so it can evolve without touching the others.
- **Migrations**: in-house, numbered, **per module** — `SchemaMigrator` keeps each module's version in `hcf_schema_version` and only applies forward. Details and portability rules in section 13.

## 5. Scheduler and threading

- **Classic `BukkitScheduler`**; Folia is not targeted (section 12). Database writes go through `runTaskAsynchronously`, and whatever reads the world stays on the main thread: Paper forbids reading it elsewhere, which is why local chat, for example, keeps a copy of player positions maintained by the main thread.
- Every repeating task (the DTR announcement poll, the periodic save, the scoreboard) is scheduled cleanly and cancelled in `onDisable`. DTR regeneration is not a task at all: it is computed on read (see `DtrManager`).

## 6. Configuration

- One global `config.yml` plus one file per module (`teams.yml`, `dtr.yml`, `economy.yml`, `events.yml`, `limiters.yml`...), rather than one monolithic file — easier to maintain and to read.
- Hot reload (`/hcf reload`) without restarting the server, with validation: an invalid value is logged clearly and falls back to its default, never a crash. `ConfigTypeCheck` compares each file with the copy embedded in the jar and reports every setting of the wrong type.
- **Two exceptions, read at startup only**: `kitmap-mode` (it decides which modules exist) and the `storage` section of `config.yml` (the pool is opened once). `config.yml` holds only the language, the mode and the storage: each module has its own `enabled` switch in its own file.

## 7. Public API: custom events

Custom Bukkit events let other plugins extend the game without touching the core — a separate cosmetics plugin, a Discord bridge, a statistics site.

**Shipped** (`api/event/`, all fired on the main thread, all subclasses of `TeamEvent`): `TeamCreateEvent`, `TeamDisbandEvent` and `TeamRenameEvent` (cancellable, fired before), `TeamMemberJoinEvent`, `TeamMemberLeaveEvent`, `TeamRoleChangeEvent`, `TeamAllianceChangeEvent` and `TeamRaidableEvent` (fired after). Documentation for third-party developers: [`docs/developers/api.md`](docs/developers/api.md).

## 8. Game modes (HCF / Kitmap)

**The same core drives two game modes**, deployed on two separate servers but built from the same code:

- **HCF** (default): every mechanic of the plugin is active (lives, DTR, claims...).
- **Kitmap** (`kitmap-mode: true` in `config.yml`): some mechanics are not started — the **lives** system at least.

**Technically**, every module that depends on the mode checks it at startup (`onEnable`) and **does not initialise at all** when disabled — not just hiding its commands — so there is no memory or CPU cost and no phantom behaviour. The decision is made in one place rather than scattered as `if (kitmapMode)` through the modules.

**As built**: `mode/GameMode` (`HCF` / `KITMAP`), resolved once at startup — `/hcf reload` does not re-read it, since modules that never started cannot appear mid-session. Today **only `lives/` depends on the mode**: it is not built in kitmap, and `/lives` and `/revive` answer that they are unavailable. The rest of kitmap is configuration (deathbans off in `pvp.yml`, `clear-before-giving` and refill signs in `kits.yml`...).

## 9. Events: two families

The faction events split into **two families**, not one.

### Family A — capture / objective events (KOTH, Citadel, Conquest, Kill the King)

What they share: a target, a win condition, a timer, announcements, a reward for the winner.

- KOTH and Citadel are the same engine (`EventManager`, `CaptureEventDefinition`): hold a zone alone for a time. A Citadel is a KOTH with a much longer `capture-seconds`, plus a `CitadelDefinition` in its own `citadels:` section: the server team whose claim is the Citadel, and the `CitadelRules` that land refuses at all times (`events/listener/CitadelListener`). Its zone to hold joins the KOTHs in `EventSettings#definitions`, so the capture engine, the agenda, the holograms and the waypoints need nothing new.
- **Conquest** (`events/conquest/`) is a second engine: several zones captured in parallel for points, first team to the target wins.
- **Kill the King** (`events/king/`) is a third engine. It has no zone to hold, no holding team and no capture countdown: a player, their death or survival, and a border that punishes rather than counts. Forcing it into `CaptureEventDefinition` would drag a holder and a countdown through code that can have neither — and a common interface would bring nothing either, since the engines have **no method** in common.

What the family does share is only what is really common:

- **the daily schedule** — `events/DailySchedule`, with its two subtleties (a window crossing midnight, a first tick that fires nothing);
- **`/events`** — the list, `start` and `stop`: starting a Kill the King is the same act as starting a KOTH;
- **`events.yml`** — with ids shared across the three, so unique among them.

Same architecture as everywhere else: `KingEventManager` is plain Java, driven by what the server layer tells it (who can be drawn, is the King in the zone, who killed them) and answering with `KingUpdate`s. A complete reign — drawn, out of the zone, withering, killed by a teammate — plays out in a unit test.

**An event that keeps data.** A King's items are put aside during their reign and must survive a crash: `hcf_king_stashes`, loaded at startup behind the `startup/` barrier like any other data.

### Family B — resource events (Mountains)

**A different nature**: no winner, no win condition, no reward for a player or team — a **periodic block refill** in a protected region (no building, no claiming), announced to the server to draw teams into a fight.

- It does **not** inherit anything from family A: there is nothing to capture and nobody to declare the winner. Forcing a Mountain into `CaptureEventDefinition` would drag a holding team, a capture countdown and a reward through code that can have none of the three.
- Its own module, `resourcenode/`, with its own cycle: refill timer, region and palette, protection, announcements.

### What the two families share

**No abstraction — only two value types**: the box `util/Cuboid` and a clock. `resourcenode/` imports nothing from `events/` but the agenda seam below, and `events/` imports nothing from `resourcenode/`. Two consequences, which will come up again with every new family:

- **Geometry belongs to `util/`, not to a module.** A module that exports its own geometry forces the next one to depend on it for a box, or to copy it along with its sign errors on negative coordinates.
- **One agenda, two engines.** Players get one command listing what is coming. It lives in `events/`, and family B *contributes* its lines (`AgendaContributor`) rather than `events/` learning what a refill is. Staff verbs stay on their own command: `/events start` and `/resourcenode refill` are not the same act.

Both families are loaded from configuration, not from a hardcoded list: a new variant that fits an existing engine is a block of YAML, not a recompile.

## 10. Languages (i18n)

- Every player-facing message goes through `lang/en.yml` (key → text). **No text is hardcoded in Java.**
- `LangManager` resolves a key and its placeholders into the final text, with the language code read from `config.yml` (`language: en`). A `lang/<code>.yml` file dropped in the plugin folder is used as it is; a missing key falls back on the jar's copy of the same file — which exists only for `en`, so a custom translation has to carry every key. An empty message is not sent: that is how an operator silences one.
- Key convention: `<module>.<context>.<message>` (for example `team.create.success`, `dtr.raidable.announce`).

## 11. External dependencies (soft dependencies)

The core stays **self-sufficient by default**: every external dependency is **optional**, never required for basic operation.

- **Apollo (Lunar Client)**: the official open-source library (MIT, github.com/LunarClient/Apollo). A **soft dependency**: the plugin detects whether a player runs Lunar Client before sending any Apollo data, and works normally without it. Modules used: Waypoint, Team, Cooldown and Nametag (`integration/lunar/`, `apollo.yml`). Apollo is not a library the plugin bundles: the API is `provided`, supplied at runtime by the **`Apollo-Bukkit`** plugin, declared as a `softdepend`.
- **No dependency for holograms**: rather than a third-party plugin (DecentHolograms, FancyHolograms...), holograms use Paper's native `TextDisplay` entity, which every client sees and which needs nothing installed.
- **Vault**: the `economy/` module implements Vault's `Economy` interface so shops, paid ranks and other plugins can use it. If Vault is present, the implementation is registered with Bukkit's `ServicesManager` at startup; otherwise the internal economy works on its own. ⚠️ **Build pitfall**: `com.github.MilkBowl:VaultAPI:1.7` brings a transitive `org.bukkit:bukkit:1.13.1-R0.1-SNAPSHOT` that conflicts with `paper-api` during Gradle resolution; it is excluded explicitly (`exclude(group = "org.bukkit", module = "bukkit")`) in `build.gradle.kts`.
- **LuckPerms**: permissions and ranks are **delegated entirely** to LuckPerms (or any Bukkit permission plugin): the core only checks `Permissible#hasPermission`. **The one exception is the chat format**, which reads the player's prefix and suffix from the LuckPerms API when a message is sent — a soft dependency too, with an empty prefix and suffix when LuckPerms is absent.
- Any future dependency follows the same rule: check that it is really needed (is Paper's own API enough?), and add it as a soft dependency only if it brings something that cannot be replaced.

## 12. Settled architecture decisions

- **Single server.** One HCF server runs on one Paper instance. A kitmap server is a separate installation of the **same core** in another mode (section 8). No live synchronisation between the two and no Velocity/BungeeCord to handle. **Consequence:** no distributed cache (Redis) — the local in-memory cache of section 3 is the definitive answer.
- **Folia: not targeted.** An HCF map is **bounded (about 3000 × 3000 blocks) with pre-generated chunks**, which removes most of the benefit of Folia's multi-region parallelism (designed for large, unpredictable worlds). The classic `BukkitScheduler` is used throughout — simpler to maintain and sufficient here. Revisit if the context changes.

## 13. Inside a module: pure core + server adapter

Every module is split into two layers.

**1. The core — plain Java, no `import org.bukkit`.**
The module's manager, its data model, its JDBC DAO and its migrations have no dependency on the server API. The manager receives:

- a `Supplier<XSettings>` rather than a fixed configuration object, so `/hcf reload` swaps the snapshot and every later decision uses the new values without a restart;
- a storage interface (`TeamStore`) rather than a `DataSource`;
- an event interface (`TeamEventDispatcher`) rather than Bukkit's `PluginManager`;
- an injectable clock (`LongSupplier`), so expiry logic is tested without waiting.

The manager never talks to a player: it returns a `TeamResult` carrying a **language key and placeholders**, which the command layer turns into a message.

**2. The server adapter — the only layer that sees Bukkit.**
`TeamModule` (lifecycle, scheduler, command registration), `TeamSettingsLoader` (YAML → immutable settings), `BukkitTeamEventDispatcher` (custom events), `team/command/` (argument parsing and rendering).

**What this split buys:**

- The rule "unit tests on the pure logic" (CONTRIBUTING.md section 3) is achievable without a server or a database: the whole rule set of a module runs in unit tests in well under a second, including real round trips on SQLite.
- A major Paper API change only touches the adapter — the game mechanics do not move.
- The rule "no game logic in Command or Listener classes" is no longer a matter of discipline: it is **structurally impossible to break**, since the command layer has no access to the internal state.

**Model encapsulation**: `Team`'s mutators are *package-private*. The DAO, in another package, cannot change a team directly — it exchanges `TeamSnapshot`s (immutable records) through `Team#toSnapshot` / `Team#fromSnapshot`. That is what guarantees every change goes through the manager, and so through the rules.

**Rules of the adapter layer.** The tests do not see this layer; these rules come from what it hid:

- **A subclass that declares its own `HandlerList` is not handed to listeners of its parent class.** `PlayerPortalEvent` escapes a `PlayerTeleportEvent` listener, `PlayerArmorStandManipulateEvent` a `PlayerInteractEntityEvent` listener. Check the sources before relying on inheritance.
- **Refuse what must be refused, no more.** `PlayerInteractEvent#setCancelled(true)` refuses the block **and** the item in hand (its javadoc: "prevent use of food"); territory protection refuses the block alone (`setUseInteractedBlock(DENY)`), and a player still eats in a claim.
- **A handler that draws a consequence from an event ignores it when cancelled** (`ignoreCancelled = true`). `EntityDeathEvent` is cancellable in 26.2 — the player is revived — and a cancelled death must not ban, cost DTR or count.
- **Two listeners competing for an event are ordered by priority, and the order is written down**: the staff channel (`LOW`) comes before team chat routing (`NORMAL`).
- **A refusal message on a repeated action** — a held click, a held key, a pushed border — goes through `util/RefusalThrottle`.
- **A player command that finds someone by name goes through `command/VisiblePlayers`**: vanished staff are neither found nor completed.
- **An offline name is resolved with `Bukkit#getOfflinePlayerIfCached`**, never with `getOfflinePlayer(String)` (a blocking web request) nor by walking `getOfflinePlayers()` (which lists the `playerdata` folder on every call).
- **Text typed by a player and inserted into a message for another is escaped** (`ColorCodes.escape`) unless the player holds the colour permission: templates are coloured after substitution.
- **What changes a block with no player behind it** — piston, liquid, fire, dispenser, tree, sponge — **is judged by `ClaimManager#mayReach`**: as if the owner of the land it starts from were acting. A new mechanism of this kind goes through there, not through a rule written in its own listener.
- **What a player cannot hit, they cannot affect either**: every new way of reaching a player (effect, pull, push) goes through `CombatListener#judge`, which holds the safe zone, SOTW and teammate/ally rules in one place.

**Per-module migrations**: `SchemaMigrator` keeps one version **per module** in `hcf_schema_version` (column `module`), not a global one. Each module brings its numbered migrations (`TeamSchema.migrations()`) and applies them from its own asynchronous startup task. Two practical consequences: modules evolve independently, and SQL statements must stay portable between MySQL and SQLite (no `CREATE INDEX IF NOT EXISTS`, which MySQL lacks — declare `UNIQUE` constraints inline in `CREATE TABLE`). **No MySQL reserved word as a table, column or index name** (SQLite accepts them, MySQL refuses the migration): `MysqlReservedWordsTest` checks every migration against MySQL 8.4's list; a name in backticks, which both databases read, passes. **A statement that cannot be replayed (`ALTER TABLE … ADD COLUMN`, `CREATE INDEX`) is alone in its migration**: MySQL commits each DDL statement as it goes, so rolling back a migration that fails half-way does not undo what came before, and the whole migration is replayed at the next start. `MigrationRetryTest` checks it; only `team` v2, published before the rule, is an exception.

## 14. Coupling between modules: seams, not direct calls

`claim/` needs an answer only `dtr/` holds — "is this team raidable?" — and `dtr/` starts after it. Three ways to handle that; only one is acceptable here:

1. ❌ Wait for `dtr/` before writing `claim/`: both modules are big, they cannot ship as one block.
2. ❌ Put a `raidable` field on the claim, kept up by `dtr/`: that is exactly the phantom behaviour section 2 forbids, and it duplicates a truth that lives elsewhere.
3. ✅ **Declare an interface in the consuming module** (`RaidabilityPolicy`, a single method), with an explicit, conservative default implementation (`NEVER`), which the producing module replaces at its startup.

The same principle serves in `team/` (`TeamStore` for persistence, `TeamEventDispatcher` for the event bus). Three properties make it better than a direct call:

- **Development order is free.** `claim/` is shippable, testable and useful without `dtr/`.
- **The default is a visible choice.** `RaidabilityPolicy.NEVER` documents and tests the behaviour without `dtr/` — instead of a `null` that would break at the first broken block.
- **The dependency never points back.** `dtr/` knows `claim/`; `claim/` never knows `dtr/`.

**Special case — an event rather than an interface.** When the consuming module only wants to *react* (release a disbanded team's claims), the public Bukkit event is enough and needs no seam: `TeamDisbandClaimListener` listens to `TeamDisbandEvent` at `MONITOR`/`ignoreCancelled`, the one point where the disband is certain. An interface to *ask*, an event to *react*.

**Extending commands.** `claim/` adds `/team claim`, `/team map`, `/team hq`... without `team/` knowing about territory: `TeamCommand` keeps a copy-on-write list and `TeamModule#registerSubCommand` is the entry point. Any module can extend an existing command without changing it.

**The seams, and who fills them:**

| Seam | Declared in | Default when nobody answers | Filled by |
|---|---|---|---|
| `TeamStore` | `team/` | `NO_OP` (everything in memory) | `database/dao/JdbcTeamStore` |
| `TeamEventDispatcher` | `team/` | `NO_OP` (no Bukkit event) | `TeamModule` |
| `RaidabilityPolicy` | `claim/` | `NEVER` (claims always protected) | `dtr/`, **wrapped** by `phase/` (EOTW, Purge) |
| `TeleportGuard` | `claim/` | `ALLOW` (no teleport blocked) | `pvp/` |
| `ReservedRegionPolicy` | `claim/` | `NONE` (no reserved chunk) | `resourcenode/` |
| `ClaimingPolicy` | `claim/` | `OPEN` (player claims open) | `phase/` (EOTW) |
| `LockWindow` | `claim/` | `CLOSED` (no claim can be locked) | `phase/` (open during SOTW) |
| `AgendaContributor` | `events/` | no line contributed | `resourcenode/`, `phase/` |
| `CombatProtection` | `pvp/` | `NONE` (only safe zones refuse a hit) | `phase/` (SOTW) |
| `DeathbanPolicy` | `pvp/` | `USUAL` (tiers from `pvp.yml`) | `phase/` (SOTW, EOTW) |
| `DeathbanWaiver` | `pvp/` | `NONE` (every banned player is refused at login) | `lives/` (a life spent; never for an EOTW ban) |
| `DeathCostPolicy` | `dtr/` | `ALWAYS` (every death costs DTR) | `phase/` (SOTW) |
| `BuildOverride` | `claim/` | `NONE` (no bypass) | `staff/` (`/staffbuild`) |
| `KillstreakObserver` | `stats/` | `NONE` (the streak is counted, nobody is told) | `killstreak/` |
| `HologramSource` | `hologram/` | no source (only stored holograms) | `events/` (one hologram per capture zone) |
| `SpawnGuard` | `general/` | `ALLOW` (nobody refused) | `events/` (the King of Kill the King never enters spawn, `/spawn` included) |
| `LogoutGuard` | `general/` | `ALLOW` (nobody refused) | `pvp/` (a tagged player cannot leave through `/logout`: that would be a combat log) |
| Partner items | `events/` | none recognised (nothing refused as a partner item in a Citadel) | `kit/` (its abilities) |
| `AllyCombatZone` | `pvp/` | `NOWHERE` (allies hurt each other nowhere) | `events/` (a running KOTH, Citadel or Conquest zone, and the King during Kill the King) |
| `RaidOverride` | `dtr/` | `NONE` (DTR alone decides) | `phase/` (EOTW and the Purge make everything raidable: `/team dtr`, the scoreboard and raid announcements say so) |
| Scoreboard filter | `ui/` | everybody has a board | `settings/` |
| Scoreboard placeholder sources | `ui/` | only `ui/`'s own placeholders | `pvpclass/` (`%class_line%`, `%class_energy_line%`, `%archer_tag_line%`) |
| Tips filter | `schedule/` | everybody receives them | `settings/` |
| `EffectCaps` (`util/`) | `pvpclass/`, `enchant/`, `events/` (the King), `effectcommand/` | `NONE` (every effect as asked) | `limiter/` (`effects.caps`: each module asks before giving an effect, so what it gives is what it recognises as its own) |
| `/togglepm` observer | `general/` | the choice lasts the session | `settings/` (keeps it) |

**Wrapping a seam rather than replacing it.** EOTW makes every team raidable, but outside EOTW the DTR still decides: `phase/` therefore does not replace the `RaidabilityPolicy` installed by `dtr/`, it **wraps** it (`raidable = EOTW running OR Purge running OR what the DTR says`). Hence the startup order: `phase/` after `dtr/`, to wrap the real policy and not the `NEVER` default. A module that needs to *modify* another's answer, without knowing it, does it this way.

The table reads in one direction only: the "declared in" column never depends on the "filled by" column. And every default is a complete behaviour, not a `null` — a server running only `team/` and `claim/` would work, with claims simply always protected.

**A seam can grow without breaking its default.** The warzone needed a second question from `ReservedRegionPolicy` — "does this *block* belong to a region governed elsewhere?", so that a Mountain inside the warzone stays minable. It was added as a `default` method answering "no": `NONE` and every existing implementation stay valid without a line more, and only the module that has something to say overrides it. The resulting decision order in `ClaimManager#checkBuild` — **the most specific wins**: a claimed chunk follows its team's rules; on unclaimed land, a block of a region governed elsewhere is left to that module; the rest of the warzone radius follows the warzone's rule; beyond it is the wilderness. This order only describes what the territory module decides: a node's own listener still applies its own rules inside its region.

**A seam for a question about a person, not about land.** `BuildOverride` is the first seam of `claim/` about a **player** rather than a chunk or a map state. The `hcfcore.claim.bypass` permission answers "may this rank ignore protection"; `BuildOverride` answers "does this staff member want to, right now". The distinction is what makes `/staffbuild` useful: a staff member holding the permission still wants to be stopped when breaking a wall by accident while looking around. Both are needed. That is also why the question is asked in the server layer (`ClaimModule#bypassesProtection`) and not in `ClaimManager`: the manager reasons about territory, not about who holds the pickaxe.

**One judge for every way of reaching a player.** Whether a player may harm another — SOTW, safe zones, friendly fire — is decided in one place, `PvpModule#judgeHarm`. A blow, a harmful potion, a rod's pull, a blast's push and a class's debuff (a Bard's Wither) all ask it, so a new way of reaching a player cannot forget a rule.

**When NOT to use a seam.** `pvp/` asks `claim/` **directly** for safe zones, with no interface in between, although `claim/` declares two seams (`RaidabilityPolicy`, `TeleportGuard`). That is not an inconsistency: a seam exists to **invert a development-order problem**, not to be applied mechanically. When the answering module already exists when the asking one starts, a direct call is simpler, clearer and just as correct. The useful rule: *a seam when the answering module does not exist yet, a direct call otherwise.*
