# staff.yml

Moderation: staff mode and its toolbar, vanish, staff chat, broadcast, freeze, invsee, lastinv, tickets and the strike ladder.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../server/moderation.md)

## What matters

- `staff-mode.items` is the toolbar: any command, run as the staff member.
- `freeze.ban-on-logout` bans a player who disconnects while frozen.
- The strike `ladder` ships as an example: half the team's points at strikes 1 and 2, disbanded at 3.

Changes apply with `/hcf reload`.

## The shipped file

This is `plugins/HCFCore/staff.yml` as the plugin writes it on the first start. Every comment is part of the reference. [View it on GitHub](https://github.com/Lawkeys-dev/hcf-core/blob/main/src/main/resources/staff.yml).

<div class="hcf-shipped" markdown>

```yaml title="staff.yml"
--8<-- "src/main/resources/staff.yml"
```

</div>
