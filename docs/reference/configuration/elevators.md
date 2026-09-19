# elevators.yml

Elevator signs: a sign reading `[Elevator]` over `Up` or `Down` takes whoever right-clicks it to the next floor that way.

**How it plays:** [:octicons-arrow-right-24: Territory — Elevators](../../gameplay/territory.md#elevators)

Every example on this page is **taken from the shipped `elevators.yml`**. Changes apply with `/hcf reload`.

```yaml title="elevators.yml"
--8<-- "src/main/resources/elevators.yml:elevators"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `true` | Elevator signs at all: off, signs already written do nothing |
| `header` | `[Elevator]` | What a sign's first line must say; case does not matter |
| `up-word` · `down-word` | `Up` · `Down` | The second line of a sign going up, or down |
| `max-distance` | `0` | How far a sign looks for the next floor, in blocks; `0` for the whole column |
| `cooldown-seconds` | `1` | The wait between two rides, per player |
| `own-territory-only` | `false` | Only on the rider's team's land |
| `blocked-in-combat` | `false` | Refused while combat-tagged |

How a finished sign reads — its colours, its arrows — is in `lang/en.yml` under `elevator.sign`, with the messages under `elevator`.

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/elevators.yml).

<div class="hcf-shipped" markdown>

```yaml title="elevators.yml"
--8<-- "src/main/resources/elevators.yml"
```

</div>
