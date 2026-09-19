package com.lawkeys.hcfcore.ui;

import com.lawkeys.hcfcore.chat.ChatDecorations;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.mode.GameMode;
import com.lawkeys.hcfcore.stats.StatsModule;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamRole;
import com.lawkeys.hcfcore.ui.tab.GridTab;
import com.lawkeys.hcfcore.ui.tab.HeadTag;
import com.lawkeys.hcfcore.ui.tab.TabGrid;
import com.lawkeys.hcfcore.ui.tab.TabStyle;
import com.lawkeys.hcfcore.util.LegacyText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

/**
 * The tab list (FEATURES.md section 9): the HCF grid or the classic list, as
 * {@code ui.yml} says - the project owner's request of 19/09/2026.
 *
 * <p>Redrawn on its own timer, whatever the scoreboard does, and only what changed
 * is sent again: a cell, a name, a header. Switching styles on a reload takes the
 * old one away completely - grid cells, written names, list order - before the new
 * one is drawn.
 */
final class TabList {

    private static final int MEMBER_ROWS = 20;
    private static final int TOP_TEAM_ROWS = 10;

    private final Plugin plugin;
    private final UiModule ui;
    private final LangManager lang;
    private final TeamModule teams;
    private final StatsModule stats;
    private final ClaimModule claims;
    private final DoubleFunction<String> money;

    private GameMode mode = GameMode.HCF;
    private Supplier<ChatDecorations> decorations = () -> ChatDecorations.NONE;
    private GridTab grid;

    private TabStyle active;
    private BukkitTask task;
    private boolean warnedNoGrid;

    private final Map<UUID, List<String>> shownCells = new ConcurrentHashMap<>();
    private final Map<UUID, List<GridTab.Skin>> shownHeads = new ConcurrentHashMap<>();
    /** Heads by player, taken from them while online, or asked of Mojang once. */
    private final Map<UUID, GridTab.Skin> playerHeads = new ConcurrentHashMap<>();
    /** Heads by account name ({@code [head:MHF_Chest]}); empty when the account has none. */
    private final Map<String, Optional<GridTab.Skin>> accountHeads = new ConcurrentHashMap<>();
    private final java.util.Set<Object> fetching = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> shownHeader = new ConcurrentHashMap<>();
    private final Map<UUID, String> shownName = new ConcurrentHashMap<>();
    /** Offline members' names, looked up once: never a disk read every second. */
    private final Map<UUID, String> names = new ConcurrentHashMap<>();

    TabList(Plugin plugin, UiModule ui, LangManager lang, TeamModule teams, StatsModule stats, ClaimModule claims,
            DoubleFunction<String> money) {
        this.plugin = plugin;
        this.ui = ui;
        this.lang = lang;
        this.teams = teams;
        this.stats = stats;
        this.claims = claims;
        this.money = money;
    }

