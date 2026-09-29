# pvp.yml

Combat: deathbans and their rank tiers, the combat tag, the strength nerf, knockback, attack speed, safe zones, loot protection and friendly fire.

**How it plays:** [:octicons-arrow-right-24: Combat](../../gameplay/combat.md) · [Deathbans and lives](../../gameplay/deathbans-and-lives.md)

Every example on this page is **taken from the shipped `pvp.yml`**. Changes apply with `/hcf reload`.

## Switch

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:enabled"
```

`enabled: false` turns the whole module off: no deathbans, no combat tags, no combat tuning. SOTW's no-PvP rule still holds.

## Deathban

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:deathban"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Deathbans at all |
| `duration-seconds` | `3600` | The ban for a player with no matching tier |
| `permission-tiers` | `hcfcore.deathban.tier.short: 900` | Permission node to ban length. The shortest tier a player holds wins; only the nodes listed are checked. Add your own |

## Combat tag

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:combat-tag"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | The combat tag at all |
| `duration-seconds` | `30` | How long a hit keeps a player in combat |
| `tag-attacker` | `true` | Tag the attacker as well as the victim |
| `logout` | `npc` | What a tagged player who logs out leaves: `npc` a stand-in that can be killed until the tag runs out, `kill` their death at once, `none` nothing. The old `kill-on-logout: true` reads as `npc`, `false` as `none` |
| `logger.entity` | `VILLAGER` | The stand-in: any living entity but a player |
| `logger.health` | `20` | Its health when it appears, whatever the player had, in half hearts |
| `logger.knockback` | `true` | A blow pushes it back as it would the player; `false`, it does not budge. It never walks either way |
| `block-teleport` | `true` | Refuse plugin teleports (`/team hq`, `/spawn`...) while tagged |

## Ender pearl cooldown

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:ender-pearl-cooldown"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | The pearl cooldown at all |
| `seconds` | `15` | The wait between two pearls |
| `show-on-item` | `true` | The pearls in the hotbar are greyed out for the wait, as the game shows its own |
| `clear-on-death` | `true` | A death ends it |
| `block-teleport` | `true` | Refuse plugin teleports (`/spawn`, `/team hq`, `/team stuck`, `/top`, `/world`) until it is over |

## Item cooldowns

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:item-cooldowns"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Item cooldowns at all |
| `items.<id>` | Gapple, Crapple, chorus fruit, totem | One item. The id names it in `%cooldown_<id>%`, `%cooldown_<id>_line%` and `/cooldown reset` |
| `items.<id>.material` | | The item; one cooldown per item |
| `items.<id>.seconds` | `3600` · `10` · `15` · `120` | The wait |
| `items.<id>.name` | `&6Gapple`… | How the scoreboard and the messages call it |
| `items.<id>.show-on-item` | `true` | The items in the hotbar are greyed out for the wait |
| `items.<id>.clear-on-death` | Gapple and totem `false`, others `true` | A death ends it |

An item is used when it is eaten, or — a totem — when it saves its holder. The wait is kept on the player: it survives logouts and restarts.

## Strength nerf

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:strength-nerf"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | The nerf at all |
| `vanilla-bonus-per-level` | `3.0` | The damage vanilla adds per Strength level, subtracted. **Check it for your version** |
| `nerfed-bonus-per-level` | `1.5` | The damage added per level instead |

## Knockback and attack speed

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:knockback"
```

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:attack-speed"
```

| Key | As shipped | What it does |
|---|---|---|
| `knockback.enabled` | `false` | Scale the knockback of a player hitting a player |
| `knockback.horizontal`, `vertical` | `1.0`, `1.0` | Multipliers; `1.0` is vanilla |
| `attack-speed.enabled` | `false` | Replace every player's base attack speed |
| `attack-speed.value` | `4.0` | The base; vanilla's is 4. Higher recharges faster |

## Safe zones

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:safe-zones"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | No PvP on the land of a **safe** server team. `false` makes every server team fightable, whatever its kind — and takes the two below with it |
| `no-damage` | `true` | No damage of any kind there: a fall, fire, drowning, suffocation, a mob, a cactus. The void and `/kill` still land |
| `keep-fed` | `true` | Hunger never drops there, and is filled back up when a player walks, teleports or logs in on safe-zone land |
| `heal` | `true` | Health is filled back up there the same way |
| `block-combat-tagged` | `true` | A player in combat cannot enter a safe zone until their tag runs out |
| `wall.enabled` | `true` | Whether they see the border they may not cross, drawn like the claiming wand's columns — sent to that player alone, never placed |
| `wall.material` | `RED_STAINED_GLASS` | The block it is drawn in |
| `wall.radius-blocks` | `15` | The border's air blocks within this distance of the player are drawn, all round — a ball of wall where they stand. *The older `width-blocks`, `top-y` and `minimum-height` are no longer read.* |

## Death signs

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:death-signs"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | A kill by a player leaves a sign |
| `material` | `OAK_SIGN` | Any standing or hanging sign |
| `to-killer` | `false` | `false`: among the dead player's drops, under loot protection. `true`: into the killer's inventory, at their feet when it is full |
| `date-format` | `dd/MM/yyyy HH:mm` | How `%date%` is written |

The four lines are `pvp.death-sign.line-1` to `line-4` in the [language file](../messages.md).

## Loot protection

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:loot-protection"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Protect a kill's drops |
| `seconds` | `10` | How long only the killer may pick them up |
| `team-shares` | `true` | The killer's team may too |

## Friendly fire

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:friendly-fire"
```

| Key | As shipped | What it does |
|---|---|---|
| `teammates` | `false` | Whether teammates can hurt each other |
| `allies` | `EVENT_AREAS` | Where allies can hurt each other: `ALWAYS`, `EVENT_AREAS` (a running KOTH, Citadel or Conquest zone, and the King) or `NEVER` |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/pvp.yml).

<div class="hcf-shipped" markdown>

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml"
```

</div>
