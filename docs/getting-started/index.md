# Getting started

From an empty Paper server to a map ready to open, in four steps.

<div class="grid cards" markdown>

-   :material-download:{ .lg .middle } __1. Installation__

    ---

    Requirements, getting the jar, the first start, and choosing between SQLite and MySQL.

    [:octicons-arrow-right-24: Installation](installation.md)

-   :material-map:{ .lg .middle } __2. Setting up a map__

    ---

    Ranks, spawn, the warzone, roads, events, Mountains, kits — in the order that works — and a checklist before opening.

    [:octicons-arrow-right-24: Setting up a map](setup-guide.md)

-   :material-swap-horizontal:{ .lg .middle } __3. Game modes__

    ---

    HCF or Kitmap: what changes between the two, and how to set a kitmap server up.

    [:octicons-arrow-right-24: Game modes](game-modes.md)

-   :material-update:{ .lg .middle } __4. Upgrading__

    ---

    Replacing the jar safely, what migrates by itself, and what to compare after an update.

    [:octicons-arrow-right-24: Upgrading](upgrading.md)

</div>

## At a glance

| | |
|---|---|
| **Server** | Paper 26.2 or later. Spigot and Folia are not supported. |
| **Java** | 25, which Paper 26.1 and later require |
| **Storage** | SQLite out of the box; MySQL for production |
| **Optional plugins** | Vault, LuckPerms, Apollo-Bukkit (Lunar Client) |
| **Configuration** | `plugins/HCFCore/` — `config.yml` plus one file per module, all commented |
| **Apply changes** | `/hcf reload`, except `kitmap-mode` and `storage`, which need a restart |

!!! tip "New to HCF?"
    [How the game works](../gameplay/index.md) explains the genre — teams, claims, DTR, raids, deathbans — and every rule the plugin applies, with the values it ships with.
