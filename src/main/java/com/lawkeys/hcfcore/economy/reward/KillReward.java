package com.lawkeys.hcfcore.economy.reward;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Money for a kill ({@code economy.yml}, {@code kill-reward}, the project owner's
 * request of 23/09/2026): a flat amount, and a share taken from the dead player's
 * balance if the server wants kills to cost something too.
 *
 * <p>The same killer earns nothing for the same victim again within
 * {@code same-victim-cooldown-seconds}: two accounts trading kills is the first
 * thing a kill reward invites.
 *
 * <p>Pure Java: the rules and the memory of recent kills, unit-tested; the listener
 * moves the money.
 */
public final class KillReward {

    /**
     * @param stealPercent the share of the victim's balance moved to the killer, 0 to 100
     */
    public record Rules(boolean enabled, double amount, double stealPercent, long sameVictimCooldownSeconds) {

        public Rules {
            amount = Double.isFinite(amount) ? Math.max(0.0, amount) : 0.0;
            stealPercent = Double.isFinite(stealPercent) ? Math.max(0.0, Math.min(100.0, stealPercent)) : 0.0;
            sameVictimCooldownSeconds = Math.max(0L, sameVictimCooldownSeconds);
        }

        public static Rules defaults() {
            return new Rules(true, 10.0, 0.0, 300L);
        }

        public static Rules load(ConfigurationSection section) {
            Rules d = defaults();
            if (section == null) {
                return d;
            }
            return new Rules(section.getBoolean("enabled", d.enabled()),
                    section.getDouble("amount", d.amount()),
                    section.getDouble("steal-percent", d.stealPercent()),
                    section.getLong("same-victim-cooldown-seconds", d.sameVictimCooldownSeconds()));
        }
    }

    /** What one kill pays: {@code flat} from nowhere, {@code stolen} from the victim. */
    public record Payout(double flat, double stolen) {

        public static final Payout NONE = new Payout(0.0, 0.0);

        public boolean isEmpty() {
            return flat <= 0.0 && stolen <= 0.0;
        }

        public double total() {
            return flat + stolen;
        }
    }

    private final Map<String, Long> lastPaid = new ConcurrentHashMap<>();

    /**
     * Decides a kill's payout and, when it pays, remembers it for the cooldown.
     *
     * @param victimBalance what the dead player holds, for the stolen share
     */
    public Payout payout(Rules rules, UUID killer, UUID victim, double victimBalance, long now) {
        Objects.requireNonNull(rules, "rules");
        if (!rules.enabled() || killer.equals(victim)) {
            return Payout.NONE;
        }
        String pair = killer + ">" + victim;
        Long last = lastPaid.get(pair);
        if (last != null && now - last < rules.sameVictimCooldownSeconds() * 1000L) {
            return Payout.NONE;
        }
        double stolen = Math.max(0.0, victimBalance) * rules.stealPercent() / 100.0;
        // Rounded down to the cent: a share of a balance is money, not a fraction.
        stolen = Math.floor(stolen * 100.0) / 100.0;
        Payout payout = new Payout(rules.amount(), stolen);
        if (!payout.isEmpty()) {
            lastPaid.put(pair, now);
        }
        return payout;
    }

    /** Forgets kills older than the cooldown, so the memory does not grow for the whole map. */
    public void forgetOlderThan(long cutoff) {
        lastPaid.values().removeIf(at -> at < cutoff);
    }
}
