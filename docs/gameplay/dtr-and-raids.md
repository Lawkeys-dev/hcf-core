# DTR and raids

*Configured in [`dtr.yml`](../reference/configuration/dtr.md). Command: `/team dtr [team]`.*

A team's **DTR** — *Deaths Till Raidable* — is how many more deaths it can take before its land opens. It is what makes a base worth defending and a fight worth taking.

## The scale

| | Default | Setting |
|---|---|---|
| Maximum | **1.1 per member**, capped at **6.6** (six members' worth) | `maximum.per-member`, `maximum.cap`, `maximum.base` |
| Cost of a death | **1.0** | `loss-per-death` |
| Floor | **−5.0** | `minimum` |
| Regeneration | **+0.1 every 90 seconds**, from the moment of a death | `regeneration.*` |

**Below 0 — in the negative — the team is raidable.** Back at 0, it is protected again.

A solo team holds 1.1, so its first death leaves it at 0.1 — still protected — and the second one opens it. That asymmetry is the classic HCF scale. A deeper floor means a heavily farmed team takes proportionally longer to close its base.

## The raid cycle

```mermaid
flowchart LR
    P(["🛡️ Protected<br/>DTR above 0"])
    R(["⚔️ Raidable<br/>DTR below 0"])
    P -- "a member's death<br/>takes DTR below 0" --> R
    R -- "DTR regenerates<br/>back to 0" --> P
```

- **While raidable**, anyone but the team's allies can build, break and open things in its land, and explosions go through: the *pillage window*.
- **The land never changes hands.** No team can claim a raidable team's land; the raid gives access to what is inside, never to the ground.
- **Protection comes back by itself** the moment the DTR climbs back to zero, with the claim intact.

EOTW makes every team raidable whatever its DTR; the Purge only lets anybody use the blocks of enemy claims, without building or breaking — see [Map phases](map-phases.md).

## Regeneration

DTR comes back **from the moment of a death — there is no pause after it** — at **0.1 every 90 seconds**: a full point every 15 minutes. **The raid lasts as long as the climb back to zero**: 15 minutes for a team at -1, 30 at -2. The deeper a team is farmed, the longer its base stays open.

It is **stepwise, not continuous**: a partial interval gives nothing, so what players see matches what the server announces.

!!! info "DTR is worked out from time, not ticked"
    What is stored is the value at the last death and the instant regeneration resumes; the current figure is computed whenever it is read. Nothing drifts when the server lags, and **regeneration keeps running while the server is down**: a team that logs off raidable can come back protected. That is the usual HCF behaviour.

## Seeing it

- `/team dtr [team]` — the value, the maximum, and how long until protection returns (and a pause staff set, if any).
- `/team here` — whether the land you stand on is protected or raidable.
- The scoreboard — `%dtr%`, `%dtr_coloured%` (dark red with "(raidable)" when open) and `%dtr_max%`.
- Lunar Client nametags show each player's team and DTR.

## Announcements

- The team is told how much DTR a member's death cost (`announcements.on-death`).
- **Becoming raidable is announced to the whole server at once** — a death causes it.
- **Becoming protected again is announced at the next check**, every 60 seconds (`poll-seconds`). The poll only drives the announcement: the protection itself comes back on time, whatever this value.

## Staff overrides

```text
/team setdtr Vikings 3.3        # set a team's DTR
/team setregen Vikings 600      # regeneration pauses for 10 minutes
/team setregen Vikings 0        # regeneration resumes now
```

`setregen` is the one way to pause regeneration: a death never does. `setdtr` leaves such a pause as it is. Both need `hcfcore.team.admin`.

## Switching DTR off

`enabled: false` means no team is ever raidable: claims stay permanently protected, and deaths cost nothing. `regeneration.amount: 0` keeps DTR but stops regeneration — a team then recovers only through a staff override.

## Points

A team whose DTR makes it raidable **loses half its [points](teams.md#points-and-ranking)** (`teams.yml`, `points.raidable-loss-percent`, `50`), or a fixed number with the share at `0` (`points.per-raidable`) — and **the team whose kill made it raidable takes exactly what it lost** (`points.raidable-steal`): the raid pays. Never an ally; nobody takes it when no team caused the death — a fall, a mob, a player with no team.

## For developers

`TeamRaidableEvent` fires when a team's DTR makes it raidable, or no longer. It reports the DTR, not the map: EOTW fires nothing. See the [Developer API](../developers/api.md#teamraidableevent).
