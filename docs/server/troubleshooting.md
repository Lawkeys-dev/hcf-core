# Troubleshooting

The console — and `logs/latest.log` — is the first place to look: the plugin reports every setting it could not use, every load that failed, and why.

## Startup and storage

**Players are refused with "The server is still starting up".**
The plugin is loading its data. It takes a second or two; retry.

**Players are refused with "The server could not load its data", and commands say HCFCore could not load.**
A load failed at startup, and the server stays closed on purpose until it is restarted: anyone who got in would be playing on data saved nowhere. The console names the failed load (`teams`, `balances`, `the database`...) and the error. Fix the cause, then restart — `/hcf reload` does not retry a load.

**MySQL: "Public Key Retrieval is not allowed".**
The connection is unencrypted and MySQL 8's default login needs the server's key. Keep `sslMode: PREFERRED` (the default) under `storage.mysql.properties`, or, for a MySQL server without TLS, set both `sslMode: DISABLED` and `allowPublicKeyRetrieval: true`.

**MySQL: access denied, or the database does not exist.**
The plugin creates its tables, not the database or the user. See [Installation](../getting-started/installation.md#storage). If the password is in `HCFCORE_MYSQL_PASSWORD`, the variable must be set in the environment of the server process itself — the console says whether the password came from the variable or from the file.

**MySQL: startup fails with "Migration team v2 failed" and "Duplicate column name 'system_zone'".**
An earlier start lost its connection half-way through that migration: MySQL kept the new column, but the migration was not recorded as done. Record it by hand, then restart: `UPDATE hcf_teams SET system_zone = 'SAFE' WHERE type = 'SYSTEM' AND system_zone IS NULL;` and `UPDATE hcf_schema_version SET version = 2 WHERE module = 'team';`.

**Changing `kitmap-mode` or `storage` did nothing.**
Those are read only at startup: restart. Everything else applies with `/hcf reload`.

**Everything is gone after switching from SQLite to MySQL.**
Nothing copies data between the two: the new database starts empty. Switch back to find the old data where it was.

## Settings

**A change in a file does nothing.**
Run `/hcf reload` and read the console: a value the plugin cannot use is reported and replaced by its default. A setting in the wrong place — wrong indentation, a misspelled key — is simply not read, so compare with the shipped file.

**Players see a message key such as `team.create.success` instead of a message.**
The key is missing from your custom language file. English keys fall back to the bundled English; a language you added yourself has no fallback, so copy every key from `lang/en.yml`.

**A scoreboard row does not show.**
A row whose placeholders all come out empty is dropped on purpose — `%combat_line%` outside combat, `%territory%` on land nobody owns, `%dtr%` without a team. A scoreboard also stops at 15 rows; the console warns when more than 15 of yours are always shown.

## Territory and combat

**A staff member cannot build in other teams' land, spawn or the warzone.**
Building through territory protection takes both `hcfcore.claim.bypass` and `/staffbuild` turned on — operators included. `/staffbuild` says so when the permission is missing.

**An operator never gets deathbanned.**
Operators hold `hcfcore.deathban.bypass`.

**A staff member who now holds `hcfcore.deathban.bypass` still cannot join.**
The node is read at the death, not at login. Lift the ban with `/pvp lift <player>`, from the console if need be.

**Players cannot open doors or chests, or press buttons, at spawn.**
On server land, nobody but staff builds or interacts — doors, chests, buttons and signs included. Keep what players must use off server land; kit refill signs are the one exception and work there.

**Players cannot use `/top`.**
It is a staff command by default: it has no countdown, so it would lift anybody out of any trap. Grant `hcfcore.general.top` to players deliberately if your server wants it.

**Teammates, or allies, cannot hit each other.**
That is `pvp.yml`'s `friendly-fire`: teammates never hurt each other, allies only in an event area.

**The killer's teammates pick up the loot, nobody else can.**
Loot protection: for 10 seconds after a kill, drops belong to the killer and their team (`pvp.yml`, `loot-protection`).

**A frozen player disconnected and is now banned.**
Leaving while frozen bans. Lift it with `/freeze unban <player>`.

## Classes

**A class does not turn on.**
All four pieces must be the class's exact items (a netherite set is not a diamond one), worn for the whole warmup (10 s): `/class` shows the countdown. A class with a `permission` needs it; a class with `max-per-team` stays off while the team is full — take a piece off to try again. `/class info <class>` shows the set.

**An Archer's dyed set gives no effect.**
All four pieces must be leather, dyed, and read as the same colour: `/class` shows the colour the set reads as, if any. The effect is a chance per hit (20% for green, black and blue as shipped), and an arrow the rules of combat refuse gives nothing.

**A Bard's items do nothing.**
Held and click effects need the class on, and do not work on a safe zone (`abilities-in-safe-zones`). A click costs energy: the scoreboard (`%class_energy_line%`) and `/class` show how much is left.

## Classic combat

**Classic combat does nothing.**
`config.yml` must say `combat: classic`, and `pvp.yml` must be enabled; the console says `Combat: classic.` after `/hcf reload`. Each part has its own switch under `legacy-combat`.

**A sword does not block.**
Swords gain blocking while held, within half a second; switch items once. `sword-blocking.enabled` must be on.

**A weapon keeps its modern damage.**
It must be listed under `weapon-damage.damage`, by its item name. A weapon whose attributes a kit or another plugin set is left alone on purpose: give the kit a plain weapon.

**Players regenerate too slowly, or not at all.**
Classic regeneration heals half a heart every 4 seconds with 18 food or more, as 1.7 did, and only where the natural health regeneration gamerule is on.

**Players regenerate a point of health every second.**
The world is on Peaceful: the game heals everybody there, whatever the plugin does. `/difficulty easy` (or above) — a world's difficulty can differ from `server.properties` once changed in game.

## Events

**The KOTH is never captured.**
Anyone of another team in the zone freezes the countdown — allies included, and, by default, players with no team (`teamless-players-contest`).

**Pearls or partner items still work in the Citadel.**
The restrictions hold on the land of the server team named by the Citadel's `claim` in `events.yml`: draw it with `/events claim citadel`, which makes the server team if needed. `/team here` shows who owns the land you stand on. The console warns when a Citadel starts without its claim. A Citadel written under `events:` rather than `citadels:` — as in files from before Citadels had a section of their own — is a plain KOTH with no restrictions: move it.

**The King's coordinates are not on the scoreboard.**
They are the `%king_location_line%` row of `ui.yml`, added after `%king_line%` in the shipped file. A `ui.yml` written before it does not have the row: add it. The chat gives the position and the King's health once a minute (`announce-interval-seconds` in `events.yml`).

**Kill the King is called off.**
The announcement says why: fewer than `minimum-players` eligible players online (survival or adventure mode, without `hcfcore.events.king.exempt`), no warzone in the event's world (`claims.yml`, `warzone.worlds`), or no safe spot found in it. The world cannot be one with a bedrock ceiling, such as the Nether.

**A Mountain refill changes nothing.**
By default a refill fills only air: a region under water or inside solid rock has nothing to fill. Move the region, or list the blocks a refill may replace under `replace.blocks`.

**An event or a refill runs at the wrong hour.**
Times are read in the file's `time-zone`, `"system"` by default — the host's clock. Write your zone, e.g. `"Europe/Paris"`.

**An example event or Mountain appears somewhere odd.**
The examples in `events.yml` and `resourcenodes.yml` sit at made-up coordinates. They run only when staff start them, until given times — but their zone holograms show as long as they are configured. Move them onto your map or delete them ([Setting up a map](../getting-started/setup-guide.md#0-the-examples)).

## Commands and chat

**Players cannot type in chat at all; the client says "Chat disabled due to missing profile public key".**
That comes from the client and the server's `enforce-secure-profile` setting in `server.properties`, not from HCFCore: the message never reaches the server.

**Team chat shows in the console.**
On purpose, so staff can read a conversation back (`chat.yml`, `log-team-chat`).

**A command clashes with another plugin (`/spawn`, `/msg`...).**
If an essentials plugin provides the utility commands, turn HCFCore's off with `general.yml`'s `enabled: false`.

## Integrations

**Lunar Client players see no waypoints or nametags.**
Check, in order: the `Apollo-Bukkit` plugin is on the server (the console says `Lunar Client features enabled through Apollo.` at startup); Apollo's `/lunarclient <player>` sees the player on Lunar Client; the module is on in Apollo's own configuration and in `apollo.yml`.

**Shops or other plugins do not see HCFCore balances.**
Vault must be installed, and no other economy plugin should be registered with it ([Integrations](integrations.md#vault)).
