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
| `kill-on-logout` | `true` | A tagged player who logs out dies |
| `block-teleport` | `true` | Refuse plugin teleports (`/team hq`, `/spawn`...) while tagged |

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

`enabled: false` makes every server team fightable, whatever its kind.

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
