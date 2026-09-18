package com.lawkeys.hcfcore.staff.strike;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * What a team's strikes cost it, count by count.
 *
 * <p>The project owner's example (12/09/2026): a strike when a member is banned for
 * cheating, half the team's points lost with each, and the team disbanded at the
 * third. That is what {@code staff.yml} ships; the numbers are all configuration.
 *
 * <p>A rung fires at exactly its count, like a killstreak reward: a team reaching its
 * third strike was already sanctioned at the first and second on the way.
 */
public final class StrikeLadder {

    /**
     * What one rung does.
     *
     * @param pointsLossPercent share of the team's points taken, 0 to 100
     * @param disband           whether the team is disbanded
     * @param commands          console commands, {@code %team%} and {@code %strikes%}
     *                          filled in - for anything else, from any plugin
     */
    public record Sanction(int pointsLossPercent, boolean disband, List<String> commands) {

        public Sanction {
            pointsLossPercent = Math.max(0, Math.min(100, pointsLossPercent));
            commands = List.copyOf(Objects.requireNonNullElseGet(commands, List::<String>of));
        }

        public boolean doesNothing() {
            return pointsLossPercent == 0 && !disband && commands.isEmpty();
        }
    }

    private static final StrikeLadder EMPTY = new StrikeLadder(Map.of());

    private final Map<Integer, Sanction> rungs;

    private StrikeLadder(Map<Integer, Sanction> rungs) {
        this.rungs = Map.copyOf(rungs);
    }

    public static StrikeLadder empty() {
        return EMPTY;
    }

    public static StrikeLadder of(Map<Integer, Sanction> rungs, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        Map<Integer, Sanction> checked = new LinkedHashMap<>();
        for (Map.Entry<Integer, Sanction> entry : rungs.entrySet()) {
            if (entry.getKey() == null || entry.getKey() <= 0) {
                warn.accept("a sanction is set for " + entry.getKey()
                        + " strikes, which can never be reached; ignored.");
                continue;
            }
            if (entry.getValue() == null || entry.getValue().doesNothing()) {
                warn.accept("the sanction for " + entry.getKey() + " strikes does nothing; ignored.");
                continue;
            }
            checked.put(entry.getKey(), entry.getValue());
        }
        return checked.isEmpty() ? EMPTY : new StrikeLadder(checked);
    }

    /** @return the sanction for exactly this many active strikes, if the ladder has one */
    public Optional<Sanction> at(int activeStrikes) {
        return Optional.ofNullable(rungs.get(activeStrikes));
    }

    /** @return the lowest count that disbands a team - "2 of 3" reads better than "2" */
    public OptionalInt disbandAt() {
        return rungs.entrySet().stream().filter(entry -> entry.getValue().disband())
                .mapToInt(Map.Entry::getKey).min();
    }

    /**
     * @return the points a sanction takes: {@code percent} of what the team has,
     *         rounded down, and nothing from a team with none
     */
    public static long pointsLost(long points, int percent) {
        if (points <= 0 || percent <= 0) {
            return 0L;
        }
        int share = Math.min(100, percent);
        // Split so that points * percent cannot overflow, whatever the score.
        return points / 100 * share + points % 100 * share / 100;
    }

    /** @return the commands with the team's name and count filled in */
    public static List<String> fill(List<String> commands, String teamName, int count) {
        return commands.stream()
                .map(command -> command
                        .replace("%team%", teamName == null ? "" : teamName)
                        .replace("%strikes%", String.valueOf(count)))
                .toList();
    }

    public boolean isEmpty() {
        return rungs.isEmpty();
    }

    /** @return the configured counts, lowest first */
    public List<Integer> steps() {
        List<Integer> steps = new ArrayList<>(rungs.keySet());
        steps.sort(Integer::compare);
        return steps;
    }
}
