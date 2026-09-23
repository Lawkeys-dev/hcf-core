# Combat

*Configured in [`pvp.yml`](../reference/configuration/pvp.md). Command: `/pvp`.*

!!! tip "The 1.7.10 feel"
    `combat: classic` in `config.yml` switches the server to the 1.7.10 combat HCF grew up on — no attack cooldown, sword blocking, 1.7 knockback and regeneration, fast pots. See [Classic combat](classic-combat.md).

## Combat tag

A hit tags **both players for 30 seconds** (`combat-tag.duration-seconds`; `tag-attacker` decides whether the attacker is tagged too). `/pvp` shows your tag and deathban status.

A *hit* is anything the damage is credited to a player for:

- a blow or an arrow — projectiles trace back to the shooter, so an archer is tagged;
- the blast of TNT a player lit, or of a crystal a player struck.

A bed or respawn anchor set off in the wrong dimension is credited to nobody. Every rule on this page applies to credited blasts as to a blow.

**While tagged**, a player cannot use plugin teleports — `/team hq`, `/team base`, `/team stuck`, `/spawn`, `/world`, `/top` (`block-teleport`). A teleport countdown checks the tag at the start **and again at the end**.

The tag is **not saved**: it lasts a few dozen seconds, and making it survive a restart would punish players for the server being down.

## Ender pearl cooldown

A pearl thrown means **15 seconds before the next** (`ender-pearl-cooldown` in `pvp.yml`). A pearl thrown meanwhile is refused and stays in hand. The wait shows on the scoreboard, on the pearls in the hotbar, and as a Lunar Client icon; a death ends it, a logout does not. Until it is over, **teleport commands are refused** as in combat — `/spawn`, `/team hq`, `/team stuck`, `/top`, `/world` (`block-teleport`): a pearl out of a fight is not followed by a command out of it. A countdown already running is refused at its end. A partner item's Fake Pearl has its own cooldown and never starts this one ([Abilities](abilities.md)).

## Item cooldowns

Some items wait between two uses (`item-cooldowns` in `pvp.yml`). As shipped:

| Item | Wait | A death ends it |
|---|---|---|
| Enchanted golden apple (Gapple) | 1 hour | no |
| Golden apple (Crapple) | 10 seconds | yes |
| Chorus fruit | 15 seconds | yes |
| Totem of Undying | 2 minutes | no |

Eaten meanwhile, the item is refused and stays; a totem on cooldown saves nobody. Each wait shows on the scoreboard, greyed out on the item in the hotbar, and as a Lunar Client icon. It is **kept on the player**: an hour's Gapple survives logouts and restarts. Add any item — each needs an id, a material, a wait and a name. Partner items are never counted: a Golden Head has its own cooldown. Staff end one with `/cooldown reset <player> items`, or a single item's id.

## Combat logging

**Logging out while tagged kills the player** (`kill-on-logout`). That death counts like any other: deathban, DTR lost, drops on the ground.

`/logout` is the way to leave that is visibly not an escape: a **30-second countdown** that damage or moving cancels. It is refused while tagged, when it is typed and again when its countdown ends — striking a blow during the countdown tags the attacker, and the kick would then be a combat log.

## Safe zones

Nobody hits or is hit on **safe** server land — spawn, typically (`safe-zones.enabled`). Server land created as a **combat** zone — roads, event grounds — and the warzone are fought on like anywhere else. See [Territory](territory.md#server-land).

**A hit refused for any reason tags nobody.**

## Friendly fire

- **Teammates never hurt each other** (`friendly-fire.teammates: false`).
- **Allies hurt each other only in an event area** (`friendly-fire.allies: EVENT_AREAS`): inside the zone of a running KOTH, Citadel or Conquest, and on or by the King during Kill the King — where allied teams compete, since captures and the King are strictly per team. `ALWAYS` and `NEVER` are the alternatives.

Mountains are not event areas: nobody captures a node.

## Not only hits

On a **safe zone**, a player takes no damage at all — not only no PvP: no fall, fire, drowning, suffocation, mob or cactus damage (`pvp.yml`, `safe-zones.no-damage`), and their health and hunger are filled back up (`heal`, `keep-fed`).

**A player in combat cannot enter one** until their tag runs out (`block-combat-tagged`): a fight is not ended by running home to spawn. They see the border they may not cross as a wall of red glass, 15 blocks each way in front of them and up to layer 128 — sent to them alone, never placed, like the claiming wand's columns (`safe-zones.wall`).

Where a player may not hit another — a safe zone, SOTW, a teammate, an ally outside an event area — they may not affect them either:

- a splash or lingering potion with a harmful effect does nothing to them (a potion of healing still heals);
- a fishing rod does not reel them in;
- a wind charge — or TNT or a crystal the player set off — does not push them.

## Death signs

A kill by a player leaves a **death sign**: the dead player's name, "slain by", the killer's name and the date. It falls with the loot (or goes straight to the killer, `death-signs.to-killer`), and keeps its text when placed — waxed, so nobody rewrites it. A trophy wall of them is a classic of HCF bases.

## Loot protection

For **10 seconds** after a kill, what the dead player dropped can be picked up **only by the killer and the killer's team** — no other player, no mob, no hopper (`loot-protection`). Then it is ordinary loot.

- The drops fall where they always did; they only carry the claim.
- A death with no killer (lava, a fall) is not protected.
- The killer's allies are not included: strictly per team.
- `team-shares: false` keeps the loot for the killer alone.

## Strength nerf

Strength potions give less than vanilla (`strength-nerf`): the plugin subtracts vanilla's bonus per level and adds its own, **1.5** by default. Only on a blow: an arrow's damage never had Strength in it.

!!! warning "Check `vanilla-bonus-per-level` for your version"
    Vanilla's bonus is a setting (`3.0` shipped), not a constant: Mojang has changed the formula before, and the value for the targeted version could not be confirmed in official documentation. Verify it for your Minecraft version and correct it in `pvp.yml` — no recompile needed.

## Knockback and attack speed

Both ship **off**: they change how combat feels, so they should be a deliberate choice.

- **Knockback** scales the knockback of one player hitting another — weapon, fist or projectile — with separate `horizontal` and `vertical` multipliers (`1.0` is vanilla). Falling, sprinting and other plugins are untouched.
- **Attack speed** replaces every player's base attack-speed attribute (`4.0` is vanilla's base); the weapon in hand still adds its own modifier. A higher value shortens the recharge between full-strength hits — a large one gives a pre-1.9 feel. It is given as a modifier never saved to player data: switching it off, or removing the plugin, leaves nobody with a changed attribute.

## Switching combat off

`enabled: false` in `pvp.yml` disables the whole module: no deathbans, no combat tags, no combat tweaks. Each part also has its own switch. SOTW's no-PvP rule holds even then: it is the map's rule.
