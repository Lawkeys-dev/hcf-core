# Abilities (partner items)

*Configured in [`abilities.yml`](../reference/configuration/abilities.md). Commands: `/ability`, `/ability give`.*

A partner item is an item that **does something when it is used**: right-clicked, thrown, hit with, or shot from. What it does is its **type**, built into the plugin; which ones a server runs, and every value they read, are `abilities.yml`'s. The plugin ships 21, one of each type.

| Command | Does | Permission |
|---|---|---|
| `/ability` | A menu of every ability, with your cooldown on each | everyone |
| `/ability list` | The abilities, in chat | everyone |
| `/ability give <player> <ability> [amount]` | Hand one out — from the console too: a killstreak, a redeem code, a store | `hcfcore.ability.admin` |

## Shared rules

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:global"
```

- **Two cooldowns**: each ability's own (`cooldown-seconds`), and a **shared one** after any ability — 10 seconds before the next, whichever it is.
- **Where no ability works**: a Citadel's claim, the zone of a running KOTH, Citadel or Conquest, the Nether, the End — and the warzone if you switch it on.
- **An enemy is only reached if you could hit them**: never a teammate, nobody on a safe zone, nobody during SOTW, an ally only in an event area — the rules of a blow ([Combat](combat.md)). A teleport to a player who has since stepped onto a safe zone is cancelled.
- **Nothing is spent on a refusal**: on cooldown, in a refused zone, no target — the item stays, and no cooldown starts.
- An ability item is never placed as a block — a Crafting Chaos stays a crafting table in hand.
- Lunar Client players see each cooldown as an icon ([Integrations](../server/integrations.md)).

## The 21 abilities

| Ability | Item | Used by | Does |
|---|---|---|---|
| Switcher | snowball | throwing it | Switch places with the player it hits, 8 blocks away at most |
| Thunderbolt | gold ingot | right-click | 10 s: each hit has 20% to strike lightning, 1.5 hearts through armour |
| Combo Ability | bowl | right-click | 10 s of hits counted (12 at most), then Strength II for a second per hit |
| Lucky Mode | yellow dye | right-click | 10 s: each hit deals from 10% less to 35% more, at random |
| Rage Ball | egg | throwing it | Where it lands, 8 blocks: teammates Strength II and Resistance III, enemies Wither II |
| Crafting Chaos | crafting table | 3 hits with it | 10 s: each of your hits has 20% to open a crafting table on them |
| Focus Mode | gold nugget | right-click | The last player who hit you takes 25% more damage from you, 10 s |
| Ninja Ability | nether star | right-click | Teleport to the last player who hit you, 3 s later |
| Exotic Bone | bone | 3 hits with it | They cannot build, break or open chests, gates, trapdoors for 15 s; you get Speed III |
| Portable Archer | bow | shooting | Its arrows archer-tag who they hit, 10 s; breaks after 5 shots |
| Invisibility | ink sac | right-click | Invisible 2 minutes, armour hidden too, until a player hits you |
| Time Warp | feather | right-click | Back to where you threw your last pearl, 2 s later |
| Pocket Bard | orange dye | right-click | A menu to pick 3 Bard items: Strength II, Resistance III, Jump VII or Speed III |
| Berserk | red dye | right-click | Strength II, Resistance III, Regeneration III — and no Healing II potion — for 8 s |
| Close Call | cookie | right-click | Strength II and Regeneration V for 6 s, at 3.5 hearts or less only |
| Switch Stick | stick | right-click | 10 s: each hit has 20% to turn the player hit around |
| Teleport Eye | purple dye | right-click, in water | Teleport to the last player who hit you, 3 s later |
| Samurai | diamond sword | right-click | Teleport to the last player who hit you: anti-build 15 s and no pearl 16 s for them, Strength II and Speed III for you. Kept after use |
| Magic Rock | coal | a hit with it | Strength II for you, longer the less space there is above their head |
| Belch Bomb | slime ball | right-click | Slowness II and Blindness II for every enemy within 8 blocks, 6 s |
| Anti Trap Star | nether star | right-click | Teleport to the last player who hit you with a projectile, 3 s later |

"The last player who hit you" means within the last 15 seconds (`hit-within-seconds`). Every value in this table is a setting — the pages below quote each ability as shipped.

## Right-click

### Thunderbolt

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:thunderbolt"
```

Lightning is only a sight: it sets nothing on fire. The damage goes through armour, and a strike that kills is credited to you — deathban, DTR, statistics.

### Combo Ability

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:combo"
```

### Lucky Mode

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:lucky-mode"
```

### Focus Mode

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:focus-mode"
```

Both players are told. With nobody who hit you lately, nothing is spent.

### Ninja Ability, Teleport Eye, Anti Trap Star

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:ninja"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:teleport-eye"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:anti-trap-star"
```

The teleport happens after the delay, to where the player stands then. It is cancelled if they have left, changed world, or stepped where you may not reach them — a safe zone.

### Samurai

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:samurai"
```

The pearl cooldown is the game's own, shown on the pearl in their hotbar.

### Invisibility

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:invisibility"
```

With `hide-armor`, the other players no longer see your armour — the game's Invisibility shows it. A hit from a player (`reveal-on-hit`) ends it: the effect goes, the armour shows again.

### Time Warp

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:time-warp"
```

Back to where you stood when you threw the pearl — undoing it.

### Pocket Bard

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:pocket-bard"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:pocket-bard-items"
```

Right-clicking opens the menu; **nothing is spent until a set is picked**. The items it gives work as a Bard's burst: a right-click gives their effect to the teammates within 20 blocks and to you, and uses one up. They have no cooldown of their own.

### Berserk

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:berserk"
```

For those 8 seconds, drinking or throwing a potion listed in `denied-potions` is refused, and the potion stays.

### Close Call

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:close-call"
```

### Switch Stick

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:switch-stick"
```

### Belch Bomb

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:belch-bomb"
```

## Thrown

### Switcher

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:switcher"
```

### Rage Ball

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:rage-ball"
```

It works wherever it lands, on a player or on the ground. An egg hatches no chicken.

Thrown on cooldown, it stays in hand.

## Hit with

### Crafting Chaos, Exotic Bone

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:crafting-chaos"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:anti-build"
```

The hits must land on the same player, 10 seconds apart at most; you are told how many are left. Anti-build refuses placing and breaking blocks, buckets, and opening the blocks listed: a block's own name (`CHEST`) is that block alone; a family's end (`FENCE_GATE`, `TRAPDOOR`) is every one of them.

### Magic Rock

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:magic-rock"
```

The free blocks are counted straight up from above the player's head. A count with no line gives nothing, and nothing is spent.

## Shot from

### Portable Archer

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:portable-archer"
```

The tag is the Archer class's own: the scoreboard shows it, and every player's damage on the target is multiplied. The bow wears one use per shot, and a shot on cooldown is refused.

## Your own: `commands`

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:commands-example"
```

A `commands` ability runs console commands, with `%player%`, on a right-click — anything the types do not cover.
