package com.lawkeys.hcfcore.dtr;

import com.lawkeys.hcfcore.claim.RaidabilityPolicy;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.util.Durations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The DTR module's entry point and rule engine, and the other half of the reclaim
 * system specified in FEATURES.md section 3.
 *
 * <p><strong>DTR is a function of time, not a value a task keeps updating.</strong>
 * What is stored per team is the value at the last mutation plus the instant
 * regeneration resumes; {@link #getDtr} derives the current figure on read. Three
 * consequences worth knowing:
 * <ul>
 *   <li>No scheduled task writes DTR, so nothing drifts when a tick is missed and
 *       a lagging server does not fall behind on regeneration.</li>
 *   <li>Regeneration keeps running while the server is down - a team that logs off
 *       raidable can come back protected, which is the usual HCF behaviour.</li>
 *   <li>The whole thing is testable by moving an injected clock rather than by
 *       waiting.</li>
 * </ul>
 *
 * <p>This class implements {@link RaidabilityPolicy}, which is exactly what the
 * claim module has been asking for: install it with
 * {@code claimManager.setRaidabilityPolicy(dtrManager)} and territory protection
 * starts following DTR, with no change to {@code claim/}.
 *
 * <p>Like the other managers this is pure Java with no server API.
 */
public final class DtrManager implements RaidabilityPolicy {

    private final Supplier<DtrSettings> settings;
    private final TeamManager teams;
    private final DtrStore store;
    private final LongSupplier clock;

    private final Map<UUID, DtrState> states = new ConcurrentHashMap<>();
    /** Last raidability seen per team, so {@link #pollRaidabilityChanges} can spot flips. */
    private final Map<UUID, Boolean> lastKnownRaidable = new ConcurrentHashMap<>();

    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingDeletions = ConcurrentHashMap.newKeySet();

    public DtrManager(Supplier<DtrSettings> settings, TeamManager teams, DtrStore store) {
        this(settings, teams, store, System::currentTimeMillis);
    }

    public DtrManager(Supplier<DtrSettings> settings, TeamManager teams, DtrStore store,
                      LongSupplier clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private DtrSettings config() {
        return settings.get();
    }

    // ------------------------------------------------------------------
    // Reading DTR
    // ------------------------------------------------------------------

    /** @return the team's ceiling right now, which moves with its member count */
    public double getMaximum(Team team) {
        return config().maximumFor(team.getMemberCount());
    }

    /**
     * The team's DTR at this instant.
     *
     * <p>A team with no stored state has never died and sits at its maximum, so
     * joining the server does not require writing a row for everyone.
     */
    public double getDtr(Team team) {
        Objects.requireNonNull(team, "team");
        double maximum = getMaximum(team);
        DtrState state = states.get(team.getId());
        if (state == null) {
            return maximum;
        }
        return Math.min(maximum, regenerated(state, clock.getAsLong()));
    }

    /** @return the DTR of the team with this id, or empty if there is no such team */
    public Optional<Double> getDtr(UUID teamId) {
        return teams.getTeam(teamId).map(this::getDtr);
    }

    /**
     * Applies elapsed regeneration to a stored state.
     *
     * <p>Stepwise rather than continuous: a server advertising "0.1 DTR every
     * 3 minutes" should show exactly that, not a fractional drip.
     */
    private double regenerated(DtrState state, long now) {
        DtrSettings.RegenerationRules rules = config().regeneration();
        if (now < state.regenAt() || rules.intervalSeconds() <= 0 || rules.amount() <= 0) {
            return state.stored();
        }
        long elapsedMillis = now - state.regenAt();
        long steps = elapsedMillis / (rules.intervalSeconds() * 1000L);
        return state.stored() + steps * rules.amount();
    }

    /**
     * Whether the team's territory is currently open to raiding.
     *
     * <p>This is the method {@code claim/} calls on every protection check
     * (FEATURES.md section 3): raidable exactly while DTR has run out.
     */
    @Override
    public boolean isRaidable(UUID teamId) {
        if (!config().enabled()) {
            return false;
        }
        Team team = teams.getTeam(teamId).orElse(null);
        if (team == null || team.getType().isSystem()) {
            // Server-owned territory is never raidable, whatever its DTR row says.
            return false;
        }
        return getDtr(team) <= 0.0;
    }

    /** @return {@code true} while regeneration is frozen after a recent death. */
    public boolean isFrozen(Team team) {
        DtrState state = states.get(team.getId());
        return state != null && clock.getAsLong() < state.regenAt();
    }

    /** @return seconds until regeneration resumes, or {@code 0} if it already has. */
    public long getFreezeRemainingSeconds(Team team) {
        DtrState state = states.get(team.getId());
        if (state == null) {
            return 0L;
        }
        long remaining = state.regenAt() - clock.getAsLong();
        return Durations.secondsLeft(remaining);
    }

    /**
     * @return seconds until the team is protected again, or empty when it already
     *         is, or when regeneration can never get it there
     */
    public Optional<Long> getSecondsUntilProtected(Team team) {
        if (!isRaidable(team.getId())) {
            return Optional.empty();
        }
        DtrSettings.RegenerationRules rules = config().regeneration();
        if (rules.amount() <= 0 || rules.intervalSeconds() <= 0) {
            return Optional.empty();
        }

        double current = getDtr(team);
        // Strictly above zero is what lifts raidability, so a team sitting exactly
        // at 0 still needs one more step.
        int stepsNeeded = (int) Math.floor(-current / rules.amount()) + 1;
        long fromFreeze = Math.max(0L, getFreezeRemainingSeconds(team));
        return Optional.of(fromFreeze + (long) stepsNeeded * rules.intervalSeconds());
    }

    // ------------------------------------------------------------------
    // Losing DTR
    // ------------------------------------------------------------------

    /**
     * Applies the cost of one member death: DTR drops and regeneration freezes.
     *
     * @return the DTR after the death, or empty if the module is disabled or the
     *         team is server-owned
     */
    public Optional<Double> applyDeath(Team team) {
        Objects.requireNonNull(team, "team");
        DtrSettings config = config();
        if (!config.enabled() || team.getType().isSystem()) {
            return Optional.empty();
        }

        long now = clock.getAsLong();
        double current = getDtr(team);
        double updated = Math.max(config.minimum(), current - config.lossPerDeath());

        states.put(team.getId(), new DtrState(team.getId(), updated,
                now + config.regeneration().freezeSeconds() * 1000L));
        markDirty(team.getId());
        return Optional.of(updated);
    }

    // ------------------------------------------------------------------
    // Staff overrides
    // ------------------------------------------------------------------

    /**
     * Staff override of a team's DTR ({@code setdtr} in FEATURES.md section 3).
     *
     * <p>Does not touch the freeze: a staff member raising DTR to end a raid
     * expects it to stay there and then regenerate normally, not to restart a
     * 45-minute timer.
     *
     * <p>But regeneration counts from the value set, never from before it: the
     * stored value is where regeneration starts, so keeping a resume instant long
     * past added every step since then to the new value - {@code /team setdtr Test
     * 0.3} read back 1.10, the maximum, at once (found in game, 13/09/2026). The
     * resume instant is therefore the later of the freeze's end and now.
     */
    public TeamResult setDtr(Team team, double value) {
        Objects.requireNonNull(team, "team");
        DtrSettings config = config();
        if (!config.enabled()) {
            return TeamResult.fail(DtrMessages.DISABLED);
        }
        if (team.getType().isSystem()) {
            return TeamResult.fail(DtrMessages.SYSTEM_TEAM, "team", team.getName());
        }
        double maximum = getMaximum(team);
        if (value > maximum || value < config.minimum() || !Double.isFinite(value)) {
            return TeamResult.fail(DtrMessages.SET_OUT_OF_RANGE,
                    "min", format(config.minimum()), "max", format(maximum));
        }

        DtrState existing = states.get(team.getId());
        long now = clock.getAsLong();
        long regenAt = existing == null ? now : Math.max(existing.regenAt(), now);
        states.put(team.getId(), new DtrState(team.getId(), value, regenAt));
        markDirty(team.getId());
        return TeamResult.ok(DtrMessages.SET_SUCCESS, team,
                "team", team.getName(), "dtr", format(value));
    }

    /**
     * Staff override of the regeneration freeze ({@code setregen}).
     *
     * @param seconds how long from now until regeneration resumes; {@code 0} lets
     *                it resume immediately
     */
    public TeamResult setRegenSeconds(Team team, long seconds) {
        Objects.requireNonNull(team, "team");
        if (!config().enabled()) {
            return TeamResult.fail(DtrMessages.DISABLED);
        }
        if (seconds < 0) {
            return TeamResult.fail(DtrMessages.SET_REGEN_NEGATIVE);
        }

        long now = clock.getAsLong();
        DtrState existing = states.get(team.getId());
        double stored = existing == null ? getMaximum(team) : regenerated(existing, now);
        states.put(team.getId(), new DtrState(team.getId(), stored, now + seconds * 1000L));
        markDirty(team.getId());
        return TeamResult.ok(DtrMessages.SET_REGEN_SUCCESS, team,
                "team", team.getName(), "seconds", String.valueOf(seconds));
    }

    /** Resets a team to full DTR with no freeze, for a new map or a staff fix. */
    public void reset(Team team) {
        states.remove(team.getId());
        lastKnownRaidable.remove(team.getId());
        markDirty(team.getId());
    }

    /** Drops all DTR state for a team, on disband. */
    public void release(UUID teamId) {
        states.remove(teamId);
        lastKnownRaidable.remove(teamId);
        dirty.remove(teamId);
        pendingDeletions.add(teamId);
    }

    // ------------------------------------------------------------------
    // Raidability transitions
    // ------------------------------------------------------------------

    /**
     * Reports the teams whose raidability changed since the last call.
     *
     * <p>Needed because DTR is derived from time: a team crossing back above zero
     * does so silently, with nothing to hook. Rather than resurrect a task that
     * mutates DTR, this compares the derived value against what was last seen and
     * reports only the flips, so the caller can announce them.
     *
     * <p>Cheap and side-effect-free on the DTR value itself. Main-thread safe.
     *
     * @return teams whose state flipped, mapped to their new raidability
     */
    public Map<Team, Boolean> pollRaidabilityChanges() {
        Map<Team, Boolean> changes = new LinkedHashMap<>();
        for (Team team : teams.getTeams()) {
            if (team.getType().isSystem()) {
                continue;
            }
            boolean raidable = isRaidable(team.getId());
            Boolean previous = lastKnownRaidable.put(team.getId(), raidable);
            if (previous != null && previous != raidable) {
                changes.put(team, raidable);
            }
        }
        return changes;
    }

    /**
     * Seeds the raidability baseline without reporting anything.
     *
     * <p>Called once after startup so the first poll does not announce every
     * already-raidable team as if it had just happened.
     */
    public void primeRaidabilityBaseline() {
        for (Team team : teams.getTeams()) {
            if (!team.getType().isSystem()) {
                lastKnownRaidable.put(team.getId(), isRaidable(team.getId()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Loads stored DTR into the cache. Blocking - async task only. */
    public void loadAll() throws Exception {
        store.initSchema();
        states.clear();
        lastKnownRaidable.clear();
        dirty.clear();
        pendingDeletions.clear();
        for (DtrState state : store.loadAll()) {
            states.put(state.teamId(), state);
        }
    }

    /**
     * Writes out every team whose DTR changed since the last flush. Blocking -
     * async task only.
     *
     * @return the number of teams written
     */
    public int flush() throws Exception {
        for (UUID deleted : Set.copyOf(pendingDeletions)) {
            store.delete(deleted);
            pendingDeletions.remove(deleted);
        }

        int written = 0;
        for (UUID teamId : Set.copyOf(dirty)) {
            dirty.remove(teamId);
            DtrState state = states.get(teamId);
            try {
                if (state == null) {
                    // Reset back to "never died": drop the row rather than store a
                    // value that would go stale as the team's ceiling moves.
                    store.delete(teamId);
                } else {
                    store.save(state);
                }
                written++;
            } catch (Exception e) {
                dirty.add(teamId);
                throw e;
            }
        }
        return written;
    }

    private void markDirty(UUID teamId) {
        dirty.add(teamId);
        pendingDeletions.remove(teamId);
    }

    /** @return an immutable snapshot of stored state, for tests and diagnostics. */
    public List<DtrState> getStates() {
        return List.copyOf(new ArrayList<>(states.values()));
    }

    /** Formats a DTR figure the way the language file's placeholders expect. */
    public static String format(double dtr) {
        return String.format(Locale.ROOT, "%.2f", dtr);
    }
}
