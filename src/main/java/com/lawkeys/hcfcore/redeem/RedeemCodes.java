package com.lawkeys.hcfcore.redeem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Redeem codes (FEATURES.md section 15): who may use which, and what it gives.
 *
 * <p>Pure Java. Every player may redeem a given code once; a code may also cap how
 * many players redeem it in all. That covers a single-use code (a cap of one), a
 * code for the first hundred, and an open giveaway, without a second switch.
 *
 * <p><strong>Writes are queued in order</strong> rather than marked dirty: a
 * redemption is a fact to append, and a delete must not be overtaken by an earlier
 * redemption of the same code. A write that fails goes back to the front of the
 * queue and is retried at the next flush.
 */
public final class RedeemCodes {

    /** Letters, digits, dashes and underscores, 3 to 32 of them: what fits in a tweet and a chat line. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{3,32}");

    /** Why a redemption did or did not happen. */
    public enum Status {
        REDEEMED,
        /** No such code - also what an attempt too soon after a failed one gets, see {@link #redeem}. */
        UNKNOWN,
        /** The code has reached its cap. */
        EXHAUSTED,
        /** This player has already used it. */
        ALREADY_REDEEMED,
        /** The code exists but gives nothing yet: staff have not added a command. */
        EMPTY,
        /** Too soon after a failed attempt. */
        TOO_SOON
    }

    /** @param commands what to run, with the player's name not yet filled in; empty unless redeemed */
    public record Outcome(Status status, List<String> commands) {
    }

    private sealed interface Write permits SaveCode, DeleteCode, AddRedemption, ClearRedemptions {
    }

    private record SaveCode(RedeemCode code) implements Write {
    }

    private record DeleteCode(String key) implements Write {
    }

    private record AddRedemption(String key, UUID playerId, long at) implements Write {
    }

    private record ClearRedemptions(String key, UUID playerId) implements Write {
    }

    private final RedeemStore store;
    private final LongSupplier clock;
    private final Map<String, RedeemCode> codes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFailure = new ConcurrentHashMap<>();
    private final LinkedBlockingDeque<Write> pending = new LinkedBlockingDeque<>();

