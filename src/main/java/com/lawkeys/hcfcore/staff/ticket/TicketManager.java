package com.lawkeys.hcfcore.staff.ticket;

import com.lawkeys.hcfcore.util.Durations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * The queue of open reports and requests.
 *
 * <p>Pure Java. Only open and claimed tickets are held: a closed one is history,
 * and a server that has been running a year would otherwise carry every complaint
 * ever made in memory. Closing writes the row and drops it from the cache.
 *
 * <p><strong>Threading.</strong> Mutations come from the main thread; {@link #flush}
 * runs on an async task. The dirty flag is cleared <em>before</em> a row is read
 * for saving, so a change made while the save is in flight marks it dirty again
 * rather than being lost.
 */
public final class TicketManager {

    private final TicketStore store;
    /** Read on every call, so {@code /hcf reload} applies without a restart. */
    private final LongSupplier cooldownSeconds;
    private final LongSupplier clock;

    private final Map<Long, Ticket> open = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastOpened = new ConcurrentHashMap<>();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    /** Closed tickets waiting to be written, then forgotten. */
    private final Map<Long, Ticket> closedPending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);

    public TicketManager(TicketStore store, LongSupplier cooldownSeconds) {
        this(store, cooldownSeconds, System::currentTimeMillis);
    }

    public TicketManager(TicketStore store, LongSupplier cooldownSeconds, LongSupplier clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.cooldownSeconds = Objects.requireNonNull(cooldownSeconds, "cooldownSeconds");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** @return seconds this player must still wait before opening another */
    public long remainingCooldown(UUID playerId) {
        long cooldown = cooldownSeconds.getAsLong();
        if (cooldown <= 0) {
            return 0L;
        }
        Long last = lastOpened.get(playerId);
        if (last == null) {
            return 0L;
        }
        long remaining = last + cooldown * 1000L - clock.getAsLong();
        return Durations.secondsLeft(remaining);
    }

    /**
     * Opens a ticket.
     *
     * @return the ticket, or empty when the player is still waiting out their
     *         cooldown - which the caller reports rather than silently dropping
     */
    public Optional<Ticket> open(TicketType type, UUID openedBy, String openedName,
                                 UUID target, String targetName, String message) {
        if (remainingCooldown(openedBy) > 0) {
            return Optional.empty();
        }
        long now = clock.getAsLong();
        Ticket ticket = new Ticket(nextId.getAndIncrement(), type, openedBy, openedName,
                target, targetName, message, now, TicketStatus.OPEN, null);
        open.put(ticket.id(), ticket);
        dirty.add(ticket.id());
        lastOpened.put(openedBy, now);
        return Optional.of(ticket);
    }

    public Optional<Ticket> get(long id) {
        return Optional.ofNullable(open.get(id));
    }

    /** @return open and claimed tickets, oldest first - the order they should be dealt with */
    public List<Ticket> queue() {
        List<Ticket> all = new ArrayList<>(open.values());
        all.sort(Comparator.comparingLong(Ticket::openedAt).thenComparingLong(Ticket::id));
        return all;
    }

    public int size() {
        return open.size();
    }

    public long openCount() {
        return open.values().stream().filter(Ticket::isOpen).count();
    }

    /**
     * Marks a ticket as being dealt with.
     *
     * @return empty if there is no such ticket, or if somebody already has it - so
     *         two staff cannot both think it is theirs
     */
    public Optional<Ticket> claim(long id, String staffName) {
        Ticket ticket = open.get(id);
        if (ticket == null || ticket.status() != TicketStatus.OPEN) {
            return Optional.empty();
        }
        Ticket claimed = ticket.withStatus(TicketStatus.CLAIMED, staffName);
        open.put(id, claimed);
        dirty.add(id);
        return Optional.of(claimed);
    }

    /**
     * Closes a ticket and drops it from the queue.
     *
     * <p>The row is still written - the history is the point of storing tickets at
     * all - but it leaves memory, since nobody lists closed tickets by default.
     *
     * <p>The closed copy is parked <em>before</em> the open one is removed, so a
     * save running in between finds one or the other and never neither.
     */
    public Optional<Ticket> close(long id, String staffName) {
        Ticket ticket = open.get(id);
        if (ticket == null) {
            return Optional.empty();
        }
        Ticket closed = ticket.withStatus(TicketStatus.CLOSED, staffName);
        closedPending.put(id, closed);
        open.remove(id);
        dirty.add(id);
        return Optional.of(closed);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void loadAll() throws Exception {
        store.initSchema();
        open.clear();
        dirty.clear();
        closedPending.clear();
        long highest = 0L;
        for (Ticket ticket : store.loadOpen()) {
            open.put(ticket.id(), ticket);
            highest = Math.max(highest, ticket.id());
        }
        // Continue the numbering rather than restart it, so ids stay unique against
        // the closed tickets still in the table.
        nextId.set(Math.max(highest + 1, store.highestId() + 1));
    }

    /**
     * Writes every changed ticket.
     *
     * <p>A save that fails puts its ticket back in the queue and stops: the row is
     * retried at the next flush, and a closed ticket is only forgotten once its
     * closing has actually been written.
     */
    public int flush() throws Exception {
        int written = 0;
        for (Long id : Set.copyOf(dirty)) {
            dirty.remove(id);
            Ticket ticket = open.get(id);
            if (ticket == null) {
                ticket = closedPending.get(id);
            }
            if (ticket == null) {
                continue;
            }
            try {
                store.save(ticket);
            } catch (Exception e) {
                dirty.add(id);
                throw e;
            }
            if (ticket.status() == TicketStatus.CLOSED) {
                closedPending.remove(id, ticket);
            }
            written++;
        }
        return written;
    }
}
