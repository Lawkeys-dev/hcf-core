# Building from source

## Requirements

- **git**
- **A JDK 17 or later** to run Gradle. The Gradle wrapper downloads Gradle itself, and Gradle provisions the **Java 25** toolchain the plugin compiles with.

Nothing else: no server, no database and no network service is needed to build or test.

## Build

```bash
git clone https://github.com/Lawkeys-dev/hcf-core.git
cd hcf-core
./gradlew build
```

| Command | Does |
|---|---|
| `./gradlew build` | Compile, run the unit tests, build the shaded jar |
| `./gradlew test` | The tests alone |
| `./gradlew shadowJar` | The jar alone, without the tests |

The plugin is `build/libs/hcf-core-<version>.jar`. It bundles HikariCP (relocated) and the SQLite and MySQL drivers; Paper ships both drivers itself and is asked first, so the bundled copies are only a fallback.

!!! warning "Never relocate `org.sqlite`"
    JNI binds a native method to the full name of its class: a relocated SQLite driver fails to load its native library at runtime.

## Compiler and tests

- The build compiles with **`-Xlint:all`**, and should stay silent: a new warning is a finding, not noise. Warnings are not errors on purpose — a Paper version bump can deprecate an API the plugin uses, and that deserves a visible warning and a considered fix, not a build that refuses to run.
- The tests run on **JUnit 5**. They cover every rule engine — teams, claims, DTR, combat, events, economy... — without a server, and the storage layer with **real round trips on SQLite**, including the schema migrations.
- Some tests guard the project's own rules: `MysqlReservedWordsTest` checks every migration against MySQL 8.4's reserved words, `MigrationRetryTest` that a failed migration can be replayed, and `TeamMessagesTest` that every language key the code uses exists in `lang/en.yml`.

## Continuous integration

GitHub Actions runs the same build on every push to `main` and on every pull request ([`build.yml`](https://github.com/Lawkeys-dev/hcf-core/blob/main/.github/workflows/build.yml)), against the real Paper 26.2 API. A successful run carries the jar as the `hcfcore-jar` artifact for 7 days; a failed one carries the test reports.

The documentation site is built and published by [`docs.yml`](https://github.com/Lawkeys-dev/hcf-core/blob/main/.github/workflows/docs.yml) on every push to `main` that touches it.

## Project layout

```
hcf-core/
├── src/main/java/com/lawkeys/hcfcore/   # one package per module (team, claim, dtr, pvp, events...)
├── src/main/resources/                  # plugin.yml, config.yml, one YAML per module, lang/en.yml
├── src/test/java/                       # unit tests
├── docs/                                # this documentation (MkDocs)
├── ARCHITECTURE.md                      # how the code is built
├── FEATURES.md                          # what each feature does, and why
└── CONTRIBUTING.md                      # conventions and rules
```

## Writing the documentation

The documentation is Markdown in `docs/`, built with [MkDocs Material](https://squidfunk.github.io/mkdocs-material/). To preview it:

```bash
python3 -m venv .venv
.venv/bin/pip install "mkdocs>=1.6,<2" "mkdocs-material>=9.7,<10"
.venv/bin/mkdocs serve
```

then open <http://127.0.0.1:8000>.

**Examples come from the shipped files.** The configuration reference and the guides quote `src/main/resources/*.yml` directly, never a copy, so the documentation cannot drift from what the plugin installs. A whole file is included with `--8<-- "src/main/resources/pvp.yml"`; a part of it through a **named section**, delimited in the YAML by two comments:

```yaml
# --8<-- [start:loot-protection]
loot-protection:
  enabled: true
# --8<-- [end:loot-protection]
```

and quoted in a page with `--8<-- "src/main/resources/pvp.yml:loot-protection"`. The markers are removed from what the page shows, and the indentation of a nested section is taken away. When you edit a configuration file, keep its markers around the blocks they frame; `mkdocs build --strict` fails on a section that no longer exists. Whoever changes a command, a permission, a setting or a placeholder updates `docs/` in the same commit.
