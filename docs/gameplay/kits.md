# Kits and killstreaks

*Configured in [`kits.yml`](../reference/configuration/kits.md) and [`killstreaks.yml`](../reference/configuration/killstreaks.md). Command: `/kit` (alias `/kits`).*

## Kits

**A kit is made in game, not in YAML.** Put the loadout on — armour, enchanted items, potions, named items — and save it:

```text
/kit create diamond 3600 myserver.kit.diamond
```

`/kit create <id> [cooldown-seconds] [permission]` stores your inventory as it is. A permission, if given, is then needed to take the kit; any node works.

`/kit fromchest <id>` stores the chest you look at instead — or, from the console too, `/kit fromchest <id> <x> <y> <z> [world]`. The armour in it is put on (the first helmet, chestplate, leggings and boots), everything else fills the inventory in the chest's order. Such a kit has no cooldown and no permission: re-create it with `/kit create` for those.

| Command | Does |
|---|---|
| `/kit` | List the kits you can take |
| `/kit <id>` | Take one |
| `/kit layout <id> [reset]` | Arrange a kit's items (see below) |
| `/kit create <id> [cooldown] [permission]` | Save your inventory as a kit — staff |
| `/kit fromchest <id> [<x> <y> <z> [world]]` | Save a chest as a kit, its armour worn — staff, console too |
| `/kit delete <id>` | Delete a kit and its cooldowns — staff |
| `/kit give <player> <kit>` | Give a kit, starting no cooldown — staff |
| `/kit resetcooldown <player> [kit]` | Clear cooldowns — staff |

Staff commands need `hcfcore.kit.admin`.

- **A kit can make a class**: a kit holding a class's whole armour set turns the [class](classes.md) on once worn, after its warmup.
- **Cooldowns run while the server is off**, like a deathban: a daily kit would be worthless on a server that restarts every night.
- **`clear-before-giving: true`** empties the inventory first — the kitmap behaviour, you get exactly the kit. `false` adds the kit to what you carry and drops the overflow at your feet rather than eating it.

## Layout editor

`/kit layout <kit>` opens the kit as your inventory — storage rows, hotbar, off-hand. Put each item where you want it and close the window to save. From then on the kit is handed to you in that order, by `/kit` and by refill signs alike. Armour stays worn.

- `/kit layout <kit> reset` goes back to the kit's own order.
- A layout is kept per player and per kit, and is dropped when staff re-create the kit with other items.
- **Nothing is lost and nothing is duplicated**: the window shows marked copies that cannot leave it, and every item gets a slot whatever the layout says.

`layout-editor: false` turns it off.

## Refill signs

The kitmap standard: click a sign, get a kit.

```text
[Kit]
diamond
```

A sign whose first line is `[Kit]` and second line the kit id hands it out on right-click. Creating one needs `hcfcore.kit.sign`. Signs work anywhere, spawn and other server land included.

A sign whose first line is **`[Refill]`** opens its kit's items in a **self-service window** instead — potions, pearls, a Rogue's golden swords for a player back at spawn. Take what you need: the window never runs dry, and nothing is cleared. Nothing can be put into it, and nothing thrown out of it. Make a kit of the refill items (`/kit fromchest refill`, say) and write `[Refill]` / `refill`.

The sign has its own anti-spam wait (`refill-signs.cooldown-seconds`, 3), **separate from the kit's cooldown** — a kitmap kit usually has none, and the sign still must not be clickable sixty times a second. The word in brackets is `refill-signs.line`, so it can be translated.

**A sign asks before it wipes your inventory.** When a kit replaces the whole inventory (`clear-before-giving`, as shipped) and you hold anything, the first click only warns you; click the same sign again within 5 seconds to take the kit (`refill-signs.confirm-seconds`, `0` never asks). An empty inventory gets the kit at the first click.

Partner items — abilities — have their own page: [Abilities](abilities.md).

## Killstreaks

A **streak** counts a player's kills since their last death. It is stored, so it survives restarts and cannot be farmed by waiting for one. `/stats` shows the current and best streak.

Rewards fire at **exactly** their streak — a player reaching 10 was already given 3 and 5 on the way up. They are console commands, with an optional broadcast; `%player%` and `%streak%` are filled in. **The table ships empty:**

```yaml title="killstreaks.yml"
--8<-- "src/main/resources/killstreaks.yml:rewards"
```
