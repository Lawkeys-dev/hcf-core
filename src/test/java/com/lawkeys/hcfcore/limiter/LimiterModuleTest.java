package com.lawkeys.hcfcore.limiter;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for LimiterModule settings, limits, and helper formatting methods without Bukkit dependency. */
class LimiterModuleTest {

    @Test
    void settingsDefaultsAreValid() {
        LimiterSettings settings = LimiterSettings.defaults();
        assertNotNull(settings);
        assertTrue(settings.enabled());
        assertTrue(settings.fixExistingItems());
        assertEquals(2, settings.allowedLevel("minecraft:speed", 2, false));
    }

    @Test
    void claimBlockLimitsDefaultsToNone() {
        ClaimBlockLimits limits = ClaimBlockLimits.none();
        assertNotNull(limits);
        assertFalse(limits.isActive());
        assertTrue(limits.limits().isEmpty());
    }

    @Test
    void claimBlockLimitsFilteringAndChecks() {
        ClaimBlockLimits limits = new ClaimBlockLimits(true, Map.of("hopper", 10, "spawner", 0, "bad", -1));
        assertTrue(limits.isActive());
        assertEquals(Set.of("HOPPER", "SPAWNER"), limits.limits().keySet());
        assertTrue(limits.isLimited("HOPPER"));
        assertTrue(limits.isLimited("SPAWNER"));
        assertFalse(limits.isLimited("CHEST"));

        assertEquals(10, limits.limitFor("HOPPER").orElseThrow());
        assertEquals(0, limits.limitFor("SPAWNER").orElseThrow());

        assertTrue(ClaimBlockLimits.mayPlace(9, 10));
        assertFalse(ClaimBlockLimits.mayPlace(10, 10));
        assertFalse(ClaimBlockLimits.mayPlace(0, 0));
    }

    @Test
    void displayFormatting() {
        assertEquals("redstone wire", LimiterModule.display("REDSTONE_WIRE"));
        assertEquals("hopper", LimiterModule.display("HOPPER"));
    }
}
