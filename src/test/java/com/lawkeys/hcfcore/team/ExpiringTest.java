package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.util.WorldPosition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An invite and a rally point are live until their expiry; {@code 0} means never. */
class ExpiringTest {

    @Test
    void anInviteIsLiveUntilItsExpiry() {
        Invite invite = new Invite(UUID.randomUUID(), 5_000L);
        assertTrue(invite.isActiveAt(4_999L));
        assertFalse(invite.isActiveAt(5_000L));
        assertTrue(new Invite(UUID.randomUUID(), 0L).isActiveAt(Long.MAX_VALUE), "0 never expires");
    }

    @Test
    void aRallyIsLiveUntilItsExpiry() {
        Rally rally = new Rally(WorldPosition.of("world", 0, 64, 0), 5_000L);
        assertTrue(rally.isActiveAt(4_999L));
        assertFalse(rally.isActiveAt(5_000L));
        assertTrue(new Rally(rally.position(), 0L).isActiveAt(Long.MAX_VALUE), "0 never expires");
        assertThrows(NullPointerException.class, () -> new Rally(null, 0L));
    }
}
