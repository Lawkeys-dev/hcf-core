# Classic combat (1.7.10)

*Switched in [`config.yml`](../reference/configuration/config.md#combat), tuned in [`pvp.yml`](../reference/configuration/pvp.md#classic-combat).*

HCF grew up on the 1.7.10 combat: spam-clicking, sword blocking, combos in the air, fast pots. The server can play either:

```yaml title="config.yml"
--8<-- "src/main/resources/config.yml:combat"
```

| `combat` | What the server plays |
|---|---|
| `modern` | The game's own combat, as it comes — attack cooldown, sweeping, shields, off-hand. **As shipped** |
| `classic` | The 1.7.10 feel, part by part below |

`/hcf reload` applies a switch. Every part of classic combat can be turned off on its own and its numbers tuned in `pvp.yml`, section `legacy-combat`; **every value ships at what 1.7.10 did**.

!!! info "Players on any client"
    Classic combat changes how the server plays, not which client connects: players stay on the current version. To let players join from a 1.7 or 1.8 client, the server needs ViaVersion, ViaBackwards and ViaRewind — separate plugins, to check against your Paper version.

## No attack cooldown, no sweep

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-cooldown"
```

Every click is a full-strength hit, as in 1.7: the attack-speed attribute is set so high that the cooldown never shows. It replaces `attack-speed` while on.

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-sweep"
```

The sword's sweep attack is refused — its damage and its push — so a hit only ever reaches the player hit.

## Weapon damage

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-weapons"
```

Each weapon deals its 1.7.10 damage — the value listed, the player's own point included. **Swords hit harder than today and axes much softer**: in 1.7 the sword was the weapon, the axe a tool.

| | Sword | Axe | Pickaxe | Shovel |
|---|---|---|---|---|
| Wood, gold | 5 | 4 | 3 | 2 |
| Stone | 6 | 5 | 4 | 3 |
| Iron | 7 | 6 | 5 | 4 |
| Diamond | 8 | 7 | 6 | 5 |
| Netherite | 9 | 8 | 7 | 6 |

Netherite did not exist in 1.7: it is one step above diamond, as each material was above the last. An item left out of the list — a trident, a mace — keeps its modern damage; remove a line to keep that weapon modern.

The damage is put on the weapon itself while it is held, so **the tooltip shows it** ("+7 Attack Damage" on a diamond sword, on top of the player's 1, as 1.7 showed it), and critical hits, Strength and Sharpness count from it. A weapon whose attributes a kit or another plugin set on purpose is left alone. The weapon goes back to its modern damage as soon as classic combat or the part is switched off.

## Enchantments

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-enchantments"
```

**Sharpness gives its 1.7 bonus**: 1.25 per level, where the modern game gives 1 at level I and 0.5 per level above.

| Sharpness | I | II | III | IV | V |
|---|---|---|---|---|---|
| 1.7 | +1.25 | +2.5 | +3.75 | +5 | +6.25 |
| Modern | +1 | +1.5 | +2 | +2.5 | +3 |

Then as now, Sharpness is added **last**: a critical hit or Strength multiplies the weapon's damage, never the Sharpness bonus. A diamond sword with Sharpness V deals 8 + 6.25 = 14.25, and 8 × 1.5 + 6.25 = 18.25 on a critical hit — before armour.

The other combat enchantments already play as in 1.7 — checked against the current game's data: Power (+0.5 per level, +0.5), Punch, Fire Aspect (4 seconds per level) and Flame. Knockback goes through the [knockback](#knockback) above.

## Critical hits

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-criticals"
```

In 1.7, a hit while falling was critical — **sprinting included**. The modern game refuses a critical while sprinting; classic combat gives it, multiplied by `multiplier`, with its particles. A critical multiplies the weapon's damage and Strength, not Sharpness, as in 1.7.

## Sword blocking

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-blocking"
```

**Right-click with a sword to block**, as in 1.7: the hit is reduced to `(damage + 1) / 2`, from every direction, at once, with no sound and no wear on the sword. Swords gain the ability while held — whatever brought them there, a kit, a chest, a pickup — and lose it when classic combat or blocking is switched off.

It uses the item ability the game gives shields, set on the sword.

## Knockback

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-knockback"
```

The 1.7.10 formula. A hit divides the victim's velocity by `friction`, then pushes them `horizontal` away and `vertical` up, capped at `vertical-limit`. **The modern game only lifts a victim who stands on the ground; 1.7 lifts them in the air as well** — which is what makes combos. A sprint hit, or a Knockback enchantment, adds `extra-horizontal` per level where the attacker faces, and `extra-vertical` up.

The knockback multipliers of `pvp.yml` (`knockback.horizontal`, `vertical`) still apply on top, for servers that tune their knockback further.

## No off-hand, no shields

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-offhand"
```

1.7 had neither. The swap key, and putting anything in the off-hand slot, are refused; whatever is already there is moved into the inventory, or dropped when it is full. Raising a shield is refused.

## Thrown potions and ender pearls

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-potions"
```

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-pearls"
```

A splash potion or a pearl leaves the hand as in 1.7: straight where the player looks — lifted by `pitch-offset` for potions — at `speed`, **without the player's own movement**. The modern game adds the thrower's movement, which sends pots ahead of a running player; in 1.7 a pot thrown down while running landed at your feet. Raise the potions' `speed` for faster pots.

Ender pearls also lose the modern one-second cooldown. A pearl cooldown of the HCF kind (16 seconds, say) is another matter, not part of this.

## Natural regeneration

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-regeneration"
```

The modern game heals fast while saturation lasts. 1.7 healed half a heart every 4 seconds while the food bar was at 18 or more, each heal costing some hunger — which is why a fight was decided by pots and apples, not by waiting. Classic combat holds the modern regeneration off — the food bar neither heals nor spends hunger for it — and heals the 1.7 way, wherever the world's natural health regeneration gamerule is on.

!!! note "Peaceful"
    On Peaceful difficulty the game itself gives every player a point of health a second, as 1.7 did: classic regeneration only shows on Easy and above.

## Golden apples

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-apples"
```

The golden apple and the enchanted (Notch) apple give their 1.7.10 effects — the Notch apple's **Regeneration V**, where the modern one gives Regeneration II and Absorption IV. Each apple's food, saturation and effects are settings.

## Strength

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-strength"
```

**1.7 Strength was a percentage**, where the modern game adds points: it multiplied the weapon's damage — **Strength I +130%**, Strength II +260%. The modern game adds 3 points per level.

So the two nerfs differ too. The modern `strength-nerf` of `pvp.yml` takes points off (3 per level down to 1.5); **classic combat's nerf is a percentage**: `nerf.per-level` instead of `per-level`. It ships at half, as the modern nerf halves the modern bonus:

| Diamond sword (8) | Strength I | Strength II |
|---|---|---|
| 1.7.10, no nerf | 8 × 2.3 = **18.4** | 8 × 3.6 = **28.8** |
| Classic nerf, `0.65` | 8 × 1.65 = **13.2** | 8 × 2.3 = **18.4** |
| Modern, nerfed (`strength-nerf`) | 7 + 1.5 = **8.5** | 7 + 3 = **10** |

`nerf.enabled: false` gives 1.7.10's own Strength. While 1.7 Strength is on, the modern `strength-nerf` does not apply in classic combat; switch 1.7 Strength off to play the modern flat bonus — and its nerf — with classic combat.

## Fishing rod

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-rod"
```

In 1.7, a rod's hook hitting a player was a hit of no damage: it knocked them back and counted as combat — "rodding". Classic combat does the same: the hooked player is pushed away from the angler with the 1.7 knockback, shown hurt, and both are combat-tagged. Only a player the angler could hit: not on a safe zone, not during SOTW, never a teammate.

The bobber then comes back at once, ready to cast again (`remove-hook`), rather than staying on the player until reeled in — which would also pull them.

## What stays modern

The armour formula and Protection stay as the current game has them: the 1.7 ones could only be reproduced through an API Paper has deprecated. Protection is close anyway: in full Protection IV, the modern game takes 64% off a hit; 1.7 took a random 40 to 80%, 60% on average.

A mace hits as the modern game has it: its fall bonus was no weapon of 1.7.

!!! warning "Removing the plugin"
    A sword's blocking and a weapon's 1.7 damage are set on the item. Switching classic combat off, or disabling the plugin, takes them back from the items **held by the players online** — and from any item as soon as it is held again while the plugin runs. An item lying in a chest or in an offline player's inventory when the plugin is removed keeps them.
