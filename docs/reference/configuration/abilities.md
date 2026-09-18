# abilities.yml

Partner items: the shared rules, every ability, and the Pocket Bard's sets.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/abilities.md)

Every example on this page is **taken from the shipped `abilities.yml`**. Changes apply with `/hcf reload`.

## Shared rules

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:global"
```

| Key | As shipped | What it does |
|---|---|---|
| `global.enabled` | `true` | The module at all |
| `global.cooldown-seconds` | `10` | A wait after any ability before the next one, whichever it is |
| `global.hits-within-seconds` | `10` | Most time between two hits counted towards `hits-required`; longer and the count starts over |
| `global.menu-title` | `&dAbilities` | `/ability`'s menu |
| `global.disabled-in.citadel` | `true` | No ability on a Citadel's claim |
| `global.disabled-in.events` | `true` | No ability in the zone of a running KOTH, Citadel or Conquest |
| `global.disabled-in.nether` · `end` | `true` | No ability in the Nether, the End |
| `global.disabled-in.warzone` | `false` | No ability in the warzone |

## An ability

Each block under `abilities:` is one ability; its key is its id — stored on the items, so renaming it retires the items handed out.

| Key | Default | What it does |
|---|---|---|
| `type` | — | What it does: one of the types below |
| `material` | — | The item. `switcher`, `rage-ball` and `thrown-effects` are a `SNOWBALL` or an `EGG`; `portable-archer` a `BOW`; `fake-pearl` an `ENDER_PEARL`; `grappling-hook` a `FISHING_ROD` |
| `name` · `lore` | the id · none | Shown on the item; colour codes allowed |
| `glow` | `true` | Shines as an enchanted item does |
| `enchantments` | none | On the item itself: `{infinity: 1}` |
| `cooldown-seconds` | `0` | Wait before this ability can be used again |
| `consume` | `true` | One is taken from the stack when it is used |
| `enabled` | `true` | `false` leaves it out |

An effect is written `{effect: strength, level: 2, seconds: 8}` — level `1` is level I. A key a type does not read is reported in the console; an ability that cannot work — no type, an unknown item, a thrown type that is not thrown — is left out, with a warning.

## The types

#### `commands`

Console commands, run with `%player%`: any ability the others do not cover. Used by a right-click.

It reads `commands`: console commands, run with `%player%`; at least one.

#### `switcher`

Thrown snowball or egg: swaps places with the player it hits, within `distance`. Used by throwing it.

| Key | Default | What it does |
|---|---|---|
| `distance` | `8` | Blocks away the player hit may be, at most |

#### `thunderbolt`

For `seconds`, each hit has `chance`% to strike lightning, for `damage-hearts` through armour. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `seconds` | `10` | How long it runs |
| `chance` | `20` | Percent chance per hit |
| `damage-hearts` | `1.5` | Hearts a strike takes, through armour |

#### `combo`

For `seconds`, hits are counted (to `max-hits`); then `effect` for so many seconds each. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `seconds` | `10` | How long it runs |
| `max-hits` | `12` | Hits counted at most |
| `seconds-per-hit` | `1` | Seconds of the effect per hit counted |
| `effect` | as shipped | The effect |

#### `lucky-mode`

For `seconds`, each hit deals from `min-percent` to `max-percent` more, at random. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `seconds` | `10` | How long it runs |
| `min-percent` | `-10` | Least extra damage, in percent (negative: less) |
| `max-percent` | `35` | Most extra damage, in percent |

#### `rage-ball`

Thrown: where it lands, teammates get `team-effects`, enemies `enemy-effects`. Used by throwing it.

| Key | Default | What it does |
|---|---|---|
| `radius` | `8` | Blocks around where it reaches |
| `cooldown-whole-team` | `true` | The cooldown starts for the whole team |
| `team-effects` | as shipped | Effects for teammates |
| `enemy-effects` | as shipped | Effects for enemies |

#### `crafting-chaos`

`hits-required` hits with it: for `seconds`, each hit has `chance`% to open a crafting table on them. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `hits-required` | `3` | Hits on the same player it takes, `global.hits-within-seconds` apart at most |
| `chance` | `20` | Percent chance per hit |
| `seconds` | `10` | How long it runs |

#### `focus-mode`

The last player who hit you (within `hit-within-seconds`) takes `damage-multiplier` from you, for `seconds`. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `damage-multiplier` | `1.25` | What the damage is multiplied by |
| `seconds` | `10` | How long it runs |
| `hit-within-seconds` | `15` | How recent the hit must be |

#### `ninja`

Teleports, after `delay-seconds`, to the last player you hit (within `hit-within-seconds`). Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `delay-seconds` | `3` | Seconds before the teleport |
| `hit-within-seconds` | `10` | How recent the hit must be |

#### `anti-build`

`hits-required` hits with it: they cannot build, break or use `blocked-blocks` for `seconds`. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `hits-required` | `3` | Hits on the same player it takes, `global.hits-within-seconds` apart at most |
| `seconds` | `15` | How long it runs |
| `blocked-blocks` | as shipped | Blocks that may not be used: a block's name, or a family's end (`FENCE_GATE`) |
| `user-effects` | as shipped | Effects for the user |

#### `portable-archer`

A bow whose arrows archer-tag the player they hit; it breaks after `uses` shots. Used by shooting it.

| Key | Default | What it does |
|---|---|---|
| `uses` | `5` | Shots before the bow breaks |
| `tag-seconds` | `10` | How long the archer tag lasts |
| `damage-multiplier` | `1.15` | What the damage is multiplied by |

#### `invisibility`

Invisible - armour hidden too with `hide-armor` - until hit, with `reveal-on-hit`. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `effect` | as shipped | The effect |
| `hide-armor` | `true` | Armour hidden from the other players too |
| `reveal-on-hit` | `true` | A hit from a player ends it |

#### `time-warp`

Back, after `delay-seconds`, to where you threw your last ender pearl (within `pearl-within-seconds`). Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `pearl-within-seconds` | `15` | How recent the pearl must be |
| `delay-seconds` | `2` | Seconds before the teleport |

#### `pocket-bard`

A menu to pick a set of Bard items (the `pocket-bard` section). Used by a right-click.

It reads nothing of its own: its sets are the `pocket-bard` section. It has no cooldown and takes no part in the shared one; each set's items have their own `cooldown-seconds`, and wait for and start the shared one.

#### `berserk`

`effects`, and no `denied-potions` for `seconds`. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `seconds` | `8` | How long it runs |
| `effects` | as shipped | The effects |
| `denied-potions` | as shipped | Potions refused meanwhile, by type (`strong_healing` is Healing II) |

#### `close-call`

`effects`, only at `max-health-hearts` or less. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `max-health-hearts` | `3.5` | Usable only at this health or less |
| `effects` | as shipped | The effects |

#### `switch-stick`

For `seconds`, each hit has `chance`% to turn the player hit by `degrees`. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `seconds` | `10` | How long it runs |
| `chance` | `20` | Percent chance per hit |
| `degrees` | `180` | How far the player hit is turned |

#### `belch-bomb`

`effects` for every enemy within `radius`. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `radius` | `8` | Blocks around where it reaches |
| `effects` | as shipped | The effects |

#### `anti-trap-star`

Teleports, after `delay-seconds`, to the last player who hit you (within `hit-within-seconds`). Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `delay-seconds` | `3` | Seconds before the teleport |
| `hit-within-seconds` | `10` | How recent the hit must be |

#### `effects`

`effects` for you. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `effects` | as shipped | The effects |

#### `team-effects`

`effects` for your teammates within `radius` - and you, with `include-self`. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `radius` | `20` | Blocks around the user it reaches |
| `include-self` | `true` | The user gets it too |
| `effects` | as shipped | The effects |

#### `lucky-bard`

`positive-chance`% to get `good-effects`; `bad-effects` otherwise. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `positive-chance` | `50` | Percent chance of the good effects |
| `good-effects` · `bad-effects` | as shipped | The effects of either side |

#### `cleanse`

Takes off your negative effects; the others stay. Used by a right-click. It reads nothing of its own.

#### `no-fall`

No fall damage for `seconds`. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `seconds` | `10` | How long it runs |

#### `rocket`

Launches you up, with no fall damage for `no-fall-seconds`. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `height` | `3.5` | How high: `1` is about 3 blocks |
| `no-fall-seconds` | `6` | No fall damage for so long |

#### `hulk-smash`

Throws every enemy within `radius` in the air. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `radius` | `10` | Blocks around the user it reaches |
| `height` | `2` | How high: `1` is about 3 blocks |
| `push` | `0.3` | How far away from the user, as well as up |

#### `combo-fish`

For `seconds`, the players you hit can be hit again after `hit-delay-ticks`, not the game's 10. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `seconds` | `5` | How long it runs |
| `hit-delay-ticks` | `2` | Ticks before a player you hit can be hit again |

#### `shotgun`

Fires `projectiles` eggs in a fan `spread` degrees wide; each sets the player it hits on fire and deals `damage-hearts`; you are pushed back by `recoil`. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `projectiles` | `10` | Eggs per shot |
| `spread` | `10` | Degrees from the leftmost egg to the rightmost |
| `speed` | `1.5` | How fast the eggs fly |
| `damage-hearts` | `0.5` | Hearts an egg takes, through armour |
| `fire-seconds` | `10` | Seconds on fire |
| `recoil` | `1.0` | How hard the user is pushed back; `0` for none |

#### `sun`

Fireworks burst around you; every enemy within `radius` takes `damage-hearts-per-player` for each enemy caught (at most `max-players`), burns and is blinded. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `radius` | `8` | Blocks around the user it reaches |
| `damage-hearts-per-player` | `0.9` | Hearts, through armour, per enemy caught |
| `max-players` | `10` | Enemies counted at most |
| `fire-seconds` | `5` | Seconds on fire |
| `blindness-seconds` | `2` | Seconds of Blindness |
| `fireworks` | `6` | Fireworks bursting around the user: only a sight |

#### `hit-effects`

`hits-required` hits: `chance`% to give the player hit `effects`. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `hits-required` | `1` | Hits on the same player it takes, `global.hits-within-seconds` apart at most |
| `chance` | `100` | Percent chance; a miss spends it all the same |
| `effects` | as shipped | Effects for the player hit |

#### `pumpkin`

A hit: `chance`% that the player hit - in one of `classes` - has their helmet swapped for a pumpkin, given back after `seconds`. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `hits-required` | `1` | Hits on the same player it takes |
| `chance` | `50` | Percent chance; a miss spends it all the same |
| `seconds` | `10` | How long the pumpkin stays |
| `classes` | `[diamond]` | The classes it works on, by id; `[]` for anybody |

#### `disarm`

A hit: `chance`% that the weapon of the player hit swaps places with another item of theirs. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `hits-required` | `1` | Hits on the same player it takes |
| `chance` | `50` | Percent chance; a miss spends it all the same |

#### `scramble`

`hits-required` hits: the hotbar of the player hit is shuffled. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `hits-required` | `3` | Hits on the same player it takes, `global.hits-within-seconds` apart at most |

#### `starve`

`hits-required` hits: the hunger bar of the player hit drops to `food-left`. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `hits-required` | `3` | Hits on the same player it takes, `global.hits-within-seconds` apart at most |
| `food-left` | `6` | Hunger left, of 20 |

#### `grab`

A hit: the player hit is pulled towards you. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `hits-required` | `1` | Hits on the same player it takes |
| `pull` | `1.0` | How hard: stronger above 1, weaker below |
| `max-speed` | `4.0` | The fastest the pull carries a player, in blocks a tick |

#### `thorns`

A hit: for `seconds`, `reflect-percent`% of the damage that player deals you goes back to them. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `hits-required` | `1` | Hits on the same player it takes |
| `seconds` | `10` | How long it runs |
| `reflect-percent` | `30` | Percent of the damage, after armour, sent back through theirs |

#### `thrown-effects`

Thrown: the player it hits gets `effects`. Used by throwing it.

| Key | Default | What it does |
|---|---|---|
| `effects` | as shipped | Effects for the player hit |

#### `fake-pearl`

An ender pearl that flies as one and teleports nobody. Used by throwing it. It reads nothing of its own; its `cooldown-seconds` also greys out the fake pearls in the hotbar, a cooldown group of their own that leaves real pearls ready.

#### `grappling-hook`

A fishing rod: reeling in a hook stuck in a block pulls you to it. Used by reeling in.

| Key | Default | What it does |
|---|---|---|
| `pull` | `1.0` | How hard: stronger above 1, weaker below |
| `max-speed` | `4.0` | The fastest the pull carries a player, in blocks a tick |
| `no-fall-while-held` | `true` | No fall damage while it is in hand |

## Pocket Bard's sets

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml:pocket-bard-items"
```

| Key | As shipped | What it does |
|---|---|---|
| `menu-title` · `menu-size` | `&dPocket Bard Selection` · `9` | The menu; a size is 9 to 54, by nines |
| `items.<id>.material` · `name` · `lore` | | The items a pick gives |
| `items.<id>.amount` | `3` | How many |
| `items.<id>.effect` | | What a right-click with one gives |
| `items.<id>.radius` | `20` | Blocks around the user the effect reaches: the teammates there |
| `items.<id>.include-self` | `true` | The user gets it too |
| `items.<id>.cooldown-seconds` | `60` | Wait between two uses of this set's items, per player; each set apart. The shared cooldown holds as well |
| `items.<id>.slot` · `menu-name` | | The icon in the menu, from slot 0 |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/abilities.yml).

<div class="hcf-shipped" markdown>

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml"
```

</div>
