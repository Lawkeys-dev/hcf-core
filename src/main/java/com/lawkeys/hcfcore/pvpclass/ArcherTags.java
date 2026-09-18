package com.lawkeys.hcfcore.pvpclass;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is archer-tagged, until when, and how much more damage it makes them take.
 * Memory only: a mark lasts seconds.
 */
public final class ArcherTags {

    private record Mark(long until, double multiplier) {
    }

    private final Map<UUID, Mark> marks = new ConcurrentHashMap<>();

    /** Marks a player; a new mark replaces the running one, so a fresh hit starts it over. */
    public void tag(UUID victim, ArcherTag tag, long now) {
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(tag, "tag");
        marks.put(victim, new Mark(now + tag.seconds() * 1000L, tag.damageMultiplier()));
    }

    /** @return what the damage this player takes is multiplied by: {@code 1.0} when unmarked */
    public double multiplier(UUID victim, long now) {
        Mark mark = marks.get(victim);
        if (mark == null) {
            return 1.0;
        }
        if (now >= mark.until()) {
            marks.remove(victim, mark);
            return 1.0;
        }
        return mark.multiplier();
    }

    /** @return whole seconds left of the mark, rounded up, or {@code 0} */
    public long remaining(UUID victim, long now) {
        Mark mark = marks.get(victim);
        if (mark == null || now >= mark.until()) {
            return 0;
        }
        return (mark.until() - now + 999) / 1000;
    }

    public void forget(UUID victim) {
        marks.remove(victim);
    }

    public void clear() {
        marks.clear();
    }
}
