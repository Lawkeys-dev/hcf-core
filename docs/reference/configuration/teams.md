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

## Looking for a team

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:lff"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | `/lff` at all |
| `cooldown-seconds` | `300` | Before the same player can use it again |
| `max-note-length` | `64` | The longest note kept, 0 to 200 characters; colour codes are stripped |

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
| `per-kill` | `1` | To the killer's team, for killing a player of another team or of none |
| `per-death` | `-2` | To the victim's team, for any death of a member |
| `raidable-loss-percent` | `50` | The share of its points a team loses when its DTR makes it raidable, 0 to 100 |
| `per-raidable` | `0` | The same as a fixed number (negative), used when the share is `0` |
| `per-citadel-capture` | `300` | To the team that captures a Citadel |
| `per-conquest-win` | `250` | To the team that wins a Conquest |
| `per-dtc-win` · `per-slide-win` | `200` | To the team that wins a DTC, a Slide |
| `per-king-win` | `150` | To the team of the player who wins Kill the King |
| `per-last-break-win` | `150` | To the team that wins a Last Break |
| `per-totem-win` | `150` | To the team that wins a Totem |
| `per-mini-totem-win` | `80` | To the team that wins a Mini Totem — a column of 3 blocks or fewer |

## KOTH captures

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml:koth"
```

| Key | As shipped | What it does |
|---|---|---|
| `max-counted-captures` | `0` | How many KOTH and Citadel captures count for a team's ranking; `/team resetkoth` resets the counts |
| `points-per-capture` | `100` | Points a KOTH capture is worth; a Citadel's is `points.per-citadel-capture` |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/teams.yml).

<div class="hcf-shipped" markdown>

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml"
```

</div>
