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

## Strength nerf

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:strength-nerf"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | The nerf at all. In classic combat, 1.7 Strength has its own nerf, a percentage: see below |
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

## Classic combat

Used only while `config.yml` says `combat: classic`. Every value ships at what 1.7.10 did. See [Classic combat](../../gameplay/classic-combat.md) for how each part plays.

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-combat"
```

| Key | As shipped | What it does |
|---|---|---|
| `attack-cooldown.remove` | `true` | No attack cooldown: every click is a full hit. Replaces `attack-speed` |
| `attack-cooldown.attack-speed` | `1024.0` | The attack speed that gives it |
| `no-sweep-attacks` | `true` | Refuse the sweep attack's damage and push |
| `weapon-damage.enabled` | `true` | Weapons deal their 1.7 damage |
| `weapon-damage.damage` | 1.7 values | Item → damage, the player's own point included (`diamond_sword: 8`, `diamond_axe: 7`). An item not listed keeps its modern damage |
| `enchantments.enabled` | `true` | 1.7 Sharpness |
| `enchantments.sharpness-per-level` | `1.25` | Sharpness's bonus per level, added after a critical hit and Strength |
| `critical-hits.enabled` | `true` | A critical while falling, sprinting included |
| `critical-hits.multiplier` | `1.5` | What a critical multiplies the hit by |
| `sword-blocking.enabled` | `true` | Right-click with a sword to block |
| `sword-blocking.base`, `factor` | `-0.5`, `0.5` | What is blocked: `base + factor × damage` — 1.7 took `(damage + 1) / 2` |
| `knockback.enabled` | `true` | The 1.7.10 knockback for a melee hit |
| `knockback.friction` | `2.0` | What the victim's velocity is divided by |
| `knockback.horizontal`, `vertical` | `0.4`, `0.4` | The push away, and up — in the air as on the ground |
| `knockback.vertical-limit` | `0.4` | The most a hit lifts |
| `knockback.extra-horizontal`, `extra-vertical` | `0.5`, `0.1` | A sprint hit's or a Knockback level's extra push |
| `disable-offhand` | `true` | No off-hand |
| `disable-shields` | `true` | No shields |
| `thrown-potions.enabled` | `true` | Potions thrown the 1.7 way, without the thrower's movement |
| `thrown-potions.speed`, `pitch-offset`, `inaccuracy` | `0.5`, `-20.0`, `1.0` | How fast, how high above the aim, how spread |
| `ender-pearls.no-cooldown` | `true` | No one-second pearl cooldown |
| `ender-pearls.enabled`, `speed`, `pitch-offset`, `inaccuracy` | `true`, `1.5`, `0.0`, `1.0` | Pearls thrown the 1.7 way |
| `natural-regeneration.enabled` | `true` | 1.7 regeneration instead of the modern fast one |
| `natural-regeneration.interval-seconds`, `amount` | `4.0`, `1.0` | Half a heart every 4 seconds |
| `natural-regeneration.minimum-food`, `exhaustion` | `18`, `3.0` | The food it needs, and the hunger each heal costs |
| `golden-apples.enabled` | `true` | Golden apples with their 1.7 effects |
| `golden-apples.<apple>.food`, `saturation`, `effects` | 1.7 values | Per apple; each effect is an `effect`, a `level` and `seconds` |
| `strength.enabled`, `per-level` | `true`, `1.3` | 1.7 Strength, a percentage of the weapon's damage: +130% per level; replaces `strength-nerf` while on |
| `strength.nerf.enabled`, `per-level` | `true`, `0.65` | The HCF nerf of 1.7 Strength, a percentage too: +65% per level instead. Off gives 1.7.10's own |
| `fishing-rod.enabled` | `true` | A rod's hook knocks a player back and tags both |
| `fishing-rod.remove-hook` | `true` | The bobber comes back at once after the hit, instead of staying on the player until reeled in |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/pvp.yml).

<div class="hcf-shipped" markdown>

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml"
```

</div>
