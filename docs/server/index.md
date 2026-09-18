# Running a server

The tools for the people who run the map: moderation, the server-side extras, the integrations, and what to do when something does not behave.

<div class="grid cards" markdown>

-   :material-shield-account:{ .lg .middle } __[Moderation](moderation.md)__

    ---

    Staff mode, vanish, freeze, invsee, last inventories, tickets, staff chat, broadcasts, strikes.

-   :material-text-box:{ .lg .middle } __[Holograms](holograms.md)__

    ---

    Floating text and leaderboards, drawn by Paper itself — every client sees them.

-   :material-ticket-percent:{ .lg .middle } __[Redeem codes](redeem.md)__

    ---

    Codes for giveaways and stores, with rewards as console commands.

-   :material-clock-outline:{ .lg .middle } __[Schedules, timers and key-alls](schedules.md)__

    ---

    Tips, daily announcements, countdowns on the scoreboard, and `/keyall`.

-   :material-tools:{ .lg .middle } __[Utility commands](utilities.md)__

    ---

    `/spawn`, `/logout`, `/heal`, `/gamemode`, `/repair`... — or none, if your essentials plugin has them.

-   :material-puzzle:{ .lg .middle } __[Integrations](integrations.md)__

    ---

    Vault, LuckPerms and Lunar Client (Apollo).

-   :material-lifebuoy:{ .lg .middle } __[Troubleshooting](troubleshooting.md)__

    ---

    Symptoms, causes and fixes.

</div>

## Day-to-day

| Task | How |
|---|---|
| Apply a configuration change | `/hcf reload` |
| Check the version and game mode | `/hcf version` |
| Start or stop an event | `/events start <id>`, `/events stop <id>` |
| Open or close the map | `/sotw start [duration]`, `/eotw start` |
| Close a raid early | `/team setdtr <team> <value>` |
| Lift a deathban | `/pvp lift <player>` (console too) |
| Look at the ticket queue | `/tickets` |
| Reset for a new map | Stop the server, back up, then empty the database or point `storage` at a new one |

!!! tip "Read the console"
    The plugin reports every setting it could not use, every load that failed, and why. After `/hcf reload`, a clean console means your files were understood as written.
