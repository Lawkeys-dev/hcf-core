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
| `material` | — | The item. A thrown type is a `SNOWBALL` or an `EGG`; `portable-archer` a `BOW` |
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
| `hits-required` | `3` | Hits on the same player it takes, 10 s apart at most |
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

Teleports, after `delay-seconds`, to the last player who hit you. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `delay-seconds` | `3` | Seconds before the teleport |
| `hit-within-seconds` | `15` | How recent the hit must be |

#### `anti-build`

`hits-required` hits with it: they cannot build, break or open `blocked-blocks` for `seconds`. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `hits-required` | `3` | Hits on the same player it takes, 10 s apart at most |
| `seconds` | `15` | How long it runs |
| `blocked-blocks` | as shipped | Blocks that may not be opened: a block's name, or a family's end (`FENCE_GATE`) |
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

It reads nothing of its own: its sets are the `pocket-bard` section.

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

#### `teleport-eye`

In water only: teleports, after `delay-seconds`, to the last player who hit you. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `delay-seconds` | `3` | Seconds before the teleport |
| `hit-within-seconds` | `15` | How recent the hit must be |

#### `samurai`

Teleports to the last player who hit you, puts them under anti-build and a pearl cooldown; `effects` for you. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `delay-seconds` | `3` | Seconds before the teleport |
| `hit-within-seconds` | `15` | How recent the hit must be |
| `anti-build-seconds` | `15` | Anti-build on the target |
| `ender-pearl-cooldown-seconds` | `16` | Pearl cooldown on the target |
| `effects` | as shipped | The effects |

#### `magic-rock`

A hit with it: `effects-by-space`, by how many free blocks are above the player's head. Used by hitting a player with it.

| Key | Default | What it does |
|---|---|---|
| `effects-by-space` | as shipped | Free blocks above the head → effects |

#### `belch-bomb`

`effects` for every enemy within `radius`. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `radius` | `8` | Blocks around where it reaches |
| `effects` | as shipped | The effects |

#### `anti-trap-star`

Teleports, after `delay-seconds`, to the last player who hit you with a projectile. Used by a right-click.

| Key | Default | What it does |
|---|---|---|
| `delay-seconds` | `3` | Seconds before the teleport |
| `hit-within-seconds` | `15` | How recent the hit must be |

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
| `items.<id>.slot` · `menu-name` | | The icon in the menu, from slot 0 |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/abilities.yml).

<div class="hcf-shipped" markdown>

```yaml title="abilities.yml"
--8<-- "src/main/resources/abilities.yml"
```

</div>
