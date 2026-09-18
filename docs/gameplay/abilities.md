# Abilities (partner items)

*Configured in [`abilities.yml`](../reference/configuration/abilities.md). Commands: `/ability`, `/ability give`, `/ability reset`.*

A partner item is an item that **does something when it is used**: right-clicked, thrown, hit with, shot from, or reeled in. What it does is its **type**, built into the plugin; which ones a server runs, and every value they read, are `abilities.yml`'s. The plugin ships 42 — several share a type, such as the four that only give effects.

| Command | Does | Permission |
|---|---|---|
| `/ability` | A menu of every ability, with your cooldown on each | everyone |
| `/ability list` | The abilities, in chat | everyone |
| `/ability give <player> <ability> [amount]` | Hand one out — from the console too: a killstreak, a redeem code, a store | `hcfcore.ability.admin` |
| `/ability reset <player> [all\|global\|<ability>]` | End a player's cooldowns: `all` (the default) — every ability's, every Pocket Bard set's and the shared one; `global` — the shared one only; an ability's id — that one only | `hcfcore.ability.admin` |

## Shared rules

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:global"
```

- **Two cooldowns**: each ability's own (`cooldown-seconds`), and a **shared one** after any ability — 10 seconds before the next, whichever it is. The Pocket Bard itself takes no part in it — the items it gives do.
- **Where no ability works**: a safe zone (spawn), a Citadel's claim, the zone of a running KOTH, Citadel or Conquest, the Nether, the End — and the warzone if you switch it on.
- **An enemy is only reached if you could hit them**: never a teammate, nobody on a safe zone, nobody during SOTW, an ally only in an event area — the rules of a blow ([Combat](combat.md)). The Sun alone spares no side: it catches teammates and its user too, safe zones and SOTW aside. A teleport to a player who has since stepped onto a safe zone is cancelled.
- **Nothing is spent on a refusal**: on cooldown, in a refused zone, no target — the item stays, and no cooldown starts.
- An ability item is never placed as a block — a Crafting Chaos stays a crafting table in hand.
- Most are used up — one taken from the stack. Some stay: the Grappling Hook for good, and those with `uses` — the Olympia (30), the Pumpkin Reaper and the Nausea Axe (10), the Portable Archer (5) — until their durability bar, which counts the uses left, runs out. Hits and shots do not wear them, nor a chance that misses.
- Lunar Client players see each cooldown as an icon ([Integrations](../server/integrations.md)).

## The 42 abilities

| Ability | Item | Used by | Does |
|---|---|---|---|
| Switcher | snowball | throwing it | Switch places with the player it hits, 8 blocks away at most |
| Thunderbolt | gold ingot | right-click | 10 s: each hit has 20% to strike lightning, 1.5 hearts through armour |
| Combo Ability | bowl | right-click | 10 s of hits counted (12 at most), then Strength II for a second per hit |
| Lucky Mode | yellow dye | right-click | 10 s: each hit deals from 10% less to 35% more, at random |
| Rage Ball | egg | throwing it | Where it lands, 8 blocks: teammates Strength II and Resistance III, enemies Wither II |
| Crafting Chaos | crafting table | 3 hits with it | 10 s: each of your hits has 20% to open a crafting table on them |
| Focus Mode | gold nugget | right-click | The last player who hit you takes 25% more damage from you, 10 s |
| Ninja Ability | nether star | right-click | Teleport to the last player **you** hit (10 s), 3 s later |
| Portable Archer | bow | shooting | Its arrows archer-tag who they hit, 10 s; breaks after 5 shots |
| Invisibility | ink sac | right-click | Invisible 2 minutes, armour hidden too, until a player hits you |
| Time Warp | feather | right-click | Back to where you threw your last pearl, 2 s later |
| Pocket Bard | orange dye | right-click | A menu to pick 3 Bard items: Strength II, Resistance III, Jump VII or Speed III |
| Berserk | red dye | right-click | Strength II, Resistance III, Regeneration III — and no Healing II potion — for 8 s |
| Close Call | cookie | right-click | Strength II and Regeneration V for 6 s, at 3.5 hearts or less only |
| Switch Stick | stick | right-click | 10 s: each hit has 20% to turn the player hit around |
| Belch Bomb | slime ball | right-click | Slowness II and Blindness II for every enemy within 8 blocks, 6 s |
| Anti Trap Star | nether star | right-click | Teleport to the last player who hit **you** (10 s), 3 s later — out of a trap |
| Rose Thorn | rose bush | a hit with it | 10 s: 30% of the damage that player deals you goes back to them |
| Pumpkin Reaper | diamond hoe | a hit with it | On a Diamond: 50% that their helmet becomes a pumpkin for 10 s. Kept: 10 uses |
| Hulk Smash | piston | right-click | Every enemy within 10 blocks thrown about 6 blocks up |
| Sticky Web | cobweb | right-click | No fall damage for 10 s |
| Med Kit | glistering melon | right-click | Regeneration III and Absorption V for 10 s |
| Grappling Hook | fishing rod | reeling in | Fly to where the hook is stuck, from the ground or mid-air; no fall damage while in hand. Kept after use |
| Nausea Axe | iron axe | a hit with it | 50%: Nausea for 10 s. Kept: 10 uses |
| Bunny Hop | rabbit's foot | right-click | Speed III and Jump Boost IV for 10 s |
| Ice Berg | blue ice | 3 hits with it | Slowness III for 5 s |
| Antidote | milk bucket | right-click | Takes off your negative effects only |
| Golden Head | golden apple | right-click | Eaten at once: Regeneration II 10 s, Absorption II 2 minutes |
| Grabber | tripwire hook | a hit with it | Pulls the player hit towards you |
| Poisonous Potato | poisonous potato | 3 hits with it | Slowness II, Poison II and Nausea for 10 s |
| Fake Pearl | ender pearl | throwing it | Flies as a pearl, teleports nobody |
| Rocket | firework rocket | right-click | About 10 blocks up, no fall damage for 6 s |
| Combo Fish | tropical fish | right-click | 5 s: the players you hit can be hit again after 2 ticks |
| Anti-Build Bone | bone | 3 hits with it | For 15 s they cannot build, break, or open chests, doors, gates, trapdoors, buttons, levers |
| Rotten Egg | egg | throwing it | The player hit: Slowness and Poison for 10 s |
| Rage Strength | nether wart | right-click | Strength II for 8 s |
| Olympia | iron horse armour | right-click | A shotgun: 10 burning eggs scattered in a cone, 2.5 hearts if all hit; the recoil pushes you back. Kept: 30 shots |
| Baguette | bread | 3 hits with it | Hunger that drains 14 food points over 10 s — time to eat |
| Sun | sunflower | right-click | **Everyone** within 8 blocks — you and your team too: 0.9 heart for each player caught (9 at most), blind 2 s |
| Scrambler | blaze rod | 3 hits with it | Shuffles their hotbar |
| Lucky Bard | golden carrot | right-click | Heads: Strength II, Speed II, Regeneration II — tails: Slowness II, Weakness I, Poison I — 8 s |
| Disarmer Wand | breeze rod | a hit with it | 50%: their weapon swaps places with another item of their inventory |

"The last player who hit you" means within the last 15 seconds (`hit-within-seconds`) — 10 for the two stars. A chance that misses starts the cooldown — a Nausea Axe cannot be tried again at once — but keeps the item and its uses: only a success spends them. Every value in this table is a setting — the pages below quote each ability as shipped.

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

### Ninja Ability, Anti Trap Star

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:ninja"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:anti-trap-star"
```

