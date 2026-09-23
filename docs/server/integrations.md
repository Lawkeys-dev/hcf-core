# Integrations

All three are soft dependencies: HCFCore works fully without any of them, and uses each one only if its plugin is on the server at startup.

## Vault

With [Vault](https://github.com/MilkBowl/Vault) installed, HCFCore registers its economy as Vault's `Economy` provider at startup — the console says `Economy registered with Vault.`, and Vault's own `/vault-info` lists `Economy: HCFCore`. Shops, crates, paid ranks and any other Vault-aware plugin then read and change the same balances as `/balance` and `/pay`. Tested with Vault 1.7.3.

- **Run one economy.** HCFCore registers at normal priority. If another economy plugin (EssentialsX's, for example) registers too, other plugins may end up using that one instead.
- **Team banks are not exposed.** Vault's bank calls say nothing about *who* is asking, so exposing team banks would let any installed plugin move a team's money without any rank check. Vault reports no bank support.
- **No per-world balances.** The world argument of Vault's calls is ignored: a player has one balance.
- `economy.yml`'s `enabled: false` makes Vault report the economy as disabled.
- Balances are stored as decimals (`double`), the type Vault's interface itself uses.

## LuckPerms

- **Permissions need no integration.** Every check goes through Bukkit's standard permission API, which LuckPerms implements; so does any other permission plugin.
- **Chat prefix and suffix.** With [LuckPerms](https://luckperms.net) installed, the `%prefix%` and `%suffix%` of `chat.yml`'s format are the player's LuckPerms prefix and suffix — the console says `Hooked LuckPerms for chat prefixes and suffixes.` at startup. Without it, they are empty. Tested with LuckPerms 5.5.84.
- **The tab list.** The same prefix and suffix are `%prefix%` and `%suffix%` in the tab list (`ui.yml`), and the classic list can be ordered by rank: the weight of each player's primary group.

## Lunar Client (Apollo)

For players on Lunar Client, HCFCore can draw information on their screen through Lunar's [Apollo](https://lunarclient.dev/apollo). Players on any other client are sent nothing and see the plugin exactly as usual.

**Install the Apollo plugin on the server** — `Apollo-Bukkit`, from Lunar's downloads page or the [LunarClient/Apollo releases](https://github.com/LunarClient/Apollo/releases). HCFCore does not bundle it. Tested with Apollo-Bukkit 1.2.9. At startup the console says `Lunar Client features enabled through Apollo.` — or that Apollo is not installed. Apollo's own `/lunarclient <player>` tells you whether it sees a player on Lunar Client.

What is shown, each part switched in `apollo.yml`:

| Module | Shows |
|---|---|
| **Waypoints** | Your team's HQ, base and rally point; a focused player where they stand, and a focused team's HQ; KOTH, Citadel and Conquest zones; a running DTC or Last Break's core; a running Slide's zone, a running Totem's column; the King during Kill the King. Each appears when it starts and disappears when it ends |
| **Team view** | Teammates marked above their heads and on the minimap; beyond 48 blocks, where the client no longer tracks them, their position and name are sent |
| **Cooldowns** | Lunar cooldown icons for every cooldown: the combat tag, the ender pearl, the item cooldowns (Gapple, Crapple...), a running countdown (`/spawn`, `/logout`, `/team hq`, `/team stuck`...), partner items (each, the shared one, the Pocket Bard's sets), class items (a Bard's clicks, a Rogue's backstab) and the crowbar |
| **Nametags** | A team line (`Team | DTR`) above each player's name, coloured by how their team relates to yours — your team, an ally, an enemy, a focused target or no team |

Apollo's own configuration can switch each of its modules off for the whole server; a module off there stays off whatever `apollo.yml` says. `/hcf reload` takes back everything sent and sends it again as `apollo.yml` now says.

## Discord

The server's announcements can be posted to a Discord channel through a **webhook** — nothing to install, no bot. An event started or won, SOTW and EOTW, a bounty placed or collected, a team going raidable or protected again: the same text as in the game, colours removed.

1. In Discord: the channel's settings › **Integrations** › **Webhooks** › **New Webhook** › **Copy Webhook URL**. Keep it private — anybody holding it can post in that channel.
2. In `discord.yml`: paste it under `webhooks`, set `enabled: true`, `/hcf reload`.

What goes where is a list of rules on the language keys (`forward`), read top to bottom: the shipped one posts every event start and result, the map phases, bounties and raids, and leaves out the chatty lines. Several webhooks can split them across channels. See [`discord.yml`](../reference/configuration/discord.md).

## The HCF tab list needs nothing

The HCF tab list's grid is made of lines that are not players, which Paper's API cannot show. HCFCore sends them itself, as the server's own tab list packets — no PacketEvents, no ProtocolLib. They are checked when the plugin starts: on a Minecraft version whose packets are shaped otherwise, the classic tab list is shown instead, and the console says why.
