package com.lawkeys.hcfcore.integration.lunar;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * What one Lunar Client player has been sent of one kind - waypoints, cooldowns or
 * nametags - so that each update sends only what changed.
 *
 * <p>Pure Java. The wanted state is recomputed from the game every update; this
 * compares it with what was sent and answers with what to send and what to remove.
 * An entry judged the {@code same} keeps the value that was actually sent, so a
 * tolerance (a cooldown's end, recomputed from whole seconds) is measured against
 * what the client shows, and never drifts.
 *
 * @param <V> what is sent under each name
 */
public final class SentState<V> {

    /**
     * What one update must do: send these (new or changed), remove those (gone).
     *
     * @param replaced the names in {@code send} that were already on the client with
     *                 another value
     */
    public record Plan<V>(Map<String, V> send, Set<String> remove, Set<String> replaced) {

        public boolean isEmpty() {
            return send.isEmpty() && remove.isEmpty();
        }
    }

    private final Map<String, V> sent = new HashMap<>();

    /**
     * Records the plan as done: after this call, what was sent is {@code wanted}.
     *
     * @param same whether a value already sent still stands for the one now wanted
     */
    public Plan<V> reconcile(Map<String, V> wanted, BiPredicate<V, V> same) {
        Objects.requireNonNull(wanted, "wanted");
        Map<String, V> send = new LinkedHashMap<>();
        Set<String> replaced = new LinkedHashSet<>();
        for (Map.Entry<String, V> entry : wanted.entrySet()) {
            V before = sent.get(entry.getKey());
            if (before == null || !same.test(before, entry.getValue())) {
                send.put(entry.getKey(), entry.getValue());
                if (before != null) {
                    replaced.add(entry.getKey());
                }
            }
        }
        Set<String> remove = new LinkedHashSet<>();
        for (String name : sent.keySet()) {
            if (!wanted.containsKey(name)) {
                remove.add(name);
            }
        }
        sent.keySet().removeAll(remove);
        sent.putAll(send);
        return new Plan<>(send, remove, replaced);
    }

    /** Forgets one entry without removing it from the client - for a target that has left. */
    public void forget(String name) {
        sent.remove(name);
    }

    public boolean isEmpty() {
        return sent.isEmpty();
    }

    public Set<String> names() {
        return Set.copyOf(sent.keySet());
    }

    public void clear() {
        sent.clear();
    }
}
