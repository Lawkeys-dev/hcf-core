# Developer API

For plugins that react to HCFCore: listen to its events, read its teams, and reach its economy through Vault.

## Depending on HCFCore

HCFCore is not published to a Maven repository yet. Download the jar of a [release](https://github.com/Lawkeys-dev/hcf-core/releases) and compile against it, without shading it. Until `1.0.0` a minor version may change the API: pin the version you build against.

```kotlin
// build.gradle.kts of your plugin
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly(files("libs/hcf-core-0.13.3.jar"))
}
```

and declare it in your `plugin.yml`, so HCFCore loads first:

```yaml
depend: [HCFCore]      # or softdepend, if your plugin also runs without it
```

## What is public

**The events in `com.lawkeys.hcfcore.api.event` are the contract**, along with the types they hand you (`Team`, `TeamRole`, `TeamType`, `TeamEventDispatcher.LeaveCause`). Renaming or removing one of them breaks your plugin, and the project treats them accordingly. Everything else — modules, managers, settings classes — is internal and may change from one version to the next.

All of these events are fired on the main server thread. Each extends `TeamEvent`, whose `getTeam()` is the team concerned.

| Event | Fired | Cancellable | Carries |
|---|---|---|---|
| `TeamCreateEvent` | Before a team is created. Cancelling aborts it entirely | yes | `getCreator()` — `Optional<UUID>` |
| `TeamDisbandEvent` | Before a team is disbanded. Cancelling keeps it | yes | `getActor()` — `Optional<UUID>` |
| `TeamRenameEvent` | Before a rename; the team still has its old name | yes | `getOldName()`, `getNewName()`, `getActor()` |
| `TeamMemberJoinEvent` | After a player joined | no | `getPlayer()` |
| `TeamMemberLeaveEvent` | After a player left, whatever the reason; once per member when a team is disbanded | no | `getPlayer()`, `getCause()` — `LEAVE`, `KICK`, `DISBAND`, `FORCED` |
| `TeamRoleChangeEvent` | After a member's role changed, handovers included | no | `getPlayer()`, `getPreviousRole()`, `getNewRole()` |
| `TeamAllianceChangeEvent` | After two teams allied or broke their alliance; once for the pair | no | `getOtherTeam()`, `isAllied()` |
| `TeamRaidableEvent` | When a team's DTR makes it raidable, or no longer | no | `isRaidable()` |

### TeamRaidableEvent

Two details about it:

- **It reports the DTR, not the map.** EOTW and the Purge make every team raidable whatever its DTR, and fire nothing here.
- **Becoming raidable fires at once** — a death causes it. Ceasing to be fires at the DTR module's next check (`dtr.yml`, `poll-seconds`), since the passing of time causes it and nothing observes time on its own.

### Reading a team

`Team` gives read access: `getId()`, `getName()`, `getType()` (`PLAYER` or `SYSTEM`), `isSafeZone()`, `getLeader()`, `getMembers()`, `getRole(UUID)`, `getAllies()`, `isAlliedWith(UUID)`, `getBalance()`, `getPoints()`... Its mutators are not public: changes go through the plugin's own rules.

### Example

```java
import com.lawkeys.hcfcore.api.event.TeamCreateEvent;
import com.lawkeys.hcfcore.api.event.TeamRaidableEvent;
import java.util.Locale;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class RaidWatcher implements Listener {

    private final Plugin plugin;

    public RaidWatcher(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRaidable(TeamRaidableEvent event) {
        if (event.isRaidable()) {
            plugin.getLogger().info(event.getTeam().getName() + " is raidable");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreate(TeamCreateEvent event) {
        if (event.getTeam().getName().toLowerCase(Locale.ROOT).startsWith("staff")) {
            event.setCancelled(true);
        }
    }
}
```

Register it like any listener, in your plugin's `onEnable`:

```java
getServer().getPluginManager().registerEvents(new RaidWatcher(this), this);
```

## Balances

With Vault installed, balances are reachable through Vault's `Economy` service like any other economy — see [Integrations](../server/integrations.md#vault) for what is and is not exposed.

## Going further

- [Building from source](building.md) — the build, the tests, the project layout.
- [Architecture overview](architecture.md) — how the code is organised, and the rules it follows.
- [Contributing](contributing.md) — how to propose a change.
