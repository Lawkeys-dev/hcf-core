package com.lawkeys.hcfcore.schedule;

import com.lawkeys.hcfcore.util.Durations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Countdowns staff start by hand - "Double points: 12:30", "Key-All: 4:59" - which
 * everybody sees on the scoreboard and which can do something when they end.
 *
 * <p>Pure Java; the caller supplies the clock. A timer is an instant it ends at, so
 * nothing drifts when a tick is late. Memory only, like a running KOTH: a restart
 * ends every timer, since restoring a countdown would mean resuming a promotion or a
 * key-all nobody was waiting for any more.
 */
public final class CustomTimers {

    /** @param label what players see, colour codes included */
    public record Timer(String name, String label, long endsAt) {

        public Timer {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(label, "label");
        }

        /** @return whole seconds left, rounded up so a timer never reads 0 before it ends */
        public long remainingSeconds(long now) {
            long remaining = endsAt - now;
            return Durations.secondsLeft(remaining);
        }
    }

    private final Map<String, Timer> running = new ConcurrentHashMap<>();

    /** @return the name as timers are keyed: trimmed and lower case, so "KeyAll" and "keyall" are one */
    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @return {@code false}, changing nothing, if a timer of that name is already
     *         running - staff stop it first, rather than silently resetting a
     *         countdown players are watching
     */
    public boolean start(String name, String label, long endsAt) {
        String key = normalize(name);
        if (key.isEmpty()) {
            return false;
        }
        return running.putIfAbsent(key, new Timer(key, label, endsAt)) == null;
    }

    public Optional<Timer> stop(String name) {
        return Optional.ofNullable(running.remove(normalize(name)));
    }

    public Optional<Timer> get(String name) {
        return Optional.ofNullable(running.get(normalize(name)));
    }

    /** @return the timers still running, the one ending soonest first */
    public List<Timer> running() {
        List<Timer> all = new ArrayList<>(running.values());
        all.sort(Comparator.comparingLong(Timer::endsAt).thenComparing(Timer::name));
        return all;
    }

    /** @return every timer that has ended, removing them, soonest first */
    public List<Timer> pollEnded(long now) {
        List<Timer> ended = new ArrayList<>();
        for (Timer timer : running()) {
            if (now >= timer.endsAt() && running.remove(timer.name(), timer)) {
                ended.add(timer);
            }
        }
        return ended;
    }

    public void clear() {
        running.clear();
    }
}
