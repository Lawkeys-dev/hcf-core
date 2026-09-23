# economy.yml

The economy: starting balance, ceiling, currency, and `/pay`.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/economy.md)

Every example on this page is **taken from the shipped `economy.yml`**. Changes apply with `/hcf reload`.

## Balances

```yaml title="economy.yml"
--8<-- "src/main/resources/economy.yml:general"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Balances, `/pay`, `/eco` and the team bank commands; Vault reports the economy disabled when off |
| `starting-balance` | `100.0` | What a player starts with |
| `maximum-balance` | `0.0` | A ceiling per account; `0` for none. A credit over it is refused, never clamped |

## Currency

```yaml title="economy.yml"
--8<-- "src/main/resources/economy.yml:currency"
```

| Key | As shipped | What it does |
|---|---|---|
| `symbol` | `$` | Shown before an amount: `$1,234.50` |
| `singular`, `plural` | `dollar`, `dollars` | Used where an amount is named |

## /pay

```yaml title="economy.yml"
--8<-- "src/main/resources/economy.yml:pay"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | `/pay`, on its own |
| `minimum-amount` | `1.0` | The smallest transfer |

## Shop

```yaml title="economy.yml"
--8<-- "src/main/resources/economy.yml:shop"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | The shop at all |
| `mode` | `both` | `signs` (placed by staff), `menu` (`/shop`), or `both` |
| `signs.buy-header` · `sell-header` | `[Buy]` · `[Sell]` | The first line that makes a sign a shop sign |
| `menu.items` | ores sold, pearls and XP bought | One entry per item: `material`, `amount` per trade, `buy` and `sell` prices for that amount — `0` for not offered |

## Bounties

```yaml title="economy.yml"
--8<-- "src/main/resources/economy.yml:bounties"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | `/bounty` at all |
| `minimum-amount` | `100.0` | The least one placement may add |
| `announce` | `true` | Tell everybody when a bounty is placed; collecting one is always announced |
| `list-size` | `10` | How many bounties `/bounty` lists |

## Kill reward

```yaml title="economy.yml"
--8<-- "src/main/resources/economy.yml:kill-reward"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Money for a kill at all |
| `amount` | `50.0` | Paid to the killer, from nowhere |
| `steal-percent` | `0` | The share of the victim's balance moved to the killer, 0 to 100, rounded down to the cent |
| `same-victim-cooldown-seconds` | `300` | The same killer earns nothing for the same victim again within this |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/economy.yml).

<div class="hcf-shipped" markdown>

```yaml title="economy.yml"
--8<-- "src/main/resources/economy.yml"
```

</div>
