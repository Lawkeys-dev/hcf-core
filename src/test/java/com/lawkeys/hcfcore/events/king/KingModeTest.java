package com.lawkeys.hcfcore.events.king;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KingModeTest {

    @Test
    void modesAreReadWithoutCase() {
        assertEquals(Optional.of(KingMode.SOLO), KingMode.of(" Solo "));
        assertEquals(Optional.of(KingMode.TEAM), KingMode.of("team"));
        assertEquals(Optional.empty(), KingMode.of("duo"));
        assertEquals(Optional.empty(), KingMode.of(null));
    }
}
