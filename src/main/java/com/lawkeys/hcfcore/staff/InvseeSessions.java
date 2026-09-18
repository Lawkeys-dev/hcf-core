package com.lawkeys.hcfcore.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which staff members currently have somebody else's inventory open, and whether
 * they are allowed to change it.
 *
 * <p>The window itself belongs to the server; this only remembers what the window
 * means, so that a click in it can be judged. Without it a click in a player
 * inventory is indistinguishable from a staff member tidying their own.
 *
 * <p>Memory only, and cleared when the window closes, or when the viewer or the
 * player looked at leaves.
 */
public final class InvseeSessions {

    /** @param editable whether this viewer may change what they are looking at */
    public record Session(UUID viewerId, UUID targetId, boolean editable) {

        public Session {
            Objects.requireNonNull(viewerId, "viewerId");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    private final Map<UUID, Session> open = new ConcurrentHashMap<>();

    public void open(UUID viewerId, UUID targetId, boolean editable) {
        open.put(Objects.requireNonNull(viewerId, "viewerId"),
                new Session(viewerId, targetId, editable));
    }

    public Optional<Session> of(UUID viewerId) {
        return Optional.ofNullable(open.get(viewerId));
    }

    /**
     * @return whether this viewer is looking at somebody else's inventory and may
     *         <strong>not</strong> change it - the question a click handler asks
     */
    public boolean isReadOnlyFor(UUID viewerId) {
        Session session = open.get(viewerId);
        return session != null && !session.editable();
    }

    public void close(UUID viewerId) {
        open.remove(viewerId);
    }

    /** @return the staff members who have this player's inventory open */
    public List<UUID> viewersOf(UUID targetId) {
        List<UUID> viewers = new ArrayList<>();
        for (Session session : open.values()) {
            if (session.targetId().equals(targetId)) {
                viewers.add(session.viewerId());
            }
        }
        return viewers;
    }

    public int size() {
        return open.size();
    }

    public void clearAll() {
        open.clear();
    }
}