    void setSources(GameMode mode, Supplier<ChatDecorations> decorations, GridTab grid) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.decorations = Objects.requireNonNull(decorations, "decorations");
        this.grid = grid;
    }

    /** (Re)starts from the current settings, clearing whatever the last ones drew. */
    void restart() {
        stop();
        UiSettings.TablistRules rules = ui.getSettings().tablist();
        if (!rules.enabled()) {
            return;
        }
        TabStyle style = rules.style().resolve(mode);
        if (style == TabStyle.HCF && grid == null) {
            if (!warnedNoGrid) {
                plugin.getLogger().warning("ui.yml: the HCF tab list needs the PacketEvents plugin, which is not "
                        + "running; showing the classic tab list instead.");
                warnedNoGrid = true;
            }
            style = TabStyle.CLASSIC;
        }
        this.active = style;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, rules.updateTicks());
    }

    /** Takes everything this drew away, from everybody. */
    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        shownCells.clear();
        shownHeads.clear();
        shownHeader.clear();
        shownName.clear();
        active = null;
    }

    /** Stops for good: the grid stops listening too. */
    void shutdown() {
        stop();
        if (grid != null) {
            grid.stop();
        }
    }

    void forget(UUID playerId) {
        shownCells.remove(playerId);
        shownHeads.remove(playerId);
        shownHeader.remove(playerId);
        shownName.remove(playerId);
        if (grid != null) {
            grid.forget(playerId);
        }
    }

    private void clear(Player player) {
        UUID id = player.getUniqueId();
        if (shownCells.containsKey(id) && grid != null) {
            grid.hide(player);
        }
        if (shownName.containsKey(id)) {
            player.playerListName(null);
            player.setPlayerListOrder(0);
        }
        if (shownHeader.containsKey(id)) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    private void tick() {
        UiSettings.TablistRules rules = ui.getSettings().tablist();
        if (active == null) {
            return;
        }
        List<Team> top = teams == null || teams.getManager() == null
                ? List.of() : teams.getManager().getTopTeamsByPoints(TOP_TEAM_ROWS);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            LineRenderer out = ui.renderer(viewer);
            List<UUID> members = addTabValues(out, viewer, top);
            boolean hcf = active == TabStyle.HCF;
            List<String> header = hcf ? rules.hcf().header() : rules.classic().header();
            List<String> footer = hcf ? rules.hcf().footer() : rules.classic().footer();
            drawHeader(viewer, out, header, footer);
            if (hcf) {
                drawGrid(viewer, out, rules.hcf(), members, top);
            } else {
                drawName(viewer, rules.classic());
            }
        }
    }

    private void drawHeader(Player viewer, LineRenderer out, List<String> header, List<String> footer) {
        String top = LangManager.colorize(String.join("\n", header.stream().map(out::render).toList()));
        String bottom = LangManager.colorize(String.join("\n", footer.stream().map(out::render).toList()));
        String both = top + '\u0000' + bottom;
        if (!both.equals(shownHeader.put(viewer.getUniqueId(), both))) {
            viewer.sendPlayerListHeaderAndFooter(LegacyText.SERIALIZER.deserialize(top),
                    LegacyText.SERIALIZER.deserialize(bottom));
        }
    }

    private void drawGrid(Player viewer, LineRenderer out, UiSettings.GridRules rules, List<UUID> members,
                          List<Team> top) {
        List<String> cells = new ArrayList<>(TabGrid.SIZE);
        List<GridTab.Skin> heads = new ArrayList<>(TabGrid.SIZE);
        for (String template : TabGrid.cells(rules.columns())) {
            HeadTag tag = HeadTag.parse(template);
            String text = tag.text();
            boolean blank = text.isEmpty() || out.isEmptyRow(text);
            cells.add(blank ? "" : LangManager.colorize(out.render(text)));
            // A blank cell keeps the default head: an empty %member_5% shows nobody's face.
            heads.add(blank ? null : head(tag, viewer, members, top));
        }
        GridTab.Look look = new GridTab.Look(rules.latency(), rules.texture(), rules.signature());
        UUID id = viewer.getUniqueId();
        List<String> before = shownCells.put(id, cells);
        List<GridTab.Skin> headsBefore = shownHeads.put(id, heads);
        if (before == null) {
            List<GridTab.Cell> all = new ArrayList<>(TabGrid.SIZE);
            for (int i = 0; i < TabGrid.SIZE; i++) {
                all.add(new GridTab.Cell(component(cells.get(i)), heads.get(i)));
            }
            grid.show(viewer, all, look);
            return;
        }
        Map<Integer, GridTab.Cell> texts = new LinkedHashMap<>();
        Map<Integer, GridTab.Cell> reheaded = new LinkedHashMap<>();
        for (int i = 0; i < TabGrid.SIZE; i++) {
            GridTab.Cell cell = new GridTab.Cell(component(cells.get(i)), heads.get(i));
            if (!Objects.equals(headsBefore == null ? null : headsBefore.get(i), heads.get(i))) {
                reheaded.put(i, cell);
            } else if (!before.get(i).equals(cells.get(i))) {
                texts.put(i, cell);
            }
        }
        grid.update(viewer, texts, reheaded, look);
    }

    /** The head a cell asks for, or {@code null} for the default - also while it is still being fetched. */
    private GridTab.Skin head(HeadTag tag, Player viewer, List<UUID> members, List<Team> top) {
        return switch (tag.kind()) {
            case NONE -> null;
            case SELF -> playerHead(viewer.getUniqueId());
            case MEMBER -> tag.index() <= members.size() ? playerHead(members.get(tag.index() - 1)) : null;
            case TOP -> tag.index() <= top.size()
                    ? top.get(tag.index() - 1).getLeader().map(this::playerHead).orElse(null) : null;
            case ACCOUNT -> accountHead(tag.name());
            case MINESKIN -> mineskinHead(tag.name());
        };
    }

    private GridTab.Skin playerHead(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            GridTab.Skin skin = texturesOf(online.getPlayerProfile());
            if (skin != null) {
                playerHeads.put(id, skin);
                return skin;
            }
        }
        GridTab.Skin known = playerHeads.get(id);
        if (known == null && fetching.add(id)) {
            // An offline member: asked of Mojang once, off the main thread.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                var profile = Bukkit.createProfile(id);
                if (profile.complete(true)) {
                    GridTab.Skin skin = texturesOf(profile);
                    if (skin != null) {
                        playerHeads.put(id, skin);
                    }
                }
            });
        }
        return known;
    }

    private GridTab.Skin accountHead(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Optional<GridTab.Skin> known = accountHeads.get(key);
        if (known != null) {
            return known.orElse(null);
        }
        if (fetching.add(key)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                var profile = Bukkit.createProfile(name);
                boolean found = profile.complete(true);
                accountHeads.put(key, Optional.ofNullable(found ? texturesOf(profile) : null));
                if (!found) {
                    plugin.getLogger().warning("ui.yml: no Minecraft account named '" + name
                            + "' for a [head:" + name + "] of the tab list; the cell shows the default head.");
                }
            });
        }
        return null;
    }

    /** A skin of mineskin.org, by id: asked of its API once, off the main thread. */
    private GridTab.Skin mineskinHead(String id) {
        String key = "mineskin:" + id;
        Optional<GridTab.Skin> known = accountHeads.get(key);
        if (known != null) {
            return known.orElse(null);
        }
        if (fetching.add(key)) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                GridTab.Skin skin = null;
                try {
                    var request = java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create("https://api.mineskin.org/v2/skins/" + id))
                            .header("User-Agent", "HCFCore/" + plugin.getPluginMeta().getVersion())
                            .timeout(java.time.Duration.ofSeconds(15))
                            .GET().build();
                    var response = java.net.http.HttpClient.newHttpClient()
                            .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        var data = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject()
                                .getAsJsonObject("skin").getAsJsonObject("texture").getAsJsonObject("data");
                        skin = new GridTab.Skin(data.get("value").getAsString(), data.get("signature").getAsString());
                    }
                } catch (java.io.IOException | RuntimeException e) {
                    skin = null;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                accountHeads.put(key, Optional.ofNullable(skin));
                if (skin == null) {
                    plugin.getLogger().warning("ui.yml: mineskin.org has no skin " + id + " for a [head:mineskin:"
                            + id + "] of the tab list, or could not be reached; the cell shows the default head.");
                }
            });
        }
        return null;
    }

    private static GridTab.Skin texturesOf(com.destroystokyo.paper.profile.PlayerProfile profile) {
        for (var property : profile.getProperties()) {
            if (property.getName().equals("textures")) {
                return new GridTab.Skin(property.getValue(), property.getSignature() == null ? "" : property.getSignature());
            }
        }
        return null;
    }

    /** A player's own entry in the classic list: their name as written, their place in the order. */
    private void drawName(Player player, UiSettings.ClassicRules rules) {
        ChatDecorations meta = decorations.get();
        Optional<Team> team = teamOf(player);
        LineRenderer out = new LineRenderer()
                .with("%prefix%", meta.prefix(player))
                .with("%suffix%", meta.suffix(player))
                .with("%player%", player.getName())
                .with("%team_tag%", team.map(t -> lang.get(UiMessages.TAB_TEAM_TAG, "team", t.getName())).orElse(""))
                .with("%team%", team.map(Team::getName).orElse(""))
                .with("%kills%", kills(player))
                .with("%ping%", String.valueOf(player.getPing()));
        String name = LangManager.colorize(out.render(rules.name()));
        if (!name.equals(shownName.put(player.getUniqueId(), name))) {
            player.playerListName(LegacyText.SERIALIZER.deserialize(name));
        }
        int order = switch (rules.sort()) {
            case RANK -> meta.weight(player);
            case KILLS -> stats == null || stats.getManager() == null ? 0
                    : stats.getManager().get(player.getUniqueId(), player.getName()).getKills();
            case NAME -> 0;
        };
        if (player.getPlayerListOrder() != order) {
            player.setPlayerListOrder(order);
        }
    }

    /** The values only the tab list has: where the viewer is, their team's roster, the top teams. */
    private List<UUID> addTabValues(LineRenderer out, Player viewer, List<Team> top) {
        ChatDecorations meta = decorations.get();
        out.with("%prefix%", meta.prefix(viewer)).with("%suffix%", meta.suffix(viewer));

        Location at = viewer.getLocation();
        out.with("%x%", at.getBlockX()).with("%y%", at.getBlockY()).with("%z%", at.getBlockZ());
        String[] directions = lang.get(UiMessages.TAB_DIRECTIONS).split(",");
        float angle = ((at.getYaw() + 180f) % 360f + 360f) % 360f;
        out.with("%direction%", directions.length == 8
                ? directions[Math.round(angle / 45f) % 8].trim() : "");
        Optional<Team> owner = claims == null || claims.getManager() == null
                ? Optional.empty() : claims.getManager().getOwner(ClaimModule.toChunk(at));
        out.with("%location%", owner.map(t -> lang.get(UiMessages.TAB_CLAIM, "team", t.getName()))
                .orElse(lang.get(UiMessages.TAB_WILDERNESS)));

        Optional<Team> team = teamOf(viewer);
        out.with("%team_name_line%", team.map(t -> lang.get(UiMessages.TAB_TEAM_NAME, "team", t.getName()))
                .orElse(lang.get(UiMessages.TAB_NO_TEAM)));
        out.with("%members_title%", team.isPresent() ? lang.get(UiMessages.TAB_MEMBERS_TITLE) : "");
        List<UUID> members = team.map(this::roster).orElse(List.of());
        out.with("%members_total%", team.map(t -> String.valueOf(t.getMemberCount())).orElse(""));
        out.with("%members_online%", team.map(t -> String.valueOf(
                members.stream().filter(id -> Bukkit.getPlayer(id) != null).count())).orElse(""));
        out.with("%team_balance%", team.map(t -> money.apply(t.getBalance())).orElse(""));
        out.with("%team_points%", team.map(t -> String.valueOf(t.getPoints())).orElse(""));
        out.with("%team_leader%", team.flatMap(Team::getLeader).map(this::name).orElse(""));
        for (int i = 0; i < MEMBER_ROWS; i++) {
            String row = "";
            if (i < members.size()) {
                UUID id = members.get(i);
                TeamRole role = team.get().getRole(id).orElse(TeamRole.MEMBER);
                String marker = lang.get(switch (role) {
                    case LEADER -> UiMessages.TAB_ROLE_LEADER;
                    case CO_LEADER -> UiMessages.TAB_ROLE_CO_LEADER;
                    case MEMBER -> UiMessages.TAB_ROLE_MEMBER;
                });
                row = lang.get(Bukkit.getPlayer(id) != null ? UiMessages.TAB_MEMBER_ONLINE : UiMessages.TAB_MEMBER_OFFLINE,
                        "role", marker, "player", name(id));
            }
            out.with("%member_" + (i + 1) + "%", row);
        }
        for (int i = 0; i < TOP_TEAM_ROWS; i++) {
            out.with("%top_team_" + (i + 1) + "%", i < top.size()
                    ? lang.get(UiMessages.TAB_TOP_TEAM, "rank", String.valueOf(i + 1), "team", top.get(i).getName(),
                            "points", String.valueOf(top.get(i).getPoints()))
                    : "");
        }
        boolean quiet = List.of("%phase_line%", "%event_line%", "%king_line%", "%conquest_line%", "%timer_1%")
                .stream().allMatch(key -> out.value(key).isEmpty());
        out.with("%no_event_line%", quiet ? lang.get(UiMessages.TAB_NO_EVENT) : "");
        return members;
    }

    /** Online first, then by rank, then by name. */
    private List<UUID> roster(Team team) {
        return team.getMemberIds().stream()
                .sorted(Comparator.<UUID>comparingInt(id -> Bukkit.getPlayer(id) != null ? 0 : 1)
                        .thenComparing(id -> -team.getRole(id).orElse(TeamRole.MEMBER).weight())
                        .thenComparing(id -> name(id).toLowerCase(Locale.ROOT)))
                .toList();
    }

    private String name(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            names.put(id, online.getName());
            return online.getName();
        }
        return names.computeIfAbsent(id, key -> Objects.requireNonNullElse(Bukkit.getOfflinePlayer(key).getName(), "?"));
    }

    private Optional<Team> teamOf(Player player) {
        return teams == null || teams.getManager() == null
                ? Optional.empty() : teams.getManager().getTeamOf(player.getUniqueId());
    }

    private String kills(Player player) {
        return stats == null || stats.getManager() == null ? ""
                : String.valueOf(stats.getManager().get(player.getUniqueId(), player.getName()).getKills());
    }

    private static Component component(String legacy) {
        return legacy.isEmpty() ? Component.empty() : LegacyText.SERIALIZER.deserialize(legacy);
    }
}
