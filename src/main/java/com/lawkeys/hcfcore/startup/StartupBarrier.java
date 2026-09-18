package com.lawkeys.hcfcore.startup;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Knows whether every module has finished loading its data, so that nothing gets
 * to change that data before it is there.
 *
 * <p><strong>Why it exists.</strong> Each module creates its manager, then loads
 * it from the database on an async task, and that load starts by clearing the
 * cache. Anything changed in between was wiped when the load landed: a team
 * created, a death's DTR cost, a payment. The loads are also independent of each
 * other, so a move of money across two modules could land half in a cache about
 * to be cleared - with teams loaded and balances not yet, {@code /team deposit}
 * debited a starting balance that the load then replaced with the stored one,
 * and the bank kept the credit.
 *
 * <p><strong>Lifecycle.</strong> Every load is declared with {@link #expect}
 * during startup, then {@link #seal()} says no more are coming. The barrier is
 * {@link State#READY} once it is sealed <em>and</em> every load has succeeded -
 * sealing is what stops it opening early, when the loads declared so far have
 * finished but a later module has yet to declare its own. It is
 * {@link State#FAILED} as soon as one load fails.
 *
 * <p><strong>Both outcomes are final.</strong> A server that could not load part
 * of its data stays closed until it is restarted: a player let in on an empty
 * cache sees no team, no claim, the starting balance, and whatever they do is
 * saved over nothing (decided by the project owner on 11/09/2026). And
 * {@code READY} cannot be left either, since by then every load has reported and
 * no new one may be declared.
 *
 * <p>Pure Java, like every manager (ARCHITECTURE.md section 13); the server-facing
 * half is {@code StartupGate}.
 *
 * <p><strong>Threading.</strong> Loads report from async tasks while the main
 * thread asks for the state on every login and command. Transitions take the
 * lock - there are a handful per startup - and reading the state is a single
 * volatile read. The observer is called outside the lock, and never twice for
 * the same transition.
 */
public final class StartupBarrier {

    public enum State {
        /** At least one load is still running, or more may still be declared. */
        LOADING,
        /** Sealed, and every declared load has succeeded. */
        READY,
        /** At least one load failed. */
        FAILED
    }

    /** What the barrier reports as it settles. Both methods default to doing nothing. */
    public interface Observer {

        Observer NONE = new Observer() {
        };

        /** Called once, when the barrier becomes {@link State#READY}. */
        default void ready() {
        }

        /** Called once for each load that fails; the first one makes the barrier {@link State#FAILED}. */
        default void failed(String load) {
        }
    }

    private final Observer observer;

    // Guarded by this.
    private final Set<String> declared = new HashSet<>();
    private int pending;
    private boolean sealed;

    private volatile State state = State.LOADING;

    public StartupBarrier(Observer observer) {
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    /**
     * Declares a load the barrier must wait for.
     *
     * @param name what is being loaded, as it should read in the console
     *             ("teams", "the database")
     * @return the handle the load reports through, exactly once
     * @throws IllegalStateException    if the barrier is already sealed: a load
     *                                  declared that late could be missed, and
     *                                  after {@code READY} would reopen the window
     * @throws IllegalArgumentException if a load of that name was already declared
     */
    public synchronized Load expect(String name) {
        Objects.requireNonNull(name, "name");
        if (sealed) {
            throw new IllegalStateException("Load '" + name + "' declared after the startup barrier was sealed.");
        }
        if (!declared.add(name)) {
            throw new IllegalArgumentException("Load '" + name + "' was declared twice.");
        }
        pending++;
        return new Load(name);
    }

    /**
     * Says that every load has been declared. The barrier opens here if they have
     * all succeeded already. Sealing twice is harmless.
     */
    public void seal() {
        boolean opened;
        synchronized (this) {
            if (sealed) {
                return;
            }
            sealed = true;
            opened = openIfDone();
        }
        if (opened) {
            observer.ready();
        }
    }

    public State state() {
        return state;
    }

    public boolean isReady() {
        return state == State.READY;
    }

    /** Opens the barrier if nothing is left to wait for. Caller holds the lock. */
    private boolean openIfDone() {
        if (sealed && pending == 0 && state == State.LOADING) {
            state = State.READY;
            return true;
        }
        return false;
    }

    /**
     * One declared load. It reports once, by succeeding or failing; a second
     * report is ignored, so a load cannot both fail and later count as a success.
     */
    public final class Load {

        private final String name;
        // Guarded by the enclosing barrier.
        private boolean reported;

        private Load(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        /** @return {@code false} if this load had already reported, in which case nothing changed */
        public boolean succeeded() {
            boolean opened;
            synchronized (StartupBarrier.this) {
                if (reported) {
                    return false;
                }
                reported = true;
                pending--;
                opened = openIfDone();
            }
            if (opened) {
                observer.ready();
            }
            return true;
        }

        /** @return {@code false} if this load had already reported, in which case nothing changed */
        public boolean failed() {
            synchronized (StartupBarrier.this) {
                if (reported) {
                    return false;
                }
                reported = true;
                pending--;
                state = State.FAILED;
            }
            observer.failed(name);
            return true;
        }
    }
}
