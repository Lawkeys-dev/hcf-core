package com.lawkeys.hcfcore.ui;

import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.dtr.DtrModule;
import com.lawkeys.hcfcore.economy.EconomyModule;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.events.RunningEvent;
import com.lawkeys.hcfcore.events.Standing;
import com.lawkeys.hcfcore.events.conquest.ConquestController;
import com.lawkeys.hcfcore.events.conquest.ConquestDefinition;
import com.lawkeys.hcfcore.events.conquest.ConquestMessages;
import com.lawkeys.hcfcore.events.conquest.ConquestRun;
import com.lawkeys.hcfcore.events.core.CoreEventController;
import com.lawkeys.hcfcore.events.core.CoreEventKind;
import com.lawkeys.hcfcore.events.core.CoreEventDefinition;
import com.lawkeys.hcfcore.events.core.CoreMessages;
import com.lawkeys.hcfcore.events.core.CoreRun;
import com.lawkeys.hcfcore.events.core.CoreWinRule;
import com.lawkeys.hcfcore.events.slide.SlideController;
import com.lawkeys.hcfcore.events.slide.SlideDefinition;
import com.lawkeys.hcfcore.events.slide.SlideMessages;
import com.lawkeys.hcfcore.events.slide.SlideRun;
import com.lawkeys.hcfcore.events.king.KingEventController;
import com.lawkeys.hcfcore.events.king.KingRun;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.phase.PhaseModule;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.pvp.PvpSettings;
import com.lawkeys.hcfcore.schedule.CustomTimers;
import com.lawkeys.hcfcore.schedule.ScheduleModule;
import com.lawkeys.hcfcore.stats.PlayerStats;
import com.lawkeys.hcfcore.stats.StatsModule;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.ui.listener.BoardListener;
import com.lawkeys.hcfcore.ui.scoreboard.PlayerBoard;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The scoreboard and tab list (FEATURES.md section 9).
 *
 * <p>Started last of all: it reads from every other module and is read by none, so
 * whatever is missing simply leaves its placeholder empty - and a line that is
 * entirely empty disappears. A kitmap with no DTR module shows no DTR row without
 * anything being configured away.
 */
public final class UiModule {

    private final Plugin plugin;
    private final LangManager lang;
    private final TeamModule teams;
    private final DtrModule dtr;
    private final PvpModule pvp;
    private final EconomyModule economy;
    private final StatsModule stats;
    private final ClaimModule claims;
    private final PhaseModule phases;
    private final ScheduleModule schedule;
    private final EventModule events;

    /** How many custom timers the board can show, as %timer_1% to %timer_3%. */
    private static final int TIMER_ROWS = 3;

    private volatile UiSettings settings = UiSettings.defaults();
    private volatile java.util.function.Predicate<Player> wantsBoard = player -> true;
    private volatile java.util.function.BiPredicate<Player, String> wantsRow = (player, section) -> true;
    /** Placeholders other modules add, read on the main thread when a board is drawn. */
    private final List<java.util.function.Function<Player, Map<String, String>>> placeholderSources =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<UUID, PlayerBoard> boards = new ConcurrentHashMap<>();
    private BukkitTask updateTask;
    private final TabList tabList;