The **Ninja** goes to the last player you hit; the **Anti Trap Star** to the last player who hit you. The teleport happens after the delay, to where the player stands then. It is cancelled if they have left, changed world, or stepped where you may not reach them — a safe zone.

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

Right-clicking opens the menu; **nothing is spent until a set is picked**. The Pocket Bard itself has **no cooldown**, and does not start or wait for the shared one: the items it gives have theirs. The items it gives work as a Bard's burst: a right-click gives their effect to the teammates within 20 blocks and to you, and uses one up. **Each set waits 60 seconds between two uses** (`cooldown-seconds`), and every item waits for and starts the **shared cooldown** — Strength II and Resistance III are never given at once. In a Bard's hand they are never taken for the class's own items.

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

### Effects for you: Med Kit, Bunny Hop, Golden Head, Rage Strength

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:med-kit"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:bunny-hop"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:golden-head"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:rage-strength"
```

The Golden Head is eaten at once, on the click, rather than as a golden apple is.

### Lucky Bard

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:lucky-bard"
```

### Antidote

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:antidote"
```

Drunk at once, on the click. What the game counts as a harmful effect goes — Poison, Wither, Slowness, Weakness, Nausea, Blindness, Mining Fatigue…; Strength, Speed and the other good ones stay, where a plain milk bucket would take everything.

### Sticky Web, Rocket

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:sticky-web"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:rocket"
```

