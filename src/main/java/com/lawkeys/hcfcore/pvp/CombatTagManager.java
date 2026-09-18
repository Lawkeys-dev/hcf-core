package com.lawkeys.hcfcore.pvp;

import com.lawkeys.hcfcore.util.Durations;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Tracks who is currently in combat.
 *
 * <p>Like DTR, a tag is stored as an expiry instant and evaluated on read rather
 * than counted down by a task: nothing to schedule, nothing to drift, and tests
 * move a clock instead of sleeping.
 *
 * <p>Tags are memory-only by design. They last tens of seconds, so persisting
 * them would mean punishing players for the server's own downtime.
 *
 * <p>Pure Java, no server API.
 */
public final class CombatTagManager {

    private final Supplier<PvpSettings> settings;
    private final LongSupplier clock;

    /** player to the epoch millis their tag expires at. */
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();

    public CombatTagManager(Supplier<PvpSettings> settings) {
        this(settings, System::currentTimeMillis);
    }

    public CombatTagManager(Supplier<PvpSettings> settings, LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private PvpSettings.CombatTagRules rules() {
        return settings.get().combatTag();
    }

    /**
     * Tags a player, or extends an existing tag.
     *
     * @return {@code true} if this call started a fresh tag, so the caller knows
     *         whether to tell the player - re-announcing on every hit would be spam
     */
    public boolean tag(UUID player) {
        Objects.requireNonNull(player, "player");
        PvpSettings.CombatTagRules rules = rules();
        if (!settings.get().enabled() || !rules.enabled() || rules.durationSeconds() <= 0) {
            return false;
        }
        boolean wasTagged = isTagged(player);
        taggedUntil.put(player, clock.getAsLong() + rules.durationSeconds() * 1000L);
        return !wasTagged;
    }

    public boolean isTagged(UUID player) {
        if (player == null) {
            return false;
        }
        Long expiry = taggedUntil.get(player);
        if (expiry == null) {
            return false;
        }
        if (clock.getAsLong() >= expiry) {
            taggedUntil.remove(player, expiry);
            return false;
        }
        return true;
    }

    /** @return whole seconds left on the tag, or {@code 0} when untagged. */
    public long getRemainingSeconds(UUID player) {
        Long expiry = taggedUntil.get(player);
        if (expiry == null) {
            return 0L;
        }
        long remaining = expiry - clock.getAsLong();
        return Durations.secondsLeft(remaining);
    }

    /** Clears a tag, on death or on a staff override. */
    public void clear(UUID player) {
        taggedUntil.remove(player);
    }

    /**
     * @return the players whose tag expired since the last call, so the caller can
     *         tell them they are clear
     */
    public Set<UUID> pollExpired() {
        Set<UUID> expired = new LinkedHashSet<>();
        long now = clock.getAsLong();
        for (Map.Entry<UUID, Long> entry : Map.copyOf(taggedUntil).entrySet()) {
            if (now >= entry.getValue() && taggedUntil.remove(entry.getKey(), entry.getValue())) {
                expired.add(entry.getKey());
            }
        }
        return expired;
    }

    /** @return how many players are currently tagged, for diagnostics. */
    public int getTaggedCount() {
        int count = 0;
        for (UUID player : Set.copyOf(taggedUntil.keySet())) {
            if (isTagged(player)) {
                count++;
            }
        }
        return count;
    }

    public void clearAll() {
        taggedUntil.clear();
    }
}
