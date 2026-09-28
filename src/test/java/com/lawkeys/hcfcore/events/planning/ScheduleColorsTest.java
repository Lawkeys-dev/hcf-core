package com.lawkeys.hcfcore.events.planning;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** One colour per event in /schedule: by the event's id first, then by its kind. */
class ScheduleColorsTest {

    @Test
    void eachKindHasItsOwnColourAsShipped() {
        PlanningController.MenuRules rules = PlanningController.MenuRules.defaults();
        assertEquals("&6", rules.colorOf("koth", "koth"));
        assertEquals("&5", rules.colorOf("citadel", "citadel"));
        assertEquals(9, rules.colors().values().stream().distinct().count(), "nine kinds, nine colours");
    }

    @Test
    void anEventsIdWinsOverItsKind() {
        Map<String, String> colors = new HashMap<>(PlanningController.MenuRules.DEFAULT_COLORS);
        colors.put("nether-koth", "&c");
        PlanningController.MenuRules rules = new PlanningController.MenuRules(true, "CLOCK", "PAPER", "GRAY_DYE", colors);
        assertEquals("&c", rules.colorOf("Nether-KOTH", "koth"));
        assertEquals("&6", rules.colorOf("koth", "koth"));
        assertEquals("", rules.colorOf("unknown", null), "no colour of its own: the name's");
    }
}