    /** Every module here may be {@code null}: a missing one empties its placeholders. */
    public UiModule(Plugin plugin, LangManager lang, TeamModule teams, DtrModule dtr, PvpModule pvp,
                    EconomyModule economy, StatsModule stats, ClaimModule claims, PhaseModule phases,
                    ScheduleModule schedule, EventModule events) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
        this.teams = teams;
        this.dtr = dtr;
        this.pvp = pvp;
        this.economy = economy;
        this.stats = stats;
        this.claims = claims;
        this.phases = phases;
        this.schedule = schedule;
        this.events = events;
        this.tabList = new TabList(plugin, this, lang, teams, stats, claims,
                amount -> economy == null || economy.getManager() == null
                        ? String.format(Locale.ROOT, "%.2f", amount) : economy.getManager().format(amount));
    }

    /**
     * What the tab list needs from outside this module: the game mode (which style
     * {@code auto} means), the players' LuckPerms prefixes, and the grid - {@code null}
     * without PacketEvents. Set before {@link #enable()}.
     */
    public void setTabSources(com.lawkeys.hcfcore.mode.GameMode mode,
                              java.util.function.Supplier<com.lawkeys.hcfcore.chat.ChatDecorations> decorations,
                              com.lawkeys.hcfcore.ui.tab.GridTab grid) {
        tabList.setSources(mode, decorations, grid);
    }

    public UiSettings getSettings() {
        return settings;
    }

    public void enable() {
        reloadSettings();
        plugin.getServer().getPluginManager().registerEvents(new BoardListener(this), plugin);
        startUpdating();
    }

    private void startUpdating() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        UiSettings.ScoreboardRules rules = settings.scoreboard();
        if (!rules.enabled()) {
            return;
        }
        long period = Math.max(1L, rules.updateTicks());
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, period, period);
    }

    /**
     * Adds placeholders a module draws itself - the classes' {@code %class_line%}, for
     * one - so this module need not know every module that exists. The source is
     * asked once per board and per redraw, on the main thread, and must answer every
     * key it owns, with an empty value when there is nothing to show: a row whose
     * placeholders are all empty is dropped.
     */
    public void addPlaceholderSource(java.util.function.Function<Player, Map<String, String>> source) {
        placeholderSources.add(Objects.requireNonNull(source, "source"));
    }

    /**
     * Decides who gets a board. Installed by the player settings module, so a player
     * who switched the scoreboard off keeps it off; until then, everybody gets one.
     */
    public void setBoardFilter(java.util.function.Predicate<Player> filter) {
        this.wantsBoard = Objects.requireNonNull(filter, "filter");
    }

    /**
     * Decides which tagged rows a player sees - a row of {@code ui.yml} starting with
     * {@code [team]} is shown if this says yes for {@code "team"}. Installed by the
     * player settings module; until then, every row shows.
     */
    public void setRowFilter(java.util.function.BiPredicate<Player, String> filter) {
        this.wantsRow = Objects.requireNonNull(filter, "filter");
    }

    /**
     * Gives a player their board, or takes it away, after their choice changed.
     * Taking it away puts them back on the server's main scoreboard, which is what a
     * player without this plugin's board would be looking at.
     */
    public void refresh(Player player) {
        if (settings.scoreboard().enabled() && wantsBoard.test(player)) {
            attach(player);
            return;
        }
        if (boards.remove(player.getUniqueId()) != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** Gives a player their board. Called when they join, and after a reload. */
    public void attach(Player player) {
        if (!settings.scoreboard().enabled() || !wantsBoard.test(player)) {
            return;
        }
        PlayerBoard board = boards.computeIfAbsent(player.getUniqueId(),
                id -> new PlayerBoard(LangManager.colorize(LangManager.theme().title(settings.scoreboard().title()))));
        board.show(player);
        update(player, board);
    }

    public void detach(UUID playerId) {
        boards.remove(playerId);
        tabList.forget(playerId);
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerBoard board = boards.get(player.getUniqueId());
            if (board != null) {
                update(player, board);
            }
        }
    }

    /**
     * Colours are translated after the placeholders are filled, not before: a value
     * can carry its own codes ({@code %dtr_coloured%} does), and translating the
     * template first left them printed as raw {@code &4} on every board.
     */
    private void update(Player player, PlayerBoard board) {
        List<String> lines = new ArrayList<>();
        for (String line : settings.scoreboard().lines()) {
            ScoreboardRow row = ScoreboardRow.parse(line);
            if (row.section() == null || wantsRow.test(player, row.section())) {
                lines.add(row.text());
            }
        }
        board.update(renderer(player).renderAll(lines).stream()
                .map(LangManager::colorize)
                .toList());
    }

    /**
     * Collects every value the board can show for this player.
     *
     * <p>Each module is asked only if it is running, so the whole thing degrades to
     * empty strings rather than to null checks scattered through the templates.
     */
    LineRenderer renderer(Player player) {
        LineRenderer out = new LineRenderer();
        long now = System.currentTimeMillis();

        Optional<Team> team = teams == null || teams.getManager() == null
                ? Optional.empty()
                : teams.getManager().getTeamOf(player.getUniqueId());
        out.with("%team%", team.map(Team::getName).orElse(lang.get(UiMessages.NO_TEAM)));
        // Only the players this viewer can see: a vanished staff member counted here
        // gave their presence away (found in game, 20/09/2026). Staff who may see
        // vanished players count them, as they see them.
        out.with("%online%", String.valueOf(Bukkit.getOnlinePlayers().stream().filter(player::canSee).count()));
        out.with("%player%", player.getName());
        out.with("%world%", player.getWorld().getName());
        out.with("%ping%", String.valueOf(player.getPing()));

        renderDtr(out, team);
        renderTeamMarkers(out, team);
        renderStats(out, player, now);
        renderEconomy(out, player);
        renderCombat(out, player, now);
        renderPhase(out);
        renderEvents(out, team);
        renderTerritory(out, player);
        renderTimers(out, now);
        for (var source : placeholderSources) {
            source.apply(player).forEach(out::with);
        }
        return out;
    }

    /**
     * The running capture event closest to being captured, and the King while one
     * reigns. {@code %event_line%} was documented and placed on the default board
     * from the start, but nothing filled it, so the row never appeared.
     */
    private void renderEvents(LineRenderer out, Optional<Team> viewerTeam) {
        String eventLine = "";
        String kingLine = "";
        String kingLocationLine = "";
        if (events != null && events.getManager() != null) {
            Optional<RunningEvent> closest = events.getManager().getActiveEvents().stream()
                    .min(Comparator.comparingLong(RunningEvent::getRemainingMillis));
            if (closest.isPresent()) {
                eventLine = lang.get(UiMessages.EVENT_LINE,
                        "time", Durations.formatWithSeconds(closest.get().getRemainingSeconds()),
                        "event", closest.get().getDefinition().displayName());
            }
            KingEventController king = events.getKing();
            Optional<KingRun> reign = king == null || king.getManager() == null
                    ? Optional.empty()
                    : king.getManager().getCurrent().filter(KingRun::isReigning);
            if (reign.isPresent()) {
                Player crowned = Bukkit.getPlayer(reign.get().getKingId());
                kingLine = lang.get(UiMessages.KING_LINE,
                        "time", Durations.formatWithSeconds(king.getManager().getRemainingSeconds()),
                        "player", crowned == null ? "?" : crowned.getName());
                // Where the King is, redrawn with the board: the chat says it only once a minute.
                if (crowned != null) {
                    Location at = crowned.getLocation();
                    kingLocationLine = lang.get(UiMessages.KING_LOCATION_LINE,
                            "x", String.valueOf(at.getBlockX()), "y", String.valueOf(at.getBlockY()),
                            "z", String.valueOf(at.getBlockZ()),
                            "world", at.getWorld() == null ? "" : at.getWorld().getName());
                }
            }
        }
        out.with("%event_line%", eventLine).with("%king_line%", kingLine)
                .with("%king_location_line%", kingLocationLine);
        renderConquest(out);
        renderCore(out, viewerTeam);
        renderSlide(out);
        renderTotem(out);
    }

    /** How many Conquest zones the board can show, as %conquest_zone_1% to %conquest_zone_4%. */
    private static final int CONQUEST_ZONE_ROWS = 4;

    /**
     * The running Conquest: the leading team and its points, then each zone's
     * countdown - the classic Conquest board. Empty rows outside a Conquest.
     */
    private void renderConquest(LineRenderer out) {
        ConquestController conquest = events == null ? null : events.getConquest();
        Optional<ConquestRun> run = conquest == null ? Optional.empty() : conquest.getManager().getCurrent();
        String line = "";
        List<ConquestRun.ZoneState> zones = List.of();
        if (run.isPresent()) {
            ConquestDefinition definition = run.get().getDefinition();
            List<Standing> standings = run.get().standings();
            String leader = standings.isEmpty() ? "-"
                    : teams == null || teams.getManager() == null ? "?"
                    : teams.getManager().getTeam(standings.get(0).teamId()).map(Team::getName).orElse("?");
            line = lang.get(ConquestMessages.SCOREBOARD_LINE,
                    "team", leader,
                    "points", String.valueOf(standings.isEmpty() ? 0 : standings.get(0).points()),
                    "target", String.valueOf(definition.pointsToWin()),
                    "event", definition.displayName());
            zones = run.get().zones();
        }
        out.with("%conquest_line%", line);
        for (int i = 0; i < CONQUEST_ZONE_ROWS; i++) {
            String row = "";
            if (i < zones.size()) {
                ConquestRun.ZoneState zone = zones.get(i);
                row = lang.get(ConquestMessages.SCOREBOARD_ZONE,
                        "time", Durations.formatWithSeconds(zone.remainingSeconds()),
                        "zone", zone.zone().displayName());
            }
            out.with("%conquest_zone_" + (i + 1) + "%", row);
        }
    }

    /**
     * The running DTC and Last Break - {@code %dtc_line%} reads common health
     * under SHARED, or the leader's own breaks under PER_TEAM;
     * {@code %dtc_team_line%} is the viewer's own team's breaks, empty without a
     * team or a run. Empty rows outside a run.
     */
    private void renderCore(LineRenderer out, Optional<Team> viewerTeam) {
        CoreEventController core = events == null ? null : events.getCore();
        Optional<CoreRun> dtcRun = core == null ? Optional.empty()
                : core.getManager().getCurrent().filter(run -> run.getDefinition().kind() == CoreEventKind.DTC);
        String dtcLine = "";
        String dtcTeamLine = "";
        if (dtcRun.isPresent()) {
            CoreRun run = dtcRun.get();
            CoreEventDefinition definition = run.getDefinition();
            if (definition.winRule() == CoreWinRule.FIRST_TO_TARGET) {
                List<Standing> standings = run.standings();
                String leader = standings.isEmpty() ? "-"
                        : teams == null || teams.getManager() == null ? "?"
                        : teams.getManager().getTeam(standings.get(0).teamId()).map(Team::getName).orElse("?");
                dtcLine = lang.get(CoreMessages.SCOREBOARD_DTC_LINE_PER_TEAM,
                        "event", definition.displayName(), "team", leader,
                        "breaks", String.valueOf(standings.isEmpty() ? 0 : standings.get(0).points()),
                        "target", String.valueOf(definition.breaks()));
            } else {
                dtcLine = lang.get(CoreMessages.SCOREBOARD_DTC_LINE, "event", definition.displayName(),
                        "health", String.valueOf(run.health()), "max", String.valueOf(definition.breaks()));
            }
            if (viewerTeam.isPresent()) {
                dtcTeamLine = lang.get(CoreMessages.SCOREBOARD_DTC_TEAM_LINE,
                        "breaks", String.valueOf(run.breaksOf(viewerTeam.get().getId())));
            }
        }
        out.with("%dtc_line%", dtcLine).with("%dtc_team_line%", dtcTeamLine);

        Optional<CoreRun> lastBreakRun = core == null ? Optional.empty()
                : core.getManager().getCurrent().filter(run -> run.getDefinition().kind() == CoreEventKind.LAST_BREAK);
        String lastBreakLine = "";
        if (lastBreakRun.isPresent()) {
            CoreRun run = lastBreakRun.get();
            lastBreakLine = lang.get(CoreMessages.SCOREBOARD_LAST_BREAK_LINE, "event", run.getDefinition().displayName(),
                    "health", String.valueOf(run.health()), "max", String.valueOf(run.getDefinition().breaks()));
        }
        out.with("%last_break_line%", lastBreakLine);
    }

    /** How many Slide rows the top 3 can show, as %slide_top_1% to %slide_top_3%. */
    private static final int SLIDE_TOP_ROWS = 3;

    /** The running Totem: the team on its way and how far, or that nobody has started. Empty outside a run. */
    private void renderTotem(LineRenderer out) {
        com.lawkeys.hcfcore.events.totem.TotemController totem = events == null ? null : events.getTotem();
        Optional<com.lawkeys.hcfcore.events.totem.TotemRun> run = totem == null ? Optional.empty()
                : totem.getManager().getCurrent();
        String line = "";
        if (run.isPresent()) {
            var definition = run.get().getDefinition();
            UUID holder = run.get().holder();
            line = holder == null
                    ? lang.get(com.lawkeys.hcfcore.events.totem.TotemMessages.SCOREBOARD_LINE_NOBODY,
                            "event", definition.displayName(), "height", String.valueOf(definition.height()))
                    : lang.get(com.lawkeys.hcfcore.events.totem.TotemMessages.SCOREBOARD_LINE,
                            "event", definition.displayName(),
                            "team", teams == null || teams.getManager() == null ? "?"
                                    : teams.getManager().getTeam(holder).map(Team::getName).orElse("?"),
                            "broken", String.valueOf(run.get().brokenCount()),
                            "height", String.valueOf(definition.height()));
        }
        out.with("%totem_line%", line);
    }

    /** The running Slide's leader, then the live top 3. Empty rows outside a run. */
    private void renderSlide(LineRenderer out) {
        SlideController slide = events == null ? null : events.getSlide();
        Optional<SlideRun> run = slide == null ? Optional.empty() : slide.getManager().getCurrent();
        String line = "";
        List<Standing> top = List.of();
        if (run.isPresent()) {
            SlideDefinition definition = run.get().getDefinition();
            List<Standing> standings = run.get().standings();
            String leader = standings.isEmpty() ? "-"
                    : teams == null || teams.getManager() == null ? "?"
                    : teams.getManager().getTeam(standings.get(0).teamId()).map(Team::getName).orElse("?");
            line = lang.get(SlideMessages.SCOREBOARD_LINE,
                    "team", leader,
                    "points", String.valueOf(standings.isEmpty() ? 0 : standings.get(0).points()),
                    "target", String.valueOf(definition.pointsToWin()),
                    "event", definition.displayName());
            top = run.get().top(SLIDE_TOP_ROWS);
        }
        out.with("%slide_line%", line);
        for (int i = 0; i < SLIDE_TOP_ROWS; i++) {
            String row = "";
            if (i < top.size()) {
                row = lang.get(SlideMessages.SCOREBOARD_TOP, "rank", String.valueOf(i + 1),
                        "team", teams == null || teams.getManager() == null ? "?"
                                : teams.getManager().getTeam(top.get(i).teamId()).map(Team::getName).orElse("?"),
                        "points", String.valueOf(top.get(i).points()));
            }
            out.with("%slide_top_" + (i + 1) + "%", row);
        }
    }

    /** The custom timers, soonest first, one ready-made row each; a missing one is an empty row. */
    private void renderTimers(LineRenderer out, long now) {
        List<CustomTimers.Timer> running = schedule == null ? List.of() : schedule.getTimers().running();
        for (int i = 0; i < TIMER_ROWS; i++) {
            String row = "";
            if (i < running.size()) {
                CustomTimers.Timer timer = running.get(i);
                row = lang.get(UiMessages.TIMER_LINE, "time",
                        Durations.formatWithSeconds(timer.remainingSeconds(now)), "label", timer.label());
            }
            out.with("%timer_" + (i + 1) + "%", row);
        }
    }

    private void renderDtr(LineRenderer out, Optional<Team> team) {
        if (dtr == null || dtr.getManager() == null || team.isEmpty()) {
            out.with("%dtr%", "").with("%dtr_coloured%", "").with("%dtr_max%", "");
            return;
        }
        double value = dtr.getManager().getDtr(team.get());
        String shown = String.format(Locale.ROOT, "%.2f", value);
        // The team's maximum, which grows with its size. It used to repeat the
        // current value, so "%dtr%/%dtr_max%" read "0.10/0.10" on a team one death
        // from raidable (found 13/09/2026).
        out.with("%dtr%", shown).with("%dtr_max%",
                String.format(Locale.ROOT, "%.2f", dtr.getManager().getMaximum(team.get())));
        // The territory's state, not the DTR's alone: EOTW and the Purge open every
        // claim, and the board said "0.50" in green through an EOTW.
        boolean raidable = dtr.getManager().isRaidable(team.get().getId())
                || dtr.getRaidOverride().everyTeamRaidable();
        out.with("%dtr_coloured%", (raidable ? "{error}" : "{success}") + shown
                + (raidable ? " " + lang.get(UiMessages.DTR_RAIDABLE) : ""));
    }

    /**
     * What the player's team is focusing, and where it rallies. FEATURES.md promised
     * both on the scoreboard "with the ui/ module" and nothing drew them: outside
     * Lunar Client a focus or a rally had no visible effect (found in game,
     * 13/09/2026).
     *
     * <p>A focused player is shown only while online - the name of an offline one
     * would be read from their player file, disk I/O on the main thread every second
     * - and a focused team after the players.
     */
    private void renderTeamMarkers(LineRenderer out, Optional<Team> team) {
        out.with("%focus_line%", "").with("%rally_line%", "");
        if (team.isEmpty() || teams == null || teams.getManager() == null) {
            return;
        }
        String target = null;
        for (UUID playerId : team.get().getFocusedPlayers()) {
            Player focused = Bukkit.getPlayer(playerId);
            if (focused != null) {
                target = focused.getName();
                break;
            }
        }
        if (target == null) {
            for (UUID teamId : team.get().getFocusedTeams()) {
                Optional<Team> focused = teams.getManager().getTeam(teamId);
                if (focused.isPresent()) {
                    target = focused.get().getName();
                    break;
                }
            }
        }
        if (target != null) {
            out.with("%focus_line%", lang.get(UiMessages.FOCUS_LINE, "target", target));
        }
        teams.getManager().getRally(team.get()).ifPresent(rally -> out.with("%rally_line%",
                lang.get(UiMessages.RALLY_LINE, "x", String.valueOf((int) Math.floor(rally.x())),
                        "y", String.valueOf((int) Math.floor(rally.y())),
                        "z", String.valueOf((int) Math.floor(rally.z())))));
    }

    private void renderStats(LineRenderer out, Player player, long now) {
        if (stats == null || stats.getManager() == null) {
            out.with("%kills%", "").with("%deaths%", "").with("%killstreak%", "")
                    .with("%kdr%", "").with("%playtime%", "");
            return;
        }
        PlayerStats row = stats.getManager().get(player.getUniqueId(), player.getName());
        out.with("%kills%", row.getKills())
                .with("%deaths%", row.getDeaths())
                .with("%killstreak%", row.getKillstreak())
                .with("%kdr%", String.format(Locale.ROOT, "%.2f", row.killDeathRatio()))
                .with("%playtime%", Durations.format(row.playtimeSeconds(now)));
    }

    private void renderEconomy(LineRenderer out, Player player) {
        out.with("%balance%", economy == null || economy.getManager() == null
                ? ""
                : economy.getManager().format(economy.getManager().getBalance(player.getUniqueId())));
    }

    private void renderCombat(LineRenderer out, Player player, long now) {
        if (pvp == null || pvp.getCombatTags() == null) {
            out.with("%combat%", "").with("%combat_line%", "")
                    .with("%pearl%", "").with("%pearl_line%", "")
                    .with("%deathban%", "").with("%deathban_line%", "");
            return;
        }
        for (PvpSettings.ItemCooldown item : pvp.getSettings().itemCooldowns().items()) {
            long left = pvp.getSettings().itemCooldowns().enabled() ? pvp.itemCooldownLeft(player, item) : 0L;
            out.with("%cooldown_" + item.id() + "%", left > 0 ? Durations.formatWithSeconds(left) : "");
            out.with("%cooldown_" + item.id() + "_line%", left > 0
                    ? lang.get(UiMessages.ITEM_COOLDOWN_LINE, "item", item.name(), "time", Durations.formatWithSeconds(left))
                    : "");
        }
        long pearlSeconds = pvp.pearlSecondsLeft(player.getUniqueId());
        out.with("%pearl%", pearlSeconds > 0 ? Durations.formatWithSeconds(pearlSeconds) : "");
        out.with("%pearl_line%", pearlSeconds > 0
                ? lang.get(UiMessages.PEARL_LINE, "time", Durations.formatWithSeconds(pearlSeconds))
                : "");
        long tagSeconds = pvp.getCombatTags().getRemainingSeconds(player.getUniqueId());
        out.with("%combat%", tagSeconds > 0 ? Durations.formatWithSeconds(tagSeconds) : "");
        out.with("%combat_line%", tagSeconds > 0
                ? lang.get(UiMessages.COMBAT_LINE, "time", Durations.formatWithSeconds(tagSeconds))
                : "");
        // A deathbanned player is not online to read a scoreboard, so this is always
        // empty. The placeholder stays so an older ui.yml that lists it renders rather
        // than printing the raw text; the shipped board no longer does.
        out.with("%deathban%", "").with("%deathban_line%", "");
    }

    private void renderPhase(LineRenderer out) {
        String line = "";
        if (phases != null && phases.getManager() != null) {
            if (phases.getManager().isSotw()) {
                line = lang.get(UiMessages.PHASE_SOTW,
                        "time", Durations.formatWithSeconds(phases.getManager().getSotwRemainingSeconds()));
            } else if (phases.getManager().isEotw()) {
                line = lang.get(UiMessages.PHASE_EOTW);
            } else if (phases.getManager().isPurge()) {
                line = lang.get(UiMessages.PHASE_PURGE,
                        "time", Durations.formatWithSeconds(phases.getManager().getPurgeRemainingSeconds()));
            }
        }
        out.with("%phase_line%", line);
    }

    private void renderTerritory(LineRenderer out, Player player) {
        out.with("%territory%", claims == null || claims.getManager() == null
                ? ""
                : claims.ownerAt(player.getLocation())
                        .map(Team::getName)
                        .orElse(""));
    }

    public void reloadSettings() {
        var section = ConfigManager.loadFile(plugin, "ui.yml");
        this.settings = UiSettingsLoader.load(section,
                warning -> plugin.getLogger().warning("ui.yml: " + warning));
        // Titles and lines may both have changed: rebuild rather than patch. A player
        // whose board is now switched off goes back to the main scoreboard, as in
        // refresh(), rather than keeping a board nothing updates any more.
        java.util.Set<UUID> hadBoard = java.util.Set.copyOf(boards.keySet());
        boards.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (settings.scoreboard().enabled() && wantsBoard.test(player)) {
                attach(player);
            } else if (hadBoard.contains(player.getUniqueId())) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        startUpdating();
        tabList.restart();
    }

    public void disable() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        tabList.shutdown();
        boards.clear();
    }
}
