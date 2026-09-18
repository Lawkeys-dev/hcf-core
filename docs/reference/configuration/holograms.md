# holograms.yml

Holograms: how often leaderboard lines are redrawn. The holograms themselves are made in game with `/hologram`.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../server/holograms.md)

Every example on this page is **taken from the shipped `holograms.yml`**. Changes apply with `/hcf reload`. `enabled: true` at the top switches the module; the zone holograms of `events.yml` need it too.

## Refresh

```yaml title="holograms.yml"
--8<-- "src/main/resources/holograms.yml:refresh"
```

| Key | As shipped | What it does |
|---|---|---|
| `refresh-seconds` | `10` | How often lines with a leaderboard are redrawn; the others never are |

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/holograms.yml).

<div class="hcf-shipped" markdown>

```yaml title="holograms.yml"
--8<-- "src/main/resources/holograms.yml"
```

</div>
