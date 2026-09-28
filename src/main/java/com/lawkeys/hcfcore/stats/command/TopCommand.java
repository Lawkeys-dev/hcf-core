package com.lawkeys.hcfcore.stats.command;

import com.lawkeys.hcfcore.stats.PlayerStats;
import com.lawkeys.hcfcore.stats.StatsManager.Ranking;
import com.lawkeys.hcfcore.stats.StatsMessages;
import com.lawkeys.hcfcore.stats.StatsModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * {@code /leaderboard} - the leaderboards.
 *
 * <p>Not {@code /top}: FEATURES.md section 10 gives that name to the
 * teleport-to-the-highest-block command, which is what every server's players
 * expect it to do.
 *
 * <p>Rendered from the cache on demand rather than from a stored ranking: a
 * leaderboard is read far less often than a kill is recorded, so the sorting
 * belongs on the read.
 */
public final class TopCommand implements TabExecutor {

    /** How many rows a leaderboard shows. */
    private static final int ROWS = 10;

    /** Every board, in the window's order: the players', then the teams'. */
    static final List<String> KINDS =
            List.of("kills", "deaths", "kdr", "killstreak", "playtime", "team-kills", "team-points");

    private final StatsModule module;

    public TopCommand(StatsModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        // No argument: the window, every board at once - or the kills in the chat.
        if (args.length == 0 && sender instanceof org.bukkit.entity.Player player
                && module.getLeaderboardMenu().enabled()) {
            LeaderboardMenu.open(module, player);
            return true;
        }
        String kind = args.length > 0 ? canonical(args[0].toLowerCase(Locale.ROOT)) : "kills";
        if (kind == null) {
            module.getLang().send(sender, StatsMessages.TOP_USAGE,
                    "kinds", String.join(", ", KINDS));
            return true;
        }

        List<String[]> top = rows(module, kind, ROWS);
        if (top.isEmpty()) {
            module.getLang().send(sender, StatsMessages.TOP_EMPTY);
            return true;
        }
        module.getLang().send(sender, StatsMessages.TOP_HEADER, "kind", module.getLang().get(StatsMessages.board(kind)));
        for (int i = 0; i < top.size(); i++) {
            module.getLang().send(sender, StatsMessages.TOP_ENTRY,
                    "rank", String.valueOf(i + 1),
                    "player", top.get(i)[0],
                    "value", top.get(i)[1]);
        }
        return true;
    }

    /** @return the board's name as the window lists it, from any of its spellings; null if none */
    private static String canonical(String typed) {
        return switch (typed) {
            case "kills" -> "kills";
            case "deaths" -> "deaths";
            case "kdr", "ratio" -> "kdr";
            case "killstreak", "streak" -> "killstreak";
            case "playtime", "time" -> "playtime";
            case "team-kills", "teamkills", "fkills", "faction-kills" -> "team-kills";
            case "team-points", "teampoints", "points", "faction-points" -> "team-points";
            default -> null;
        };
    }

    /** @return a board's top rows, each a name and its value as shown */
    static List<String[]> rows(StatsModule module, String kind, int limit) {
        List<String[]> rows = new ArrayList<>();
        if (kind.equals("team-kills") || kind.equals("team-points")) {
            var boards = module.getTeamBoards();
            for (var row : kind.equals("team-kills") ? boards.byKills(limit) : boards.byPoints(limit)) {
                rows.add(new String[] {row.name(), row.value()});
            }
            return rows;
        }
        Ranking ranking = switch (kind) {
            case "deaths" -> Ranking.DEATHS;
            case "kdr" -> Ranking.KILL_DEATH_RATIO;
            case "killstreak" -> Ranking.HIGHEST_KILLSTREAK;
            case "playtime" -> Ranking.PLAYTIME;
            default -> Ranking.KILLS;
        };
        long now = System.currentTimeMillis();
        for (PlayerStats stats : module.getManager().top(ranking, limit)) {
            rows.add(new String[] {stats.getName(), render(ranking, stats, now)});
        }
        return rows;
    }

    private static String render(Ranking ranking, PlayerStats stats, long now) {
        return switch (ranking) {
            case KILLS -> String.valueOf(stats.getKills());
            case DEATHS -> String.valueOf(stats.getDeaths());
            case KILL_DEATH_RATIO -> String.format(Locale.ROOT, "%.2f", stats.killDeathRatio());
            case HIGHEST_KILLSTREAK -> String.valueOf(stats.getHighestKillstreak());
            case PLAYTIME -> Durations.format(stats.playtimeSeconds(now));
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        for (String kind : KINDS) {
            if (kind.startsWith(prefix)) {
                options.add(kind);
            }
        }
        return options;
    }
}