    public RedeemCodes(RedeemStore store, LongSupplier clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static boolean isValid(String code) {
        return code != null && VALID.matcher(code).matches();
    }

    public Optional<RedeemCode> get(String code) {
        return Optional.ofNullable(codes.get(RedeemCode.key(code)));
    }

    /** @return every code, alphabetically */
    public List<RedeemCode> list() {
        List<RedeemCode> all = new ArrayList<>(codes.values());
        all.sort(Comparator.comparing(RedeemCode::key));
        return all;
    }

    /**
     * @return {@code false}, changing nothing, if the code is not valid or already
     *         exists - an existing code is deleted first, never silently replaced
     */
    public boolean create(String code, int maxUses, String createdBy, String firstCommand) {
        if (!isValid(code) || codes.containsKey(RedeemCode.key(code))) {
            return false;
        }
        List<String> commands = firstCommand == null || firstCommand.isBlank() ? List.of() : List.of(firstCommand);
        RedeemCode created = new RedeemCode(code, commands, maxUses, createdBy, clock.getAsLong(), Set.of());
        codes.put(created.key(), created);
        pending.addLast(new SaveCode(created));
        return true;
    }

    public boolean addCommand(String code, String command) {
        return update(code, existing -> {
            List<String> commands = new ArrayList<>(existing.commands());
            commands.add(command);
            return with(existing, commands, existing.maxUses());
        });
    }

    /** @param index from 1, as {@code /redeemadmin info} numbers them */
    public boolean removeCommand(String code, int index) {
        RedeemCode existing = codes.get(RedeemCode.key(code));
        if (existing == null || index < 1 || index > existing.commands().size()) {
            return false;
        }
        return update(code, current -> {
            List<String> commands = new ArrayList<>(current.commands());
            commands.remove(index - 1);
            return with(current, commands, current.maxUses());
        });
    }

    public boolean setMaxUses(String code, int maxUses) {
        return update(code, existing -> with(existing, existing.commands(), maxUses));
    }

    public boolean delete(String code) {
        String key = RedeemCode.key(code);
        if (codes.remove(key) == null) {
            return false;
        }
        pending.addLast(new DeleteCode(key));
        return true;
    }

    /**
     * Uses a code.
     *
     * <p>A player who just failed must wait before trying again, however the next
     * attempt would have gone: without it, codes could be found by trying names as
     * fast as chat allows.
     *
     * @param failureCooldownMillis how long a failed attempt makes a player wait
     */
    public Outcome redeem(String code, UUID playerId, long failureCooldownMillis) {
        long now = clock.getAsLong();
        Long failedAt = lastFailure.get(playerId);
        if (failedAt != null && now - failedAt < failureCooldownMillis) {
            return new Outcome(Status.TOO_SOON, List.of());
        }
        String key = RedeemCode.key(code);
        RedeemCode existing = codes.get(key);
        Status refusal = existing == null ? Status.UNKNOWN
                : existing.redeemers().contains(playerId) ? Status.ALREADY_REDEEMED
                : existing.isExhausted() ? Status.EXHAUSTED
                : existing.commands().isEmpty() ? Status.EMPTY
                : null;
        if (refusal != null) {
            lastFailure.put(playerId, now);
            return new Outcome(refusal, List.of());
        }
        Set<UUID> redeemers = new HashSet<>(existing.redeemers());
        redeemers.add(playerId);
        codes.put(key, new RedeemCode(existing.code(), existing.commands(), existing.maxUses(),
                existing.createdBy(), existing.createdAt(), redeemers));
        lastFailure.remove(playerId);
        pending.addLast(new AddRedemption(key, playerId, now));
        return new Outcome(Status.REDEEMED, existing.commands());
    }

    /**
     * Makes a code usable again: by one player, or by everybody when
     * {@code playerId} is null.
     *
     * @return whether there was anything to reset
     */
    public boolean reset(String code, UUID playerId) {
        String key = RedeemCode.key(code);
        RedeemCode existing = codes.get(key);
        if (existing == null || (playerId != null && !existing.redeemers().contains(playerId))) {
            return false;
        }
        Set<UUID> redeemers = new HashSet<>(existing.redeemers());
        if (playerId == null) {
            redeemers.clear();
        } else {
            redeemers.remove(playerId);
        }
        codes.put(key, new RedeemCode(existing.code(), existing.commands(), existing.maxUses(),
                existing.createdBy(), existing.createdAt(), redeemers));
        pending.addLast(new ClearRedemptions(key, playerId));
        return true;
    }

    private boolean update(String code, java.util.function.UnaryOperator<RedeemCode> change) {
        String key = RedeemCode.key(code);
        RedeemCode existing = codes.get(key);
        if (existing == null) {
            return false;
        }
        RedeemCode changed = change.apply(existing);
        codes.put(key, changed);
        pending.addLast(new SaveCode(changed));
        return true;
    }

    private static RedeemCode with(RedeemCode code, List<String> commands, int maxUses) {
        return new RedeemCode(code.code(), commands, maxUses, code.createdBy(), code.createdAt(), code.redeemers());
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void loadAll() throws Exception {
        store.initSchema();
        codes.clear();
        pending.clear();
        for (RedeemCode code : store.loadAll()) {
            codes.put(code.key(), code);
        }
    }

    public int flush() throws Exception {
        int written = 0;
        Write write;
        while ((write = pending.pollFirst()) != null) {
            try {
                switch (write) {
                    case SaveCode save -> store.saveCode(save.code());
                    case DeleteCode delete -> store.deleteCode(delete.key());
                    case AddRedemption add -> store.addRedemption(add.key(), add.playerId(), add.at());
                    case ClearRedemptions clear -> store.clearRedemptions(clear.key(), clear.playerId());
                }
            } catch (Exception e) {
                pending.addFirst(write);
                throw e;
            }
            written++;
        }
        return written;
    }

    /** Forgets a player's failed-attempt wait, when they leave. */
    public void forget(UUID playerId) {
        lastFailure.remove(playerId);
    }
}
