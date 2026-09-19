# settings.yml

Which settings players can switch for themselves, and what `/cobble` leaves on the ground.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/interface.md#player-settings)

Every example on this page is **taken from the shipped `settings.yml`**. Changes apply with `/hcf reload`. `enabled: true` at the top switches the module.

## Offered settings

```yaml title="settings.yml"
--8<-- "src/main/resources/settings.yml:offered"
```

The switches players get, in menu order: `scoreboard`, `private-messages`, `tips`, `cobblestone`, and one per section of the scoreboard — `scoreboard-team`, `scoreboard-stats`, `scoreboard-balance`, `scoreboard-combat`, `scoreboard-cooldowns`, `scoreboard-class`, `scoreboard-events`, `scoreboard-timers` — each hiding the rows of `ui.yml` tagged with its section (`[team]`...). Removing one takes it away — it is then on for everybody.

## Cobblestone

```yaml title="settings.yml"
--8<-- "src/main/resources/settings.yml:cobblestone"
```

`materials`: what the cobblestone switch leaves on the ground.

## The whole shipped file

[View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/settings.yml).

<div class="hcf-shipped" markdown>

```yaml title="settings.yml"
--8<-- "src/main/resources/settings.yml"
```

</div>
