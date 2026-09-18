# Enchants, limits and the crowbar

*Configured in [`enchants.yml`](../reference/configuration/enchants.md), [`limiters.yml`](../reference/configuration/limiters.md) and [`crowbar.yml`](../reference/configuration/crowbar.md).*

## Custom enchants

Enchantments that do not exist in vanilla — the well-known HCF and kitmap ones. Nine ship configured; every one can be renamed, re-levelled, moved to other items, removed, or copied under a new id.

| Enchant | Type | Max | On | Does |
|---|---|---|---|---|
| Fire Resistance | `effect` | I | armour | Fire Resistance while worn |
| Speed | `effect` | II | boots | Speed while worn |
| Jump Boost | `effect` | II | boots | Jump Boost while worn |
| Night Vision | `effect` | I | helmet | Night Vision while worn |
| Invisibility | `effect` | I | chestplate | Invisibility while worn |
| Hellforged | `hellforged` | IV | armour | Repairs the piece each time its wearer takes damage |
| Implanted | `implanted` | III | helmet | Keeps its wearer fed |
| Recover | `recover` | II | chestplate | Regeneration when health falls low, then a cooldown |
| Autosmelt | `autosmelt` | I | pickaxe | Smelts what it mines, exactly as a furnace would |

- **Five behaviours**, each a `type`: `effect` (any potion effect — one more effect enchant is one more block of YAML), `hellforged`, `implanted`, `recover` and `autosmelt`. Autosmelt follows the server's own furnace recipes, datapacks included.
- **An effect enchant never overrides a stronger effect from elsewhere**: a Speed II potion is not cut down to a Speed I boot.
- **The enchant lives on the item** — its level, a lore line and the glint — so kits, chests, deaths and trades carry it like the rest of the item.
- Lowering a `max-level` lowers items already made.

### Getting one

| Command | Does |
|---|---|
| `/cenchant list` | What exists — everyone (aliases `/ce`, `/customenchant`) |
| `/cenchant apply <enchant> [level]` | On the held item — staff |
| `/cenchant remove <enchant>` | From the held item — staff |
| `/cenchant give <player> <enchant> [level] [amount]` | Books — staff, and from the console |

A **book is dragged onto an item** in the inventory, with the anvil's rule: a higher book sets its level, an equal one raises it by one, up to the maximum. `/cenchant give` from the console is how a shop, a crate or a redeem code hands books out. Staff commands need `hcfcore.enchant.admin`.

## Enchantment, potion and effect caps

Which levels a map allows — Protection I or II, Sharpness I or II, no Strength II — is **the** defining choice of an HCF map. **Both lists ship empty.**

```yaml title="limiters.yml"
--8<-- "src/main/resources/limiters.yml:enchantments"
```

```yaml title="limiters.yml"
--8<-- "src/main/resources/limiters.yml:potions"
```

**Enchantments** are capped:

- **at the enchanting table** — the offers shown are corrected, and an enchantment with nothing left is refused rather than charged;
- **at the anvil** — a result that brings nothing is removed, so nobody pays for a forbidden book;
- **on everything else** (`fix-existing-items`, on by default) — loot, villager trades, fishing, other plugins — when the holder joins, closes an inventory, or fights. This only ever lowers. The very first hit with such an item can still land at its old level: a hit's damage is worked out before it can be seen.

**A cap can be above vanilla's maximum.** The anvil then combines past it with vanilla's rule — two equal levels make one higher — so two Sharpness V make Sharpness VI when sharpness is capped at 6. Nothing else in the game creates such levels.

**Potions** are capped when the effect comes from a potion — drunk, splashed, lingering, a tipped arrow. An effect above its cap is **brought down**, not refused: Strength II drinks as Strength I when strength is capped at 1. Golden apples, beacons, commands and other plugins are left alone.

**A potion capped at `0` is forbidden**:

- it is **not brewed**: the brewing stand leaves that bottle as it was (the ingredient is used up, as for the other bottles);
- **drinking or throwing one is refused**, and the potion stays in hand; shooting a tipped arrow of it is refused. The player is told why;
- one that still reaches a player — a dispenser, a potion from before — gives nothing.

!!! tip "Strength from the Bard only"
    The classic HCF rule: `strength: 0` under `caps`. **The classes are not potions**: a Bard still gives Strength to the team, and a class's own effects are untouched. See [Classes](classes.md).

### Effect caps

```yaml title="limiters.yml"
--8<-- "src/main/resources/limiters.yml:effects"
```

**An effect cap holds whatever gives the effect**: a potion, a class — a Bard's burst, a Diamond's passive —, a custom enchant, the King, a golden apple, a beacon, a command, another plugin. `resistance: 3` and **no player ever has more than Resistance III**. An effect above its cap is brought down to it, keeping its duration; `0` forbids the effect entirely.

| | `potions.caps` | `effects.caps` |
|---|---|---|
| What it caps | The effect **when a potion gives it** | The effect, **whatever gives it** |
| A Bard's Strength II, with `strength: 1` | Untouched | Strength I |
| A Strength II potion, with `strength: 1` | Strength I | Strength I |
| `0` | The potion is not brewed, nor drunk, thrown or shot | Nobody has the effect |

For a potion, the lower of the two caps holds. The classes, custom enchants and the King give their effects already within the cap — a Bard wanting Resistance IV gives Resistance III — and still take back only what they gave. A cap set with `/hcf reload` holds at once on the effects players already have, and on those a player brings back when joining.

## Blocks per claim

The most of a block a team's whole territory may hold — against lag machines and abuse. Placing one more there is refused. **Ships empty:**

```yaml title="limiters.yml"
--8<-- "src/main/resources/limiters.yml:claim-blocks"
```

- `/team limits` (alias `blocks`) shows your team's usage.
- A limit of `0` forbids the block in claims. Only player teams' land counts.
- Counts are kept per chunk as blocks are placed, broken, burnt and blown up, and each claimed chunk is **recounted exactly** in the background the first time it loads after a start — which puts right what no event reports (redstone washed away, blocks changed by another plugin).
- Staff get past the limits only while holding `hcfcore.limiter.bypass` **and** with `/staffbuild` on.

## Crowbar

A tool that takes a placed **End portal frame** out — the block survival cannot break, and a common part of base traps. Right-click a frame with it; the frame drops as an item, and its eye too if it had one.

```text
/crowbar give <player> [amount]      # hcfcore.crowbar.admin
```

**Where**: in the wilderness, or in your own team's claim. **Never** in another team's claim — raidable or not — nor on server land or in the warzone. The crowbar is there so a team can adjust its own traps, not so a raid can defuse somebody else's.

- The removal is then offered to every other protection as an ordinary block break, so a Mountain or another plugin's region still has its say.
- **Ships neutral**: unlimited uses (`uses`), no cooldown (`cooldown-seconds`), no cost (`cost`, taken from the economy). Nothing is taken for a refused use.
- The item, its name and its lore are configurable; `%uses%` in the lore shows the uses left.
- Frames only — no spawners. Taking a frame out of an already active portal leaves the portal blocks in place, as in vanilla.
