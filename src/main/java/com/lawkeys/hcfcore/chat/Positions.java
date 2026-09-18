package com.lawkeys.hcfcore.chat;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Where every online player stood at the last refresh, for local chat.
 *
 * <p>Chat arrives on its own thread, and the Paper documentation is explicit about
 * what may not be done there: "If a method changes or accesses the world state, it
 * is not safe to be used from an asynchronous task." A player's position is world
 * state. So the main thread copies positions here every few ticks, and the chat
 * thread reads only the copy. Half a second stale is nothing when deciding who hears
 * a sentence.
 *
 * <p>Pure Java; the copy is replaced whole, never edited, so a reader sees one
 * refresh or the next and never half of each.
 */
public final class Positions {

    /** One player's position, with the world as an id so nothing here touches the server. */
    public record Position(UUID world, double x, double y, double z) {

        public Position {
            Objects.requireNonNull(world, "world");
        }
    }

    private volatile Map<UUID, Position> latest = Map.of();

    public void replace(Map<UUID, Position> positions) {
        this.latest = Map.copyOf(positions);
    }

    public void clear() {
        this.latest = Map.of();
    }

    /**
     * Whether two players are within {@code range} blocks of each other.
     *
     * <p>A player missing from the copy - one who joined since the last refresh -
     * counts as in range. Local chat is a courtesy, not a secret: a line that reaches
     * somebody a little too far away for half a second is better than one lost.
     */
    public boolean within(UUID a, UUID b, int range) {
        Map<UUID, Position> snapshot = latest;
        Position from = snapshot.get(a);
        Position to = snapshot.get(b);
        if (from == null || to == null) {
            return true;
        }
        if (!from.world().equals(to.world())) {
            return false;
        }
        double dx = from.x() - to.x();
        double dy = from.y() - to.y();
        double dz = from.z() - to.z();
        return dx * dx + dy * dy + dz * dz <= (double) range * range;
    }
}
