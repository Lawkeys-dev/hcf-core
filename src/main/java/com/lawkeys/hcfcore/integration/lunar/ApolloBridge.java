package com.lawkeys.hcfcore.integration.lunar;

import com.lawkeys.hcfcore.claim.HomeType;
import com.lawkeys.hcfcore.dtr.DtrManager;
import com.lawkeys.hcfcore.events.RunningEvent;
import com.lawkeys.hcfcore.events.king.KingRun;
import com.lawkeys.hcfcore.ability.Ability;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamRelation;
import com.lawkeys.hcfcore.util.ColorCodes;
import com.lawkeys.hcfcore.util.Cuboid;
import com.lawkeys.hcfcore.util.WorldPosition;
import com.lunarclient.apollo.Apollo;
import com.lunarclient.apollo.common.icon.ItemStackIcon;
import com.lunarclient.apollo.common.location.ApolloBlockLocation;
import com.lunarclient.apollo.common.location.ApolloLocation;
import com.lunarclient.apollo.module.ApolloModule;
import com.lunarclient.apollo.module.cooldown.Cooldown;
import com.lunarclient.apollo.module.cooldown.CooldownModule;
import com.lunarclient.apollo.module.nametag.Nametag;
import com.lunarclient.apollo.module.nametag.NametagModule;
import com.lunarclient.apollo.module.team.TeamMember;
import com.lunarclient.apollo.module.team.TeamModule;
import com.lunarclient.apollo.module.waypoint.Waypoint;
import com.lunarclient.apollo.module.waypoint.WaypointModule;
import com.lunarclient.apollo.player.ApolloPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The four Apollo modules the project owner chose on 12/09/2026 - waypoints, team
 * view, cooldowns and nametags - for players on Lunar Client.
 *
 * <p><strong>The only class that names an Apollo type.</strong> It is loaded only once
 * {@link LunarIntegration} has seen the Apollo plugin, so a server without it never
 * loads one. Every call is the one Lunar documents and uses in its own examples
 * ({@code Apollo.getModuleManager().getModule(...)},
 * {@code Apollo.getPlayerManager().getPlayer(uuid)}, the modules' {@code display*},
 * {@code remove*}, {@code override*} and {@code update*}), checked against the
 * {@code apollo-api} 1.2.7 jar this project compiles against.
 *
 * <p>Everything is read and sent on the main thread, every {@code update-ticks}.
 * Lunar's own team example reads player locations from an async task; Paper's
 * documentation says reading world state there is unsafe, so this does not. Each kind
 * is diffed against what the player was sent ({@link SentState}), and taken back by
 * name - never by the modules' {@code reset*}, which would also clear what Apollo's
 * own configuration or another plugin put there (the team view excepted: a player has
 * one team list, and it is ours).
 */
final class ApolloBridge implements LunarBridge, Listener {

    private static final LegacyComponentSerializer LEGACY = com.lawkeys.hcfcore.util.LegacyText.SERIALIZER;
    /** A cooldown whose end moved less than this is the one the client already shows. */
    private static final long COOLDOWN_TOLERANCE_MS = 1500L;

    private record WaypointSpec(String world, int x, int y, int z, int rgb) {
    }

    private record CooldownSpec(long endsAt, String icon) {
    }

    /** What one Lunar player has been sent. Main thread only. */
    private static final class Viewer {
        final SentState<WaypointSpec> waypoints = new SentState<>();
        final SentState<CooldownSpec> cooldowns = new SentState<>();
        final SentState<List<String>> nametags = new SentState<>();
        boolean teamView;
    }

    private final Plugin plugin;
    private final LangManager lang;
    private final LunarSources sources;
    private final WaypointModule waypointModule;
    private final TeamModule teamModule;
    private final CooldownModule cooldownModule;
    private final NametagModule nametagModule;
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private LunarSettings settings;
    private BukkitTask task;
    private boolean warned;

