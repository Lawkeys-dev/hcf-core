package com.lawkeys.hcfcore.events;

import com.lawkeys.hcfcore.util.Cuboid;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Citadel: a KOTH, run by the capture engine, plus the claim whose rules hold around it. */
class CitadelTest {

    private static final Cuboid ZONE = Cuboid.between("world", -200, 60, -200, -185, 90, -185);
    private static final CaptureEventDefinition CAPTURE = new CaptureEventDefinition("citadel", "&5Citadel",
            ZONE, 1800, ContestPolicy.RESET, 0L, List.of(), List.of(), List.of());
    private static final CitadelDefinition CITADEL = new CitadelDefinition("citadel", "Citadel", CitadelRules.ALL);
    private static final EventSettings SETTINGS = new EventSettings(true, 1L, ZoneOffset.UTC, true, true,
            List.of(CAPTURE), List.of(CITADEL));

    @Test
    void theClaimIsFoundByTheNameOfItsServerTeam() {
        assertEquals(CITADEL, SETTINGS.citadelClaimedBy("citadel").orElseThrow(), "names are matched without case");
        assertTrue(SETTINGS.citadelClaimedBy("Spawn").isEmpty());
        assertTrue(SETTINGS.citadelClaimedBy(null).isEmpty());
    }

    @Test
    void theCitadelIsFoundByItsEvent() {
        assertEquals("Citadel", SETTINGS.citadel("CITADEL").orElseThrow().claim());
        assertTrue(SETTINGS.citadel("koth").isEmpty());
    }

    @Test
    void itsZoneToHoldIsAnOrdinaryCaptureEvent() {
        assertEquals(CAPTURE, SETTINGS.find("citadel").orElseThrow());
        AtomicLong clock = new AtomicLong();
        EventManager events = new EventManager(() -> SETTINGS, clock::get);
        events.start(CAPTURE);
        UUID team = UUID.randomUUID();
        Occupant holder = Occupant.of(UUID.randomUUID(), team);
        events.tick(java.util.Map.of("citadel", List.of(holder)));
        clock.addAndGet(1800 * 1000L);
        List<EventUpdate> updates = events.tick(java.util.Map.of("citadel", List.of(holder)));
        assertTrue(updates.stream().anyMatch(update -> update.type() == EventUpdate.Type.CAPTURED),
                "held for its 30 minutes, the zone is captured like a KOTH's");
    }

    @Test
    void settingsWithoutCitadelsHaveNone() {
        EventSettings plain = new EventSettings(true, 1L, ZoneOffset.UTC, true, true, List.of(CAPTURE));
        assertTrue(plain.citadels().isEmpty());
        assertFalse(plain.citadelClaimedBy("Citadel").isPresent());
    }

    @Test
    void theShippedCitadelRefusesEverything() {
        CitadelRules all = CitadelRules.ALL;
        assertTrue(all.enderPearls() && all.partnerItems() && all.chorusFruit() && all.elytra() && all.riptide());
    }
}
