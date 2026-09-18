# Kits and abilities

*Configured in [`kits.yml`](../reference/configuration/kits.md) and [`killstreaks.yml`](../reference/configuration/killstreaks.md). Command: `/kit` (alias `/kits`).*

## Kits

**A kit is made in game, not in YAML.** Put the loadout on — armour, enchanted items, potions, named items — and save it:

```text
/kit create diamond 3600 myserver.kit.diamond
```

`/kit create <id> [cooldown-seconds] [permission]` stores your inventory as it is. A permission, if given, is then needed to take the kit; any node works.

| Command | Does |
|---|---|
| `/kit` | List the kits you can take |
| `/kit <id>` | Take one |
| `/kit layout <id> [reset]` | Arrange a kit's items (see below) |
| `/kit create <id> [cooldown] [permission]` | Save your inventory as a kit — staff |
| `/kit delete <id>` | Delete a kit and its cooldowns — staff |
| `/kit give <player> <kit>` | Give a kit, starting no cooldown — staff |
| `/kit resetcooldown <player> [kit]` | Clear cooldowns — staff |
| `/kit ability <player> <ability> [amount]` | Give ability items — staff |

Staff commands need `hcfcore.kit.admin`.

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

The sign has its own anti-spam wait (`refill-signs.cooldown-seconds`, 3), **separate from the kit's cooldown** — a kitmap kit usually has none, and the sign still must not be clickable sixty times a second. The word in brackets is `refill-signs.line`, so it can be translated.

## Abilities (partner items)

An ability is an item that **runs console commands when right-clicked**, with a cooldown. It is the one shape that can express any partner item — the plugin does not need to know what yours do. **The list ships empty.**

```yaml title="kits.yml"
abilities:
  switcher:
    material: ENDER_PEARL
    name: "&5&lSwitcher"
    lore:
      - "&7Swaps places with whoever you hit."
    cooldown-seconds: 15
    consume: true          # take one from the stack when used
    commands:
      - "some-command %player%"
```

- Hand them out with `/kit ability <player> <id> [amount]`, a killstreak reward, a redeem code, or any plugin that can run a command.
- An ability with no command is refused when the file loads — an item that looks like a tool and does nothing is worse than none.
- Cooldowns live in memory: they stop spam in a fight, and a fight does not survive a restart.
- Lunar Client players see ability cooldowns as icons.

## Killstreaks

A **streak** counts a player's kills since their last death. It is stored, so it survives restarts and cannot be farmed by waiting for one. `/stats` shows the current and best streak.

Rewards fire at **exactly** their streak — a player reaching 10 was already given 3 and 5 on the way up. They are console commands, with an optional broadcast; `%player%` and `%streak%` are filled in. **The table ships empty:**

```yaml title="killstreaks.yml"
rewards:
  5:
    broadcast: "&c%player% &7is on a &f%streak% &7killstreak!"
    commands:
      - "kit give %player% streak5"
  10:
    broadcast: "&4%player% &7is on &f%streak%&7! Somebody stop them."
    commands:
      - "kit ability %player% switcher 1"
```
