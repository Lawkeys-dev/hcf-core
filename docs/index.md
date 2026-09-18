---
title: HCFCore
hide:
  - navigation
  - toc
---

<div class="hcf-hero" markdown>

# HCFCore

<p class="hcf-tagline">An open source, fully configurable <strong>Hardcore Factions</strong> core for Paper. One plugin drives both <strong>HCF</strong> and <strong>Kitmap</strong>, and every rule, number and message is yours to change without touching the code.</p>

[Get started :material-rocket-launch:](getting-started/index.md){ .md-button .md-button--primary }
[How the game works :material-sword-cross:](gameplay/index.md){ .md-button }
[GitHub :fontawesome-brands-github:](https://github.com/Lawkeys-dev/hcf-core){ .md-button }

</div>

## Everything an HCF server needs, in one plugin

<div class="grid cards" markdown>

-   :material-account-group:{ .lg .middle } __Teams and territory__

    ---

    Factions with roles, invitations, alliances, a bank, points, focus and rally. Chunk claims with buffers and connected land, HQ and base, server land for spawn and roads, a warzone, and a claim lock for SOTW.

    [:octicons-arrow-right-24: Teams](gameplay/teams.md) · [Territory](gameplay/territory.md)

-   :material-shield-sword:{ .lg .middle } __DTR and raids__

    ---

    Deaths Till Raidable on the classic scale, computed from time so it never drifts. A raid opens a team's land to pillage; its protection comes back on its own the moment DTR regenerates. Land never changes hands.

    [:octicons-arrow-right-24: DTR and raids](gameplay/dtr-and-raids.md)

-   :material-sword:{ .lg .middle } __Combat__

    ---

    Deathbans with rank tiers, combat tag and combat logging, safe zones, friendly fire rules, loot protection, the strength nerf — and a **classic 1.7.10 combat** mode: no attack cooldown, sword blocking, 1.7 knockback.

    [:octicons-arrow-right-24: Combat](gameplay/combat.md) · [Classic combat](gameplay/classic-combat.md)

-   :material-shield-star:{ .lg .middle } __Classes__

    ---

    Diamond, Bard, Archer, Rogue and Miner, chosen by the armour you wear: Bard buffs and energy, the archer tag, the Rogue's backstab. Create your own from scratch in one file.

    [:octicons-arrow-right-24: Classes](gameplay/classes.md)

-   :material-flag-variant:{ .lg .middle } __Events__

    ---

    KOTH, Citadel, Conquest and Kill the King, on daily schedules, with holograms above every zone. Mountains that refill on a clock. SOTW, EOTW and the Purge to open and close the map.

    [:octicons-arrow-right-24: Capture events](gameplay/events.md) · [Map phases](gameplay/map-phases.md)

-   :material-treasure-chest:{ .lg .middle } __Kits and items__

    ---

    Kits built from an inventory, refill signs, a layout editor, and 43 partner items built in — Switcher, Ninja, Pocket Bard, Grappling Hook… Custom enchants, effect commands such as `/speed`, enchantment, potion and effect caps, block limits per claim, and a crowbar for End portal frames.

    [:octicons-arrow-right-24: Kits](gameplay/kits.md) · [Items](gameplay/items.md)

-   :material-shield-account:{ .lg .middle } __Moderation__

    ---

    Staff mode with a configurable toolbar, vanish, freeze, invsee, last inventories, a ticket queue for reports and requests, staff chat, and strikes against teams.

    [:octicons-arrow-right-24: Moderation](server/moderation.md)

-   :material-monitor-dashboard:{ .lg .middle } __Interface__

    ---

    A flicker-free scoreboard built from template lines, a tab list, a chat format with LuckPerms prefixes and kill counts, statistics and leaderboards, holograms, and per-player settings.

    [:octicons-arrow-right-24: Chat, scoreboard and settings](gameplay/interface.md)

-   :material-puzzle:{ .lg .middle } __Integrations__

    ---

    Vault for shops and ranks, LuckPerms for chat prefixes, and Lunar Client through Apollo: waypoints, team view, cooldowns and nametags. All optional.

    [:octicons-arrow-right-24: Integrations](server/integrations.md)

</div>

## Built to be trusted with a live server

<div class="grid" markdown>

!!! success "Nothing hardcoded"
    Every gameplay value — deathban length, DTR scale, claim limits, capture times, rewards — is a setting, and `/hcf reload` applies it without a restart. Balance decisions (points scale, enchantment caps, killstreak rewards) ship **empty or neutral**: the plugin never decides your server's balance for you.

!!! success "Never waits on the database"
    All data lives in memory and is written to SQLite or MySQL in the background. The main thread never waits on a query, and nobody gets in before every load has finished — a half-loaded server stays closed.

!!! success "Tested rules"
    Every rule engine is plain Java, covered by more than 1,000 unit tests — including real round trips on SQLite and checks against MySQL's reserved words — and tried in game on Paper 26.2 with SQLite, MySQL 8.4, LuckPerms, Vault and Lunar Client.

!!! success "Open and extensible"
    MIT licensed, with public Bukkit events for other plugins to react to teams, alliances and raids. No required dependency: Vault, LuckPerms and Apollo are used only when present.

</div>

## Quick start

=== "1. Install"

    Put the jar in `plugins/` of a **Paper 26.2** server running **Java 25**, and start it. It runs out of the box on SQLite.

    [:octicons-arrow-right-24: Installation](getting-started/installation.md)

=== "2. Set up the map"

    Claim spawn as a safe zone, set the warzone, move the example events onto your map, create your kits.

    ```text
    /team createsystem Spawn safe
    /team forceclaim Spawn 3
    ```

    [:octicons-arrow-right-24: Setting up a map](getting-started/setup-guide.md)

=== "3. Open"

    Choose the values the plugin leaves to you, switch to MySQL for production, and start the map with a SOTW.

    ```text
    /sotw start 2h
    ```

    [:octicons-arrow-right-24: Before opening](getting-started/setup-guide.md#12-before-opening)

!!! warning "Pre-release"
    HCFCore compiles against the real Paper 26.2 API, its unit tests pass in CI, and it has been tried in game module by module — but it has not yet run a production map under load. Read the code, and test it on your own setup before deploying it to a server you care about.
