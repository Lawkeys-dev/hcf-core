# Economy

*Configured in [`economy.yml`](../reference/configuration/economy.md). Commands: `/balance`, `/pay`, `/eco`.*

## Balances

Every player has a balance, starting at **100** (`starting-balance`). A player who never earns costs no storage: nothing is written until their balance changes.

```text
/balance              # yours (aliases /bal, /money)
/balance <player>     # someone else's
/pay <player> 250     # send money
```

- `/pay` sends **1 at least** (`pay.minimum-amount`), and can be turned off on its own (`pay.enabled`).
- `maximum-balance` caps an account (`0` = no ceiling). A credit that would cross it is **refused rather than clamped**, so money is never silently destroyed.
- Amounts are shown with the currency symbol — `$1,234.50` — or named, `1 dollar`, `2 dollars` (`currency`).

## Team banks

A team has a balance of its own: `/team deposit <amount>` (every member) and `/team withdraw <amount>` (the leader). See [Teams](teams.md#bank).

## Staff

```text
/eco give <player> <amount>
/eco take <player> <amount>
/eco set <player> <amount>
```

Aliases `/economy`; needs `hcfcore.economy.admin`. The player is told about the change — a balance that changes silently reads as a bug.

## Safety

Every change is atomic: two withdrawals at the same moment cannot spend the same money twice, and the total amount of money is conserved — checked by load tests with dozens of threads.

Balances are stored as decimal numbers (`double`), the type Vault's own interface uses: anything else would mean converting on every call and losing the same precision at the boundary.

## Vault

With Vault installed, shops, crates, paid ranks and any Vault-aware plugin read and change the same balances as `/balance` and `/pay` — with exactly the same ceiling and refusals. Team banks are not exposed to Vault, and there are no per-world balances. See [Integrations](../server/integrations.md#vault).

## Switching it off

`enabled: false` disables balances, `/pay`, `/eco` and the team bank commands, and Vault then reports the economy as disabled.
