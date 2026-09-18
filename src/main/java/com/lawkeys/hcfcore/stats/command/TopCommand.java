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

    private static final List<String> KINDS =
            List.of("kills", "deaths", "kdr", "killstreak", "playtime");

    private final StatsModule module;

    public TopCommand(StatsModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        String kind = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "kills";
        Ranking ranking = switch (kind) {
            case "kills" -> Ranking.KILLS;
            case "deaths" -> Ranking.DEATHS;
            case "kdr", "ratio" -> Ranking.KILL_DEATH_RATIO;
            case "killstreak", "streak" -> Ranking.HIGHEST_KILLSTREAK;
            case "playtime", "time" -> Ranking.PLAYTIME;
            default -> null;
        };
        if (ranking == null) {
            module.getLang().send(sender, StatsMessages.TOP_USAGE,
                    "kinds", String.join(", ", KINDS));
            return true;
        }

        List<PlayerStats> top = module.getManager().top(ranking, ROWS);
        if (top.isEmpty()) {
            module.getLang().send(sender, StatsMessages.TOP_EMPTY);
            return true;
        }
        long now = System.currentTimeMillis();
        module.getLang().send(sender, StatsMessages.TOP_HEADER, "kind", kind);
        for (int i = 0; i < top.size(); i++) {
            module.getLang().send(sender, StatsMessages.TOP_ENTRY,
                    "rank", String.valueOf(i + 1),
                    "player", top.get(i).getName(),
                    "value", render(ranking, top.get(i), now));
        }
        return true;
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
