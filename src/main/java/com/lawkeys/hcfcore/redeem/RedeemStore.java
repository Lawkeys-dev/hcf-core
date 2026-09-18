package com.lawkeys.hcfcore.redeem;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistence contract for redeem codes.
 *
 * <p>Redemptions are written one at a time rather than with their code: a giveaway
 * code can be used by thousands of players, and rewriting all of them at every use
 * would be the wrong cost.
 *
 * <p><strong>Threading.</strong> Blocking; async task only (CONTRIBUTING.md section 5).
 */
public interface RedeemStore {

    void initSchema() throws Exception;

    /** @return every code, with who has redeemed it */
    Collection<RedeemCode> loadAll() throws Exception;

    /** Writes a code's own fields - not its redemptions. */
    void saveCode(RedeemCode code) throws Exception;

    /** Deletes a code and its redemptions. */
    void deleteCode(String key) throws Exception;

    void addRedemption(String key, UUID playerId, long at) throws Exception;

    /** Forgets redemptions of a code: one player's, or everybody's when {@code playerId} is null. */
    void clearRedemptions(String key, UUID playerId) throws Exception;

    RedeemStore NO_OP = new RedeemStore() {
        @Override
        public void initSchema() {
        }

        @Override
        public Collection<RedeemCode> loadAll() {
            return List.of();
        }

        @Override
        public void saveCode(RedeemCode code) {
        }

        @Override
        public void deleteCode(String key) {
        }

        @Override
        public void addRedemption(String key, UUID playerId, long at) {
        }

        @Override
        public void clearRedemptions(String key, UUID playerId) {
        }
    };
}
