package com.lawkeys.hcfcore.killstreak;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The reward table, by streak.
 *
 * <p>Pure Java, and the whole of the module's logic: given a streak, which reward -
 * if any - fires.
 *
 * <p><strong>Exactly one reward fires per kill, the one matching that streak.</strong>
 * Not "every reward up to it": a player reaching 10 has already been given the
 * rewards for 5 and for 3 on the way, and handing them out again would multiply
 * every table by itself. A streak that skips a configured number - impossible with
 * kills, possible if something else ever sets a streak - simply misses it.
 */
public final class KillstreakRewards {

    private static final KillstreakRewards EMPTY = new KillstreakRewards(Map.of());

    private final Map<Integer, KillstreakReward> byStreak;

    private KillstreakRewards(Map<Integer, KillstreakReward> byStreak) {
        this.byStreak = Map.copyOf(byStreak);
    }

    /** No rewards configured - which is the shipped default, since the table is the operator's. */
    public static KillstreakRewards empty() {
        return EMPTY;
    }

    /** @param warn told about every reward dropped, and why */
    public static KillstreakRewards of(List<KillstreakReward> rewards, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        Map<Integer, KillstreakReward> byStreak = new LinkedHashMap<>();
        for (KillstreakReward reward : Objects.requireNonNullElseGet(rewards, List::<KillstreakReward>of)) {
            if (reward == null) {
                continue;
            }
            if (reward.streak() <= 0) {
                warn.accept("a reward is set for streak " + reward.streak()
                        + ", which can never be reached; ignored.");
                continue;
            }
            if (!reward.hasBroadcast() && reward.commands().isEmpty()) {
                warn.accept("the reward for streak " + reward.streak()
                        + " has neither a broadcast nor any command, so it would do nothing; ignored.");
                continue;
            }
            if (byStreak.putIfAbsent(reward.streak(), reward) != null) {
                warn.accept("two rewards are set for streak " + reward.streak()
                        + "; keeping the first.");
            }
        }
        return byStreak.isEmpty() ? EMPTY : new KillstreakRewards(byStreak);
    }

    /** @return the reward for exactly this streak, if one is configured */
    public Optional<KillstreakReward> at(int streak) {
        return Optional.ofNullable(byStreak.get(streak));
    }

    public boolean isEmpty() {
        return byStreak.isEmpty();
    }

    public int size() {
        return byStreak.size();
    }

    /** @return the configured streaks, lowest first, for a {@code /killstreaks} listing */
    public List<KillstreakReward> all() {
        List<KillstreakReward> all = new ArrayList<>(byStreak.values());
        all.sort((a, b) -> Integer.compare(a.streak(), b.streak()));
        return all;
    }
}
