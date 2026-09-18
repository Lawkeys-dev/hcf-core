# Game modes

The same plugin drives two game modes. Each server runs one of them: an HCF server and a kitmap server are two installations, each with its own data.

```yaml title="config.yml"
--8<-- "src/main/resources/config.yml:kitmap-mode"
```

`kitmap-mode` is read **at startup only**: changing it needs a restart, not `/hcf reload` — modules that never started cannot appear in the middle of a session.

## What changes

| | HCF | Kitmap |
|---|---|---|
| **Lives** (`/lives`, `/revive`) | on | **not started at all** — there is no deathban for a life to lift. The commands answer that they are unavailable, and `lives.yml` is not used |
| Everything else | the same code | the same code, shaped by configuration |

That is the whole difference in code. A kitmap is otherwise a matter of configuration — which is the point: every rule is a setting, so the two modes need no separate builds.

## Setting up a kitmap

The usual kitmap choices, file by file:

=== "pvp.yml"

    Kitmap usually has no deathbans: a death sends the player back to spawn to refill.

    ```yaml
    deathban:
      enabled: false
    ```

=== "kits.yml"

    Players get exactly the kit, refilled from signs at spawn.

    ```yaml
    clear-before-giving: true
    refill-signs:
      enabled: true
      cooldown-seconds: 3
    ```

    Create the kits in game (`/kit create <id>`), with no cooldown, then place signs whose first line is `[Kit]` and second line the kit id.

=== "dtr.yml and claims.yml"

    Many kitmaps keep teams and claims but make raiding pointless or impossible. Either turn DTR off — claims are then permanently protected and deaths cost nothing:

    ```yaml
    # dtr.yml
    enabled: false
    ```

    or keep the classic rules if your kitmap raids.

=== "classes.yml"

    Classes are central to kitmap: a kit holding a class's armour set (gold for Bard, leather for Archer, chainmail for Rogue) makes the class turn on once worn. Keep `warmup-seconds` short for kitmap, and set team limits if you want them:

    ```yaml
    warmup-seconds: 5
    classes:
      bard:
        max-per-team: 2
    ```

    Then create one kit per class (`/kit create bard`...) with the matching armour, and a refill sign for each.

=== "killstreaks.yml"

    Killstreak rewards are a staple of kitmap. The table ships empty:

    ```yaml
    rewards:
      5:
        broadcast: "&c%player% &7is on a &f%streak% &7killstreak!"
        commands:
          - "kit give %player% streak5"
    ```

!!! tip
    Everything that ships empty or switched off is listed in [Setting up a map, step 10](setup-guide.md#10-what-the-plugin-leaves-to-you). Go through it for a kitmap too.
