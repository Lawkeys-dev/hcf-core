package com.lawkeys.hcfcore.crowbar;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.lawkeys.hcfcore.crowbar.CrowbarRules.Verdict.ALLOWED;
import static com.lawkeys.hcfcore.crowbar.CrowbarRules.Verdict.ENEMY_CLAIM;
import static com.lawkeys.hcfcore.crowbar.CrowbarRules.Verdict.SERVER_LAND;
import static com.lawkeys.hcfcore.crowbar.CrowbarRules.Verdict.WARZONE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The crowbar's zone rule, set by the project owner on 28/08/2026. */
class CrowbarRulesTest {

    private final UUID mine = UUID.randomUUID();
    private final UUID theirs = UUID.randomUUID();

    @Test
    void theWildernessIsAllowed() {
        assertEquals(ALLOWED, CrowbarRules.judge(null, false, false, mine));
        assertEquals(ALLOWED, CrowbarRules.judge(null, false, false, null), "even without a team");
    }

    @Test
    void yourOwnClaimIsAllowed() {
        assertEquals(ALLOWED, CrowbarRules.judge(mine, false, false, mine));
    }

    /** The whole point: never to defuse somebody else's trap during a raid. */
    @Test
    void anEnemyClaimIsNeverAllowed() {
        assertEquals(ENEMY_CLAIM, CrowbarRules.judge(theirs, false, false, mine));
        assertEquals(ENEMY_CLAIM, CrowbarRules.judge(theirs, false, false, null));
    }

    @Test
    void serverLandAndTheWarzoneAreNeitherWildernessNorYours() {
        assertEquals(SERVER_LAND, CrowbarRules.judge(theirs, true, false, mine));
        assertEquals(WARZONE, CrowbarRules.judge(null, false, true, mine));
    }

    @Test
    void usesCountDownAndZeroMeansNoLimit() {
        assertEquals(4, CrowbarRules.afterUse(5));
        assertEquals(0, CrowbarRules.afterUse(1), "the last use spends it");
        assertEquals(Integer.MAX_VALUE, CrowbarRules.afterUse(0));
    }
}
