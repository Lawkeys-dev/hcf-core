# classes.yml

Classes: Diamond, Bard, Archer, Rogue and Miner as shipped, and any class you write — armour set, passive effects, energy, held and right-click effects, archer tag, backstab, dyed sets, invisibility.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/classes.md)

Every example on this page is **taken from the shipped `classes.yml`** — the file the plugin writes on its first start — so it is exactly what your server gets. Changes apply with `/hcf reload`, to classes already on too.

## General settings

```yaml title="classes.yml"
--8<-- "src/main/resources/classes.yml:settings"
```

| Key | Type | Default | What it does |
|---|---|---|---|
| `enabled` | true / false | `true` | Turns every class off: sets still worn give nothing, `/class` and `/dyes` say classes are off |
| `warmup-seconds` | whole seconds | `10` | How long a whole set must be worn before its class turns on. `0` turns it on at once |
| `abilities-in-safe-zones` | true / false | `false` | Whether held and right-click effects work while their user stands in a safe zone (spawn). Passive effects always do |

## A class

Every class sits under `classes:`, with an **id** — 1 to 32 lower-case letters, digits, `-` or `_` — used by `/class info <id>`. Only `armor` is required; **every other part is optional, and any class may use any part**.

| Key | Type | Default | What it does |
|---|---|---|---|
| `display-name` | text, `&` colours | the id | The name players see |
| `permission` | permission node | none | Needed to use the class; without it, wearing the set gives no class. Any node works |
| `max-per-team` | whole number | `0` | How many members of one team may be in the class at once. `0` is no limit |
| `armor` | four items | — | The set, see below. **Required** |
| `passive-effects` | effects | none | [Passive effects](#passive-effects) |
| `energy` | section | none | [Energy](#energy) |
| `held-effects` | items | none | [Held effects](#held-effects) |
| `click-effects` | items | none | [Right-click effects](#right-click-effects) |
| `archer-tag` | section | none | [Archer tag](#archer-tag) |
| `dye-effects` | colours | none | [Dyed sets](#dyed-sets) |
| `backstab` | section | none | [Backstab](#backstab) |
| `invisible-below-y` | height | none | [Invisible below a height](#invisible-below-a-height) |

A whole class, the Rogue as shipped:

```yaml title="classes.yml — the Rogue"
--8<-- "src/main/resources/classes.yml:rogue"
```

### Armour

```yaml title="classes.yml — the Bard's set"
--8<-- "src/main/resources/classes.yml:bard-armor"
```

| Key | Type | What it is |
|---|---|---|
| `helmet`, `chestplate`, `leggings`, `boots` | item name | The four pieces, as the server names items (`GOLDEN_HELMET`, `minecraft:golden_helmet` works too) |

- All four pieces make the set; a player wearing three of them has no class.
- **Two classes cannot share a set**: the first one in the file wins, and the second is reported in the console and skipped.
- An unknown item skips the whole class, with a message in the console.
- Any item that goes in an armour slot works — a netherite set makes a class as well as any other.

### Passive effects

Kept on the player for as long as the class is on.

```yaml title="classes.yml — the Bard's passive effects"
--8<-- "src/main/resources/classes.yml:bard-passive"
```

Each line is `effect: level` — `speed: 2` is Speed II. The effect names are the server's: `speed`, `slowness`, `haste`, `strength`, `jump_boost`, `regeneration`, `resistance`, `fire_resistance`, `water_breathing`, `invisibility`, `night_vision`, `saturation`, `absorption`, `health_boost`, `weakness`, `poison`, `wither`, `blindness`... An unknown name is reported and left out; the class stays.

A class never replaces a stronger effect the player already has — a Speed III potion is not cut down to a Speed II passive — and when the class turns off, only the effects it gave are taken back.

### Energy

Fills while the class is on; [right-click effects](#right-click-effects) spend it.

```yaml title="classes.yml — the Bard's energy"
--8<-- "src/main/resources/classes.yml:bard-energy"
```

| Key | Type | Default | What it does |
|---|---|---|---|
| `max` | number | `100` | The most it holds |
| `per-second` | number | `1.0` | How much it gains each second |

Energy starts at 0 each time the class turns on. The scoreboard shows it (`%class_energy_line%`), and `/class` too.

### Held effects

An effect handed out **while an item is held in the main hand**, by item.

```yaml title="classes.yml — the Bard's held effects"
--8<-- "src/main/resources/classes.yml:bard-held"
```

| Key | Type | Default | What it does |
|---|---|---|---|
| *(the section's name)* | item name | — | The item to hold: `SUGAR`, `BLAZE_POWDER`... |
| `effect` | effect name | — | **Required.** The effect handed out |
| `level` | 1 to 255 | `1` | Its level |
| `seconds` | seconds | `5` | How long each application lasts. It is renewed **every second** while the item is held, so the effect is continuous — and lasts this long after the item is put away or a player leaves the range. Keep it at 2 or more |
| `radius` | blocks | `20` | How far it reaches (unused with `targets: self`) |
| `targets` | see [targets](#targets) | `team` | Who it reaches |

### Right-click effects

An effect handed out **once, on a right-click** with an item.

```yaml title="classes.yml — the Bard's right-click effects"
--8<-- "src/main/resources/classes.yml:bard-click"
```

| Key | Type | Default | What it does |
|---|---|---|---|
| *(the section's name)* | item name | — | The item to right-click |
| `effect` | effect name | — | **Required.** The effect handed out |
| `level` | 1 to 255 | `1` | Its level |
| `seconds` | seconds | `8` | How long it lasts |
| `radius` | blocks | `20` | How far it reaches (unused with `targets: self`) |
| `targets` | see [targets](#targets) | `self` | Who it reaches |
| `energy` | whole number | `0` | Energy spent. Needs an `energy:` section on the class, otherwise it is ignored with a warning |
| `cooldown-seconds` | seconds | `0` | Wait before the item can be used again |
| `consume` | true / false | `true` | Whether one item is taken from the stack |

A right-click refused — not enough energy, a cooldown, a safe zone — does nothing else: the item is not used up, nor eaten. A self-only click, the Archer's:

```yaml title="classes.yml — the Archer's right-click effect"
--8<-- "src/main/resources/classes.yml:archer-click"
```

### Targets

| `targets` | Reaches |
|---|---|
| `self` | The user alone |
| `team` | The user and their teammates in range; the user alone without a team |
| `team-and-allies` | The same, plus members of allied teams |
| `enemies` | Everybody else in range **whom the user could hit** — not on a safe zone, not during SOTW, never a teammate, an ally only in an event area — and who is in survival or adventure mode and visible to the user |

### Archer tag

A player hit by the class's arrows is **marked**, and takes more damage from everybody while the mark lasts.

```yaml title="classes.yml — the Archer's tag"
--8<-- "src/main/resources/classes.yml:archer-tag"
```

| Key | Type | Default | What it does |
|---|---|---|---|
| `seconds` | seconds | `10` | How long the mark lasts; a new hit starts it over |
| `damage-multiplier` | number, 1.0 or more | `1.25` | What the damage the marked player takes is multiplied by — `1.25` is 25% more, `1.5` is 50% more |

### Dyed sets

For a **leather** set: while all four pieces are dyed one colour, each arrow hit from the class has a chance to give the target an effect.

```yaml title="classes.yml — the Archer's dyed sets"
--8<-- "src/main/resources/classes.yml:archer-dyes"
```

| Key | Type | Default | What it does |
|---|---|---|---|
| *(the section's name)* | dye colour | — | `WHITE`, `LIGHT_GRAY`, `GRAY`, `BLACK`, `BROWN`, `RED`, `ORANGE`, `YELLOW`, `LIME`, `GREEN`, `CYAN`, `LIGHT_BLUE`, `BLUE`, `PURPLE`, `MAGENTA`, `PINK` |
| `effect` | effect name | — | **Required.** Given to the player hit |
| `level` | 1 to 255 | `1` | Its level |
| `seconds` | seconds | `10` | How long it lasts |
| `chance` | 0 to 100 | `0` | Percent chance per arrow hit |

Each colour is the game's own dye colour. A piece dyed with one dye has exactly that colour; a piece dyed with several counts as the dye its colour is closest to. `/dyes` shows the effects in a menu. A set that is not all leather cannot be dyed, and the console says so.

### Backstab

A melee hit **from behind** with a given weapon deals fixed damage that armour, enchantments and Resistance do not reduce.

```yaml title="classes.yml — the Rogue's backstab"
--8<-- "src/main/resources/classes.yml:rogue-backstab"
```

| Key | Type | Default | What it does |
|---|---|---|---|
| `weapon` | item name | — | **Required.** The item the hit must be dealt with |
| `damage` | number | `6.0` | Health taken, in half-hearts — `6.0` is three hearts |
| `cooldown-seconds` | seconds | `15` | Wait before the next backstab |
| `break-weapon` | true / false | `true` | Whether the weapon breaks on a backstab |
| `max-angle` | 0 to 180 degrees | `60` | How far the attacker may face away from the way the victim faces and still count as behind |

*Behind* means on the victim's back side **and** facing roughly the same way. A backstab that would kill is dealt as an ordinary, overwhelming hit, so the kill is the attacker's — deathban, DTR and statistics follow as usual.

### Invisible below a height

```yaml title="classes.yml — the Miner's invisibility"
--8<-- "src/main/resources/classes.yml:miner-invisible"
```

The player is invisible while below this height (Y). Any whole number, negative ones included.

## The shipped classes

??? example "Diamond"

    ```yaml
    --8<-- "src/main/resources/classes.yml:diamond"
    ```

??? example "Bard"

    ```yaml
    --8<-- "src/main/resources/classes.yml:bard"
    ```

??? example "Archer"

    ```yaml
    --8<-- "src/main/resources/classes.yml:archer"
    ```

??? example "Rogue"

    ```yaml
    --8<-- "src/main/resources/classes.yml:rogue"
    ```

??? example "Miner"

    ```yaml
    --8<-- "src/main/resources/classes.yml:miner"
    ```

## What is checked when the file loads

Nothing in the file can stop the server. What cannot be used is reported in the console and replaced or left out:

| Problem | What happens |
|---|---|
| An unknown armour piece | The class is skipped |
| Two classes on the same set | The second is skipped |
| An invalid id | The class is skipped |
| An unknown effect, item or colour | That line is left out; the class stays |
| An energy cost on a class without `energy` | The cost is ignored |
| A value of the wrong kind (`seconds: ten`) | Its default is used |
| A `*-seconds` value over a hundred years | Capped |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/classes.yml).

<div class="hcf-shipped" markdown>

```yaml title="classes.yml"
--8<-- "src/main/resources/classes.yml"
```

</div>
