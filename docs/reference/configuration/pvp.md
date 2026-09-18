# pvp.yml

Combat: deathbans and their rank tiers, the combat tag, the strength nerf, knockback, attack speed, safe zones, loot protection and friendly fire.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/combat.md)

## What matters

- `deathban.permission-tiers`: the shortest tier a player holds wins — see [Deathbans](../../gameplay/deathbans-and-lives.md).
- **Check `strength-nerf.vanilla-bonus-per-level` for your Minecraft version.**
- Knockback and attack speed ship off.
- `friendly-fire.allies`: `ALWAYS`, `EVENT_AREAS` (default) or `NEVER`.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/pvp.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/pvp.yml).

<div class="hcf-shipped" markdown>

```yaml title="pvp.yml"
--8<-- "src/main/resources/pvp.yml"
```

</div>
