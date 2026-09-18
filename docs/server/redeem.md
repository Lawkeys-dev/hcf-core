# Redeem codes

*Configured in [`redeem.yml`](../reference/configuration/redeem.md). Commands: `/redeem`, `/redeemadmin`, `/resetredeem`.*

Codes for giveaways, promotions and stores. A code's reward is a list of **console commands** — an item, money, a rank, a kit, lives, from whichever plugin provides it — so codes work with any store or plugin.

## For players

```text
/redeem SUMMER
```

Every player can redeem a given code **once**. There is no name completion, which would list the codes.

After a failed attempt — no such code, already used, all used up — a player waits **3 seconds** before the next one (`failed-attempt-cooldown-seconds`), so codes cannot be found by trying names as fast as chat allows.

## For staff

Codes are made in game and stored; they are not written in any file. All of these need `hcfcore.redeem.admin`.

```text
/redeemadmin create SUMMER 100 eco give %player% 500
/redeemadmin addcommand SUMMER lives give %player% 1
```

| Command | Does |
|---|---|
| `/redeemadmin create <code> <max-uses\|unlimited> <command>` | A new code with its first reward |
| `/redeemadmin addcommand <code> <command>` | Another reward |
| `/redeemadmin removecommand <code> <number>` | Remove a reward — numbers come from `info` |
| `/redeemadmin setuses <code> <max-uses\|unlimited>` | Change how many players can redeem it |
| `/redeemadmin info <code>` | Its rewards and uses |
| `/redeemadmin list` | Every code |
| `/redeemadmin delete <code>` | Delete it |
| `/resetredeem <code> [player]` | Let one player, or everybody, use it again |

`/redeemadmin` has the alias `/redeemcodes`. `%player%` in a reward is the player redeeming.

## Single-use or giveaway

`max-uses` caps **how many players** can redeem the code in all:

- `1` — a single-use code, for one person;
- `100` — the first hundred players;
- `unlimited` — an open giveaway.

Each player still redeems it only once. A code with no reward answers "not ready" rather than being used up for nothing.
