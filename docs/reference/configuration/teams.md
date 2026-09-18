# teams.yml

Teams: names, size, roles, invitations, alliances, focus, rally, the bank, and the Team Points scale.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/teams.md)

## What matters

- `required-roles` sets the minimum role for every action — see [Teams](../../gameplay/teams.md#roles).
- `points` and `koth` hold the Team Points scale, **all at 0** — a balance decision for your server.
- A limit of `0` means no limit.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/teams.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/teams.yml).

<div class="hcf-shipped" markdown>

```yaml title="teams.yml"
--8<-- "src/main/resources/teams.yml"
```

</div>