    private ApolloBridge(Plugin plugin, LangManager lang, LunarSources sources) {
        this.plugin = plugin;
        this.lang = lang;
        this.sources = sources;
        this.waypointModule = Apollo.getModuleManager().getModule(WaypointModule.class);
        this.teamModule = Apollo.getModuleManager().getModule(TeamModule.class);
        this.cooldownModule = Apollo.getModuleManager().getModule(CooldownModule.class);
        this.nametagModule = Apollo.getModuleManager().getModule(NametagModule.class);
    }

    /** Named from {@link LunarIntegration} only after the Apollo plugin was found. */
    static LunarBridge start(Plugin plugin, LangManager lang, LunarSources sources, LunarSettings settings) {
        ApolloBridge bridge = new ApolloBridge(Objects.requireNonNull(plugin, "plugin"),
                Objects.requireNonNull(lang, "lang"), Objects.requireNonNull(sources, "sources"));
        plugin.getServer().getPluginManager().registerEvents(bridge, plugin);
        bridge.apply(settings);
        return bridge;
    }

    @Override
    public void apply(LunarSettings replacement) {
        takeBackEverything();
        if (task != null) {
            task.cancel();
            task = null;
        }
        this.settings = Objects.requireNonNull(replacement, "settings");
        if (replacement.enabled()) {
            long ticks = replacement.updateTicks();
            this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, ticks, ticks);
        }
    }

    @Override
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        takeBackEverything();
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        viewers.remove(id);
        // Gone from every client's world with its entity; sent again if they return.
        String key = id.toString();
        viewers.values().forEach(viewer -> viewer.nametags.forget(key));
    }

    // ------------------------------------------------------------------
    // Every update
    // ------------------------------------------------------------------

    private void update() {
        LunarSettings current = settings;
        long now = System.currentTimeMillis();
        TeamManager teams = sources.teams().getManager();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Optional<ApolloPlayer> found = Apollo.getPlayerManager().getPlayer(viewer.getUniqueId());
            if (found.isEmpty()) {
                continue;
            }
            ApolloPlayer apollo = found.get();
            Viewer state = viewers.computeIfAbsent(viewer.getUniqueId(), id -> new Viewer());
            Team team = teams.getTeamOf(viewer.getUniqueId()).orElse(null);
            try {
                if (current.teamView().enabled() && isOn(TeamModule.class)) {
                    teamView(viewer, apollo, team, state, current.teamView());
                }
                if (current.waypoints().enabled() && isOn(WaypointModule.class)) {
                    waypoints(viewer, apollo, team, state, current.waypoints());
                }
                if (current.cooldowns().enabled() && isOn(CooldownModule.class)) {
                    cooldowns(viewer, apollo, state, current.cooldowns(), now);
                }
                if (current.nametags().enabled() && isOn(NametagModule.class)) {
                    nametags(viewer, apollo, team, state, current.nametags().style());
                }
            } catch (RuntimeException e) {
                // One player's failure must not stop the others; said once, not every update.
                if (!warned) {
                    warned = true;
                    plugin.getLogger().log(Level.WARNING,
                            "Lunar Client update failed for " + viewer.getName() + " (further failures are not logged).", e);
                }
            }
        }
    }

    /** Whether the server owner left this module on in Apollo's own configuration. */
    private static boolean isOn(Class<? extends ApolloModule> module) {
        return Apollo.getModuleManager().isEnabled(module);
    }

    // ------------------------------------------------------------------
    // Team view
    // ------------------------------------------------------------------

    /**
     * Teammates on the minimap and above their heads; beyond the tracking range their
     * position and name are sent too, the client having no entity to follow there.
     */
    private void teamView(Player viewer, ApolloPlayer apollo, Team team, Viewer state, LunarSettings.TeamView rules) {
        List<TeamMember> members = new ArrayList<>();
        if (team != null) {
            Color marker = new Color(rules.markerColor());
            Location from = viewer.getLocation();
            double range = rules.trackingRange() * rules.trackingRange();
            for (UUID id : team.getMemberIds()) {
                Player member = id.equals(viewer.getUniqueId()) ? null : Bukkit.getPlayer(id);
                if (member == null || !viewer.canSee(member) || !member.getWorld().equals(viewer.getWorld())) {
                    continue;
                }
                TeamMember.TeamMemberBuilder builder = TeamMember.builder().playerUuid(id).markerColor(marker);
                Location at = member.getLocation();
                double dx = at.getX() - from.getX();
                double dz = at.getZ() - from.getZ();
                if (dx * dx + dz * dz > range) {
                    builder.location(ApolloLocation.builder().world(at.getWorld().getName())
                                    .x(at.getX()).y(at.getY()).z(at.getZ()).build())
                            .displayName(Component.text(member.getName()));
                }
                members.add(builder.build());
            }
        }
        if (!members.isEmpty()) {
            teamModule.updateTeamMembers(apollo, members);
            state.teamView = true;
        } else if (state.teamView) {
            teamModule.resetTeamMembers(apollo);
            state.teamView = false;
        }
    }

    // ------------------------------------------------------------------
    // Waypoints
    // ------------------------------------------------------------------

    private void waypoints(Player viewer, ApolloPlayer apollo, Team team, Viewer state, LunarSettings.Waypoints rules) {
        Map<String, WaypointSpec> wanted = new LinkedHashMap<>();
        TeamManager teams = sources.teams().getManager();
        if (team != null) {
            if (rules.hq()) {
                home(team.getId(), HomeType.HQ).ifPresent(at -> wanted.put(text("apollo.waypoint.hq"),
                        spec(at, rules.hqColor())));
            }
            if (rules.base()) {
                home(team.getId(), HomeType.BASE).ifPresent(at -> wanted.put(text("apollo.waypoint.base"),
                        spec(at, rules.baseColor())));
            }
            if (rules.rally()) {
                teams.getRally(team).ifPresent(at -> wanted.put(text("apollo.waypoint.rally"),
                        spec(at, rules.rallyColor())));
            }
            if (rules.focus()) {
                for (UUID id : team.getFocusedPlayers()) {
                    Player target = Bukkit.getPlayer(id);
                    if (target != null && viewer.canSee(target)) {
                        wanted.put(text("apollo.waypoint.focus", "player", target.getName()),
                                spec(target.getLocation(), rules.focusColor()));
                    }
                }
                for (UUID id : team.getFocusedTeams()) {
                    teams.getTeam(id).ifPresent(focused -> home(focused.getId(), HomeType.HQ).ifPresent(at ->
                            wanted.put(text("apollo.waypoint.focus-team", "team", focused.getName()),
                                    spec(at, rules.focusColor()))));
                }
            }
        }
        if (rules.events()) {
            for (RunningEvent run : sources.events().getManager().getActiveEvents()) {
                wanted.put(plain(run.getDefinition().displayName()),
                        spec(run.getDefinition().zone(), rules.eventColor()));
            }
            sources.events().getConquest().getManager().getCurrent().ifPresent(run -> run.zones().forEach(zone ->
                    wanted.put(text("apollo.waypoint.conquest-zone", "zone", plain(zone.zone().displayName())),
                            spec(zone.zone().area(), rules.eventColor()))));
            sources.events().getKing().getManager().getCurrent().filter(KingRun::isReigning).ifPresent(run -> {
                Player king = Bukkit.getPlayer(run.getKingId());
                if (king != null && viewer.canSee(king)) {
                    wanted.put(text("apollo.waypoint.king", "player", king.getName()),
                            spec(king.getLocation(), rules.eventColor()));
                }
            });
        }
        SentState.Plan<WaypointSpec> plan = state.waypoints.reconcile(wanted, Object::equals);
        for (String name : plan.remove()) {
            waypointModule.removeWaypoint(apollo, name);
        }
        for (Map.Entry<String, WaypointSpec> entry : plan.send().entrySet()) {
            if (plan.replaced().contains(entry.getKey())) {
                waypointModule.removeWaypoint(apollo, entry.getKey());
            }
            WaypointSpec spec = entry.getValue();
            waypointModule.displayWaypoint(apollo, Waypoint.builder()
                    .name(entry.getKey())
                    .location(ApolloBlockLocation.builder().world(spec.world()).x(spec.x()).y(spec.y()).z(spec.z())
                            .build())
                    .color(new Color(spec.rgb()))
                    .preventRemoval(false)
                    .hidden(false)
                    .build());
        }
    }

    private Optional<WorldPosition> home(UUID teamId, HomeType type) {
        return sources.claims().getManager().getHome(teamId, type).map(home -> home.position());
    }

    private static WaypointSpec spec(WorldPosition at, int rgb) {
        return new WaypointSpec(at.world(), (int) Math.floor(at.x()), (int) Math.floor(at.y()),
                (int) Math.floor(at.z()), rgb);
    }

    private static WaypointSpec spec(Location at, int rgb) {
        return new WaypointSpec(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), rgb);
    }

    /** The middle of a zone, on its floor. */
    private static WaypointSpec spec(Cuboid zone, int rgb) {
        return new WaypointSpec(zone.world(), Math.floorDiv(zone.minX() + zone.maxX(), 2), zone.minY(),
                Math.floorDiv(zone.minZ() + zone.maxZ(), 2), rgb);
    }

    // ------------------------------------------------------------------
    // Cooldowns
    // ------------------------------------------------------------------

    /**
     * Every cooldown a player has running: the combat tag, the pearl, the item
     * cooldowns, a countdown ({@code /spawn}, {@code /team hq}...), the partner items'
     * - each, the shared one, the Pocket Bard's sets - the class's clicks and backstab,
     * the crowbar.
     */
    private void cooldowns(Player viewer, ApolloPlayer apollo, Viewer state, LunarSettings.Cooldowns rules, long now) {
        UUID id = viewer.getUniqueId();
        Map<String, CooldownSpec> wanted = new LinkedHashMap<>();
        if (rules.combatTag()) {
            long left = sources.pvp().getCombatTags().getRemainingSeconds(id);
            if (left > 0) {
                wanted.put("hcf-combat-tag", new CooldownSpec(now + left * 1000L, rules.combatTagIcon()));
            }
        }
        if (rules.enderPearl()) {
            long left = sources.pvp().pearlSecondsLeft(id);
            if (left > 0) {
                wanted.put("hcf-ender-pearl", new CooldownSpec(now + left * 1000L, rules.enderPearlIcon()));
            }
        }
        if (rules.itemCooldowns() && sources.pvp().getSettings().itemCooldowns().enabled()) {
            for (var item : sources.pvp().getSettings().itemCooldowns().items()) {
                long left = sources.pvp().itemCooldownLeft(viewer, item);
                if (left > 0) {
                    wanted.put("hcf-item-" + item.id(), new CooldownSpec(now + left * 1000L, item.material()));
                }
            }
        }
        if (rules.warmups()) {
            sources.warmups().of(id).ifPresent(warmup -> wanted.put("hcf-warmup-" + warmup.kind(),
                    new CooldownSpec(warmup.finishesAt(), rules.warmupIcon())));
        }
        if (rules.abilities()) {
            for (Ability ability : sources.abilities().getSettings().abilities()) {
                long left = sources.abilities().getCooldowns().remaining(id, ability.id(), now);
                if (left > 0) {
                    wanted.put("hcf-ability-" + ability.id(), new CooldownSpec(now + left * 1000L,
                            ability.material().toUpperCase(Locale.ROOT)));
                }
            }
            long global = sources.abilities().globalCooldownLeft(id);
            if (global > 0) {
                wanted.put("hcf-ability-global", new CooldownSpec(now + global * 1000L, rules.abilityGlobalIcon()));
            }
            for (var pocket : sources.abilities().getSettings().pocketBard()) {
                long left = sources.abilities().pocketCooldownLeft(id, pocket);
                if (left > 0) {
                    wanted.put("hcf-pocket-" + pocket.id(), new CooldownSpec(now + left * 1000L,
                            pocket.material().toUpperCase(Locale.ROOT)));
                }
            }
        }
        if (rules.classes() && sources.classes() != null) {
            sources.classes().runningCooldowns(viewer).forEach((item, left) -> wanted.put(
                    "hcf-class-" + item.toLowerCase(Locale.ROOT), new CooldownSpec(now + left * 1000L, item)));
        }
        if (rules.crowbar() && sources.crowbar() != null) {
            long left = sources.crowbar().cooldownLeft(id);
            if (left > 0) {
                wanted.put("hcf-crowbar", new CooldownSpec(now + left * 1000L,
                        sources.crowbar().getSettings().material().name()));
            }
        }
        SentState.Plan<CooldownSpec> plan = state.cooldowns.reconcile(wanted, (sent, next) ->
                sent.icon().equals(next.icon()) && Math.abs(sent.endsAt() - next.endsAt()) < COOLDOWN_TOLERANCE_MS);
        for (String name : plan.remove()) {
            cooldownModule.removeCooldown(apollo, name);
        }
        for (Map.Entry<String, CooldownSpec> entry : plan.send().entrySet()) {
            if (plan.replaced().contains(entry.getKey())) {
                cooldownModule.removeCooldown(apollo, entry.getKey());
            }
            CooldownSpec spec = entry.getValue();
            cooldownModule.displayCooldown(apollo, Cooldown.builder()
                    .name(entry.getKey())
                    .duration(Duration.ofMillis(Math.max(0L, spec.endsAt() - now)))
                    .icon(ItemStackIcon.builder().itemName(spec.icon()).build())
                    .build());
        }
    }

    // ------------------------------------------------------------------
    // Nametags
    // ------------------------------------------------------------------

    /** Every player this viewer can see in their world, coloured by how their teams relate. */
    private void nametags(Player viewer, ApolloPlayer apollo, Team team, Viewer state, NametagStyle style) {
        TeamManager teams = sources.teams().getManager();
        DtrManager dtr = sources.dtr().getManager();
        Map<String, List<String>> wanted = new HashMap<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer) || !viewer.canSee(target) || !target.getWorld().equals(viewer.getWorld())) {
                continue;
            }
            Team theirs = teams.getTeamOf(target.getUniqueId()).orElse(null);
            TeamRelation base = teams.getRelation(team, theirs);
            boolean focused = team != null && (team.isFocusingPlayer(target.getUniqueId())
                    || (theirs != null && team.isFocusingTeam(theirs.getId())));
            String dtrText = theirs == null ? "" : dtr.getDtr(theirs.getId()).map(DtrManager::format).orElse("");
            wanted.put(target.getUniqueId().toString(), style.lines(NametagStyle.relation(base, focused),
                    theirs == null ? null : theirs.getName(), dtrText, target.getName()));
        }
        SentState.Plan<List<String>> plan = state.nametags.reconcile(wanted, List::equals);
        for (String key : plan.remove()) {
            nametagModule.resetNametag(apollo, UUID.fromString(key));
        }
        for (Map.Entry<String, List<String>> entry : plan.send().entrySet()) {
            List<Component> lines = entry.getValue().stream()
                    .map(line -> (Component) LEGACY.deserialize(com.lawkeys.hcfcore.lang.LangManager.colorize(line)))
                    .toList();
            nametagModule.overrideNametag(apollo, UUID.fromString(entry.getKey()),
                    Nametag.builder().lines(lines).build());
        }
    }

    // ------------------------------------------------------------------
    // Taking everything back
    // ------------------------------------------------------------------

    private void takeBackEverything() {
        for (Map.Entry<UUID, Viewer> entry : viewers.entrySet()) {
            Viewer state = entry.getValue();
            try {
                Apollo.getPlayerManager().getPlayer(entry.getKey()).ifPresent(apollo -> {
                    state.waypoints.names().forEach(name -> waypointModule.removeWaypoint(apollo, name));
                    state.cooldowns.names().forEach(name -> cooldownModule.removeCooldown(apollo, name));
                    state.nametags.names().forEach(key -> nametagModule.resetNametag(apollo, UUID.fromString(key)));
                    if (state.teamView) {
                        teamModule.resetTeamMembers(apollo);
                    }
                });
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not take back what a Lunar Client player was sent.", e);
            }
        }
        viewers.clear();
        warned = false;
    }

    private String text(String key, String... placeholders) {
        return plain(lang.get(key, placeholders));
    }

    /** Waypoint names are plain text: colour codes, typed or translated, are dropped. */
    private static String plain(String text) {
        return ColorCodes.strip(com.lawkeys.hcfcore.lang.LangManager.colorize(text));
    }
}