### Hulk Smash

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:hulk-smash"
```

They are thrown a little away from you too. Their fall is theirs: a Hulk Smash is also a way to hurt.

### Combo Fish

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:combo-fish"
```

After a hit, the game makes a player untouchable for 10 ticks. For the Combo Fish's 5 seconds, a player you hit can be hit again after 2 ticks (`hit-delay-ticks`), as in a practice server's combo mode.

### Olympia

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:olympia"
```

The eggs scatter in a cone, as a shotgun's pellets, and hatch nothing. Each one that hits a player you could hit sets them on fire and takes its quarter heart through armour: 2.5 hearts if all ten hit, fire aside. The Olympia stays for 30 shots, its durability bar counting them. The recoil pushes you away from where you aim: aimed at the ground, it throws you up.

### Sun

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:sun"
```

A weapon of last resort: it catches **everyone** within 8 blocks — you and your teammates as much as your enemies (`hits-everyone: false` spares all but enemies) — and each player caught makes it hurt more. You alone with 2 enemies: 3 players, 2.7 hearts each, yours included; 10 or more, 9. The fireworks are only a sight. The damage goes through armour, and an enemy's death is credited to you. Nobody in a safe zone is caught, nor anybody during SOTW.

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

### Rotten Egg

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:rotten-egg"
```

### Fake Pearl

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:fake-pearl"
```

Nothing tells it from a real pearl until it lands, and vanishes. Its short cooldown between two throws is its own: your real pearls stay ready. Time Warp never goes back to where a fake pearl was thrown.

## Hit with

When it takes several hits, they must land on the same player, 10 seconds apart at most; you are told how many are left.

### Crafting Chaos, Anti-Build Bone

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:crafting-chaos"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:anti-build"
```

Anti-build is on the player hit alone. It refuses placing and breaking blocks, buckets, and using the blocks listed: a block's own name (`CHEST`) is that block alone; a family's end (`FENCE_GATE`, `DOOR`, `BUTTON`) is every one of them.

### Effects for them: Nausea Axe, Ice Berg, Poisonous Potato

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:nausea-axe"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:ice-berg"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:poisonous-potato"
```

### Rose Thorn

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:rose-thorn"
```

What comes back is 30% of the damage they really dealt you, after armour; it goes through theirs.

### Pumpkin Reaper

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:pumpkin-reaper"
```

Only on a player whose Diamond class is on (`classes`); on anybody else, or a bare head, nothing is spent. The pumpkin carries Curse of Binding: it stays on until the helmet comes back — then it is gone, wherever it was. If the head is taken by then, the helmet goes in the inventory. A death meanwhile drops the helmet, never the pumpkin.

### Grabber

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:grabber"
```

The pull comes right after the hit's own knockback, so it wins over it.

### Baguette, Scrambler, Disarmer Wand

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:baguette"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:scrambler"
```

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:disarmer-wand"
```

The Baguette's Hunger is the game's own, strong enough to empty 14 points in its 10 seconds once their saturation is gone — it is taken at once: eating keeps them up, an Antidote cures it. The Scrambler shuffles the nine hotbar slots. The Disarmer Wand swaps the item in hand with one from the inventory above the hotbar, a filled slot if there is one: nothing ever falls on the ground.

## Shot from

### Portable Archer

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:portable-archer"
```

The tag is the Archer class's own: the scoreboard shows it, and every player's damage on the target is multiplied. The bow wears one use per shot, and a shot on cooldown is refused.

## Reeled in

### Grappling Hook

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:grappling-hook"
```

Cast the hook at a block; once it is stuck in it, lying on it, or against its side, reel in: you fly to it, in an arc — standing, jumping or falling. Casting costs nothing — the cooldown starts on the pull. The rod never breaks.

## Your own: `commands`

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:commands-example"
```

A `commands` ability runs console commands, with `%player%`, on a right-click — anything the types do not cover.
