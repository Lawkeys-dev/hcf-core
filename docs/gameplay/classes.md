# Classes

*Configured in [`classes.yml`](../reference/configuration/classes.md). Command: `/class` (aliases `/classes`, `/pvpclass`).*

A class is chosen by what you **wear**: put on a class's whole armour set — helmet, chestplate, leggings and boots — and after a **10-second warmup** the class turns on. Take one piece off and it turns off at once.

| Class | Armour | In short |
|---|---|---|
| **Diamond** | diamond | The ordinary PvP set: no effects |
| **Bard** | gold | Supports the team: buffs teammates with held items, bursts with energy |
| **Archer** | leather | Fast; its arrows mark a target, who then takes more damage |
| **Rogue** | chainmail | Fast and agile; a golden sword hit from behind is a backstab |
| **Miner** | iron | For mining: haste, night vision, invisible deep underground |

Every number below is a setting, and you can [create your own classes](#creating-your-own) from scratch.

```text
/class               # your class, its energy, the warmup left
/class list          # every class and its armour
/class info bard     # everything a class does
/dyes                # the effects of each dye colour, in a menu
```

## How a class turns on

1. Put on the whole set. You are told *"the class turns on in 10s"*, and the scoreboard counts down (`%class_line%`).
2. When the warmup ends, the class is on: its effects apply, its items work.
3. Take any piece off — or swap to another set — and the class is off at once. A new set starts a new warmup.

A logout or a death drops the class; the next time you wear the set, the warmup starts again. Kits (`/kit`) that hand out a class's armour make the class turn on the same way.

!!! tip "Classes in the Citadel"
    Inside a [Citadel](events.md#what-the-citadel-refuses), pearls and partner items are refused but class abilities keep working: the Citadel is where classes decide the fight.

!!! info "Effects never cut a stronger one short"
    A class never replaces a stronger effect you already have: a Speed III potion is not cut down to a Bard's Speed II, and a weaker, longer effect comes back once a stronger one ends. When the class turns off, only the effects it gave are taken back.

## Diamond

Full diamond armour. No effects: the ordinary PvP set, listed so the scoreboard and `/class` name it.

??? example "Diamond — as shipped in `classes.yml`"

    ```yaml
    --8<-- "src/main/resources/classes.yml:diamond"
    ```

## Bard

Full **gold** armour. The Bard is the team's support.

**Always**: Speed II, Regeneration I, Resistance II.

**Holding an item** buffs every teammate within **20 blocks** — the Bard included — for as long as it stays in hand:

| Hold | Teammates get |
|---|---|
| Sugar | Speed II |
| Blaze Powder | Strength I |
| Iron Ingot | Resistance I |
| Ghast Tear | Regeneration I |
| Feather | Jump Boost II |
| Magma Cream | Fire Resistance I |

**Right-clicking the item** spends **energy** for a stronger burst, and uses up the item:

| Right-click | Effect | Reaches | Energy |
|---|---|---|---|
| Sugar | Speed III, 8 s | teammates | 20 |
| Blaze Powder | Strength II, 5 s | teammates | 45 |
| Iron Ingot | Resistance III, 5 s | teammates | 40 |
| Ghast Tear | Regeneration III, 5 s | teammates | 40 |
| Feather | Jump Boost VII, 5 s | teammates | 25 |
| Spider Eye | Wither II, 5 s | **enemies** | 35 |

**Energy** fills by **1 per second** up to **100** while the class is on, and starts at 0 each time it turns on. The scoreboard shows it (`%class_energy_line%`).

- Bard effects reach the Bard's **team only**, not allies — `targets: team-and-allies` changes that.
- The **Spider Eye** reaches only enemies the Bard could hit: not on a safe zone, not during SOTW, never a teammate, and an ally only in an event area — the same rules as a blow ([Combat](combat.md#friendly-fire)).
- Held and click effects do **not** work while the Bard stands in a safe zone (`abilities-in-safe-zones`).

??? example "Bard — as shipped in `classes.yml`"

    ```yaml
    --8<-- "src/main/resources/classes.yml:bard"
    ```

## Archer

Full **leather** armour. Fast and fragile.

**Always**: Speed III, Resistance II.

**Archer tag**: an arrow hit from an Archer **marks** the target for **10 seconds**. A marked player takes **25% more damage from everybody**, not only the Archer. A new hit starts the mark over. Both players are told, and the marked player's scoreboard shows the time left (`%archer_tag_line%`).

**Right-click Sugar**: Speed IV for 8 seconds, every 30 seconds.

### Dyed sets

Dye the whole leather set one colour and the Archer's arrows may add an effect to whoever they hit. As shipped:

| Colour | Chance per arrow hit | Effect |
|---|---|---|
| **Green** (the dye made from cactus) | 20% | Poison I, 10 s |
| **Black** (the ink sac) | 20% | Wither I, 10 s |
| **Blue** (lapis lazuli) | 20% | Slowness I, 10 s |

Every colour can have its own effect, chance and duration — `dye-effects` in `classes.yml`, with gray given as a commented example. **`/dyes`** (alias `/dyecolors`) opens a menu of every colour with an effect: its dye, the class, the effect and its duration, the chance per hit — and which one your set is right now. From the console, it prints the same list. `/class` also shows the colour your set reads as, and `/class info archer` lists them all.

- **All four pieces** must be dyed and read as the same colour. A piece dyed with several dyes counts as the dye its colour is closest to — one green and one lime dye still read as green, one green and two lime as lime.
- The effect follows the hit: an arrow refused by the rules of combat (safe zone, SOTW, a teammate) gives nothing.
- The archer tag and the dye effect are independent: a hit always tags, and may also poison.

**Dyeing keeps the enchantments.** Dye a piece the vanilla way — the piece and a dye in the inventory's crafting grid, or at a crafting table — and it keeps everything it carried: enchantments, custom enchants, name and lore. The game builds the dyed piece from the original, only changing its colour.

??? example "Archer — as shipped in `classes.yml`"

    ```yaml
    --8<-- "src/main/resources/classes.yml:archer"
    ```

## Rogue

Full **chainmail** armour. Fast, agile, and dangerous from behind.

**Always**: Speed III, Jump Boost II, Resistance I.

**Backstab**: a melee hit **from behind** with a **golden sword** deals **3 hearts through armour** — armour, enchantments and Resistance do not reduce it — and the sword breaks. Then **15 seconds** before the next one.

- *From behind* means standing on the victim's back side **and** facing roughly the way they face (within 60°): walking backwards into somebody's face is not a backstab, and neither is standing behind them looking sideways.
- A backstab follows the rules of any hit: it cannot land on a safe zone, during SOTW, or on a teammate.
- A backstab that would kill kills as an ordinary hit, credited to the Rogue — deathban, DTR and statistics follow as usual.

**Right-click**: Sugar gives Speed IV, Feather gives Jump Boost V — 8 seconds each, every 30 seconds.

??? example "Rogue — as shipped in `classes.yml`"

    ```yaml
    --8<-- "src/main/resources/classes.yml:rogue"
    ```

## Miner

Full **iron** armour. For mining, not fighting.

**Always**: Haste II, Night Vision, Fire Resistance.

**Invisible below Y 20**: deep underground, the Miner is invisible — handy for mining without being hunted.

??? example "Miner — as shipped in `classes.yml`"

    ```yaml
    --8<-- "src/main/resources/classes.yml:miner"
    ```

## Team limits

Each class can be limited per team — for example two Bards and three Archers — with `max-per-team`. **Every class ships with no limit** (`0`). A player whose team is full is told so when the warmup ends, and the class stays off until they take a piece off and try again.

## Creating your own

Every part of a class is optional except its armour, and **any class may use any part**. A class is a block of YAML in `classes.yml`:

```yaml title="classes.yml"
classes:
  assassin:
    display-name: "&5Assassin"
    permission: "myserver.class.assassin"   # empty for everybody
    max-per-team: 2
    armor:
      helmet: NETHERITE_HELMET
      chestplate: NETHERITE_CHESTPLATE
      leggings: NETHERITE_LEGGINGS
      boots: NETHERITE_BOOTS
    passive-effects:
      speed: 2
      invisibility: 1
    backstab:
      weapon: NETHERITE_SWORD
      damage: 8.0            # half-hearts: four hearts
      cooldown-seconds: 20
      break-weapon: false
      max-angle: 45
    click-effects:
      INK_SAC:
        effect: blindness
        level: 1
        seconds: 4
        radius: 8
        targets: enemies
        cooldown-seconds: 45
```

| Part | Does |
|---|---|
| `armor` | The four pieces that make the set (required) |
| `display-name`, `permission`, `max-per-team` | Its name; who may use it; how many per team |
| `passive-effects` | Effects kept for as long as the class is on |
| `energy` | `max` and `per-second`: what click effects spend |
| `held-effects` | An effect handed out while an item is held |
| `click-effects` | An effect handed out on a right-click, with an energy cost, a cooldown, and whether the item is used up |
| `archer-tag` | Arrow hits mark the target, who takes more damage |
| `backstab` | A hit from behind with a weapon deals fixed damage through armour |
| `invisible-below-y` | Invisible below a height |
| `dye-effects` | Leather sets: per dye colour, a chance for the class's arrows to give an effect |

**Targets** of held and click effects: `self` (the user alone), `team` (the user and teammates in range), `team-and-allies`, or `enemies` (everybody else in range the user could hit).

Every key, its default and an example of each part taken from the shipped file are in the [`classes.yml` reference](../reference/configuration/classes.md).

After editing, `/hcf reload` applies the file — to classes already on, too. An unknown item or effect is reported in the console and left out; a class whose armour is unknown, or whose set another class already uses, is skipped. The [shipped file](../reference/configuration/classes.md) documents every setting.
