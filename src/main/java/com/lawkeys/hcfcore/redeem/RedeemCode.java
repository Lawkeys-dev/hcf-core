package com.lawkeys.hcfcore.redeem;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One code, what it gives, and who has used it.
 *
 * <p>Immutable; {@link RedeemCodes} replaces it on every change.
 *
 * @param code     as staff typed it, for display; matched without regard to case
 * @param commands the reward, run from the console with {@code %player%} filled in -
 *                 the one shape that can give an item, money, a rank or a kit from
 *                 any plugin without this module knowing what those are
 * @param maxUses  how many players may redeem it in all; {@code 0} is no limit
 */
public record RedeemCode(String code, List<String> commands, int maxUses, String createdBy,
                         long createdAt, Set<UUID> redeemers) {

    public RedeemCode {
        Objects.requireNonNull(code, "code");
        commands = List.copyOf(commands);
        maxUses = Math.max(0, maxUses);
        createdBy = createdBy == null ? "" : createdBy;
        redeemers = Set.copyOf(redeemers);
    }

    /** @return the key codes are matched on: lower case */
    public static String key(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return key(code);
    }

    public int uses() {
        return redeemers.size();
    }

    public boolean isExhausted() {
        return maxUses > 0 && redeemers.size() >= maxUses;
    }
}
