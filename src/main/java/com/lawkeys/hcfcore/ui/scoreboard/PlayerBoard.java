package com.lawkeys.hcfcore.ui.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's sidebar, updated in place.
 *
 * <p><strong>Written against the official Bukkit scoreboard API rather than a
 * packet library.</strong> CONTRIBUTING.md's stack table suggests FastBoard "to avoid
 * reinventing the packet-based scoreboard" - but nothing here reinvents packets: it
 * is the documented API, every method of it checked in the Paper 26.2 sources. The
 * two problems a packet library exists to solve are both solved without one. Flicker
 * comes from clearing and refilling the board each tick, and is avoided by writing
 * the text into team prefixes that are updated in place. The ugly red score numbers
 * down the right-hand side are removed by {@code Objective#numberFormat}, which
 * Paper added for exactly this. The result is one less shaded dependency.
 *
 * <p>Each row owns a hidden entry - a unique colour code - that never changes, so
 * the row keeps its slot while its text is rewritten.
 */
public final class PlayerBoard {

    private static final String OBJECTIVE = "hcfcore";

    /** Hidden entries, one per row: a colour code renders as nothing at all. */
    private static final String[] ENTRIES = {
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e"
    };

    private final Scoreboard board;
    private final Objective objective;
    private final List<Team> rows = new ArrayList<>();

    private int visibleRows;

    public PlayerBoard(String title) {
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, component(title));
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        // Hides the red number beside every row. Paper-only, and the reason no packet
        // library is needed for a clean board.
        this.objective.numberFormat(NumberFormat.blank());

        for (int i = 0; i < ENTRIES.length; i++) {
            Team row = board.registerNewTeam("row" + i);
            row.addEntry(ENTRIES[i]);
            rows.add(row);
        }
    }

    public void show(Player player) {
        player.setScoreboard(board);
    }

    public void title(String title) {
        objective.displayName(component(title));
    }

    /**
     * Rewrites the board.
     *
     * <p>Rows are scored from the bottom up so the first line given is the top line
     * shown, which is how everybody writes a board in configuration and not how the
     * scoreboard API orders things.
     */
    public void update(List<String> lines) {
        int count = Math.min(lines.size(), ENTRIES.length);
        for (int i = 0; i < count; i++) {
            rows.get(i).prefix(component(lines.get(i)));
            // Only score a row the first time it appears; re-scoring an existing one
            // makes the client redraw it, which is the flicker being avoided.
            if (i >= visibleRows) {
                objective.getScore(ENTRIES[i]).setScore(count - i);
            }
        }
        for (int i = count; i < visibleRows; i++) {
            board.resetScores(ENTRIES[i]);
        }
        // Scores encode position, so a board that changed length has to renumber the
        // rows that stayed.
        if (count != visibleRows) {
            for (int i = 0; i < count; i++) {
                objective.getScore(ENTRIES[i]).setScore(count - i);
            }
        }
        visibleRows = count;
    }

    private static net.kyori.adventure.text.Component component(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }
}
