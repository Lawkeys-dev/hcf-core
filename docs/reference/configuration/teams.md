# teams.yml

Teams: names, size, roles, invitations, alliances, focus, rally, the bank, and the Team Points scale.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/teams.md)

Every example on this page is **taken from the shipped `teams.yml`**. Changes apply with `/hcf reload`. Throughout the file, a limit of `0` means no limit. Teams have no `enabled` switch: the rest of the plugin is built on them.

## Names

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:names"
```

| Key | As shipped | What it does |
|---|---|---|
| `min-length`, `max-length` | `3`, `16` | Length of a team name |
| `pattern` | letters, digits, `_` | A regular expression the whole name must match. An invalid one is reported and the built-in one is used |
| `blacklist` | `spawn`, `warzone`, `wilderness`, `staff`, `admin` | Names players may not use, without case. Server teams are exempt |

## Members

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:membership"
```

| Key | As shipped | What it does |
|---|---|---|
| `max-members` | `20` | Members per team |
| `max-co-leaders` | `2` | Co-leaders at once |
| `invite-expiry-seconds` | `300` | How long an invitation lasts; `0` never expires |
| `disband-on-last-member-leave` | `true` | Disband a team whose last member leaves |
| `role-after-leadership-transfer` | `co-leader` | What the old leader becomes: `leader`, `co-leader` or `member` |

## Roles

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:required-roles"
```

The minimum role for each action: `leader`, `co-leader` or `member`. An unknown value falls back to `leader`, the safest, and is reported. The territory actions (claim, unclaim, set home, lock) are in [`claims.yml`](claims.md#roles).

## Alliances, focus and rally

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:alliances"
```

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:focus"
```

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:rally"
```

| Key | As shipped | What it does |
|---|---|---|
| `alliances.enabled` | `true` | Alliances at all |
| `alliances.max-allies` | `1` | Allied teams per team, checked on both sides |
| `focus.enabled` | `true` | Focus at all |
| `focus.max-targets` | `0` | Players and teams a team may focus at once |
| `rally.enabled` | `true` | Rally points at all |
| `rally.duration-seconds` | `300` | How long a rally point lasts; `0` until cleared |

## Bank

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:bank"
```

| Key | As shipped | What it does |
|---|---|---|
| `bank.enabled` | `true` | Deposits and withdrawals. Turning the economy off disables them too |

## Points

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:points"
```

| Key | As shipped | What it does |
|---|---|---|
| `starting` | `0` | Points a new team starts with |
| `minimum` | `0` | The floor points never go below |
| `per-kill` | `0` | To the killer's team, for killing a player of another team or of none |
| `per-death` | `0` | To the victim's team, for any death — write a negative number to take points |
| `per-raidable` | `0` | To a team its DTR just made raidable |
| `per-conquest-win` | `0` | To the team that wins a Conquest |
| `per-king-win` | `0` | To the team of the player who wins Kill the King |
| `per-dtc-win` | `0` | To the team that wins a DTC |
| `per-last-break-win` | `0` | To the team that wins a Last Break |
| `per-slide-win` | `0` | To the team that wins a Slide |

## KOTH captures

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:koth"
```

| Key | As shipped | What it does |
|---|---|---|
| `max-counted-captures` | `0` | How many KOTH and Citadel captures count for a team's ranking; `/team resetkoth` resets the counts |
| `points-per-capture` | `0` | Points a capture is worth |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/teams.yml).

<div class="hcf-shipped" markdown>

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml"
```

</div>
