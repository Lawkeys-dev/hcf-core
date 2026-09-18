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

## Critical hits

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-criticals"
```

In 1.7, a hit while falling was critical — **sprinting included**. The modern game refuses a critical while sprinting; classic combat gives it, multiplied by `multiplier`, with its particles. A hit the modern game already counts as critical is left as it is.

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

The modern game heals fast while saturation lasts. 1.7 healed half a heart every 4 seconds while the food bar was at 18 or more, each heal costing some hunger — which is why a fight was decided by pots and apples, not by waiting. Classic combat refuses the modern regeneration and heals the 1.7 way, wherever the world's natural health regeneration gamerule is on.

## Golden apples

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-apples"
```

The golden apple and the enchanted (Notch) apple give their 1.7.10 effects — the Notch apple's **Regeneration V**, where the modern one gives Regeneration II and Absorption IV. Each apple's food, saturation and effects are settings.

## Strength

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-strength"
```

1.7 Strength multiplied the hit: **Strength I was +130%**, Strength II +260%. While on, it replaces the HCF `strength-nerf` of `pvp.yml`; switch it off to keep the nerf with classic combat — many 1.7 HCF servers nerfed Strength, and the nerf is there for it.

## Fishing rod

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml:legacy-rod"
```

In 1.7, a rod's hook hitting a player was a hit of no damage: it knocked them back and counted as combat — "rodding". Classic combat does the same: the hooked player is pushed away from the angler with the 1.7 knockback, shown hurt, and both are combat-tagged. Only a player the angler could hit: not on a safe zone, not during SOTW, never a teammate.

## What stays modern

Weapon damage (a diamond axe hits harder than in 1.7), the armour formula, and the Sharpness bonus stay as the current game has them. The armour formula of 1.7 could only be reproduced through an API Paper has deprecated.
