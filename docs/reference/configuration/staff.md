# staff.yml

Moderation: staff mode and its toolbar, vanish, staff chat, broadcast, freeze, invsee, lastinv, tickets and strikes: offences, their share of points, the count that disbands.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../server/moderation.md)

Every example on this page is **taken from the shipped `staff.yml`**. Changes apply with `/hcf reload`, the toolbar included.

## Switch

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:enabled"
```

## Staff mode

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:staff-mode"
```

| Key | As shipped | What it does |
|---|---|---|
| `vanish` | `true` | Entering staff mode vanishes you |
| `flight` | `true` | Entering staff mode lets you fly |
| `invulnerable` | `true` | No damage taken or dealt, no mob aggro, nothing picked up |
| `announce` | `true` | Tell other staff who enters and leaves |
| `items` | six slots | The toolbar, by inventory slot (0-8 the hotbar) |

**A toolbar slot:**

| Key | What it does |
|---|---|
| `material` | The item |
| `name`, `lore` | Its name and lines, `&` colours |
| `command` | Run as the staff member, without `/`. `%player%` is the player clicked, `%staff%` the staff member. Any command, another plugin's included |
| `needs-target` | `true` if the item works only by right-clicking a player |
| `vanished-material` | The item shown while its holder is vanished, in place of `material` — the shipped Vanish switch: `GRAY_DYE`, `LIME_DYE` when vanished |

## Vanish and staff chat

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:vanish"
```

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:staff-chat"
```

| Key | As shipped | What it does |
|---|---|---|
| `vanish.hide-from-tab` | `true` | Also hide vanished staff from the player list |
| `vanish.see-permission` | `hcfcore.staff.vanish.see` | Who still sees vanished staff; nobody holds it by default |
| `staff-chat.enabled` | `true` | The staff channel |
| `staff-chat.format` | `&9[SC] ...` | `%player%` and `%message%` |

## Broadcast and clear chat

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:broadcast"
```

| Key | As shipped | What it does |
|---|---|---|
| `format` | `&8[&cBroadcast&8] ...` | `/broadcast`'s line, with `%message%` |
| `clear-chat-lines` | `100` | Blank lines `/clearchat` pushes; staff are skipped |

## Freeze

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:freeze"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | `/freeze` |
| `allowed-commands` | private messages | Commands a frozen player may still run, without `/` |
| `ban-on-logout` | `true` | Disconnecting while frozen bans, until `/freeze unban` |
| `reminder-seconds` | `10` | How often the frozen player is reminded; `0` once |

## Invsee, lastinv and tickets

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:invsee"
```

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:last-inventory"
```

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:tickets"
```

| Key | As shipped | What it does |
|---|---|---|
| `invsee.enabled` | `true` | `/invsee`, read-only without `hcfcore.staff.invsee.edit` |
| `last-inventory.enabled` | `true` | `/lastinv` |
| `last-inventory.keep` | `3` | Deaths kept per player |
| `tickets.enabled` | `true` | `/report`, `/request`, `/tickets` |
| `tickets.cooldown-seconds` | `60` | Wait between two tickets from one player |

## Strikes

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml:strikes"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Strikes |
| `valid-seconds` | `0` | How long a strike counts; `0` for the whole map |
| `disband-at` | `3` | A team is disbanded when its active strikes reach this, whatever they were for; `0` for never |
| `offences.<id>.name` | | How messages write the offence |
| `offences.<id>.points-loss-percent` | 25 to 50 | Share of the team's points a strike for it takes, rounded down |
| `offences.<id>.commands` | none | Console commands run with it: `%team%`, `%strikes%`, `%offence%` |

The shipped offences are `cheating` (50%), `bug-abuse` and `boosting` (40%), `teaming` (35%) and `other` (25%). Add, rename or remove any: the id is what staff type after `/strike add <team>`.

*A `staff.yml` from before offences, with a `ladder`, keeps working with the shipped offences and a warning: move its numbers to `offences` and `disband-at`.*

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/staff.yml).

<div class="hcf-shipped" markdown>

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml"
```

</div>
