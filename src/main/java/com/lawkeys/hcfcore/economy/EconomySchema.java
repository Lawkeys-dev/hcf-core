package com.lawkeys.hcfcore.economy;

import com.lawkeys.hcfcore.database.migration.Migration;

import java.util.List;

/**
 * The economy module's versioned schema.
 *
 * <p>Balances are stored as {@code DOUBLE} rather than an integer number of
 * cents. That is not the usual advice for money, and it is a deliberate
 * concession: the Vault {@code Economy} interface this module has to expose
 * (ARCHITECTURE.md section 11) is itself double-based, so storing anything else
 * would mean converting on every call and still losing the same precision at the
 * boundary. Amounts are rounded for display.
 *
 * <p>Verified 29/08/2026 against the VaultAPI source at the {@code 1.7} tag - the
 * version pinned in {@code build.gradle.kts} - where every money-carrying member
 * ({@code getBalance}, {@code has}, {@code depositPlayer}, {@code withdrawPlayer},
 * {@code EconomyResponse.amount} and {@code .balance}) is a {@code double}.
 */
public final class EconomySchema {

    public static final String MODULE = "economy";

    private EconomySchema() {
    }

    private static final Migration V1 = Migration.of(MODULE, 1,
            "player balances",
            List.of("""
                    CREATE TABLE IF NOT EXISTS hcf_balances (
                        player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        balance DOUBLE NOT NULL
                    )"""));

    public static List<Migration> migrations() {
        return List.of(V1);
    }
}
