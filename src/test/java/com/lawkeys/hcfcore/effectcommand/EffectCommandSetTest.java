package com.lawkeys.hcfcore.effectcommand;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The effect commands as the file declares them, checked without a server. */
class EffectCommandSetTest {

    private final List<String> warnings = new ArrayList<>();

    private EffectCommandSet set(EffectCommandSet.Entry... entries) {
        return EffectCommandSet.of(List.of(entries), warnings::add);
    }

    private static EffectCommandSet.Entry entry(String name, String effect, int level, String... aliases) {
        return new EffectCommandSet.Entry(name, effect, level, List.of(aliases), null);
    }

    @Test
    void aCommandGetsItsEffectKeyAndDefaultPermission() {
        EffectCommand speed = set(entry("Speed", "speed", 2, "/SP")).get("speed").orElseThrow();
        assertEquals("minecraft:speed", speed.effect());
        assertEquals(2, speed.level());
        assertEquals(List.of("sp"), speed.aliases(), "lower case, without the slash");
        assertEquals("hcfcore.effect.speed", speed.permission());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void aPermissionWrittenInTheFileIsKept() {
        EffectCommand speed = EffectCommandSet.of(List.of(new EffectCommandSet.Entry("speed", "speed", 2, List.of(),
                "ranks.vip.speed")), warnings::add).get("speed").orElseThrow();
        assertEquals("ranks.vip.speed", speed.permission());
    }

    @Test
    void aNameOrAnAliasBelongsToTheFirstCommandOnly() {
        EffectCommandSet set = set(entry("speed", "speed", 2, "sp"), entry("sprint", "speed", 1, "sp", "speed"),
                entry("speed", "haste", 1));
        assertEquals(List.of(), set.get("sprint").orElseThrow().aliases(), "sp and speed are taken");
        assertEquals("minecraft:speed", set.get("speed").orElseThrow().effect(), "the first /speed stays");
        assertEquals(3, warnings.size());
    }

    @Test
    void aCommandThatCannotBeUsedIsLeftOut() {
        EffectCommandSet set = set(entry("no effect", "speed", 1), entry("blank", " ", 1), entry("zero", "speed", 0),
                entry("huge", "speed", 256), entry("fine", "mypack:glow", 255));
        assertEquals(1, set.all().size());
        assertEquals("mypack:glow", set.get("fine").orElseThrow().effect(), "another namespace is kept");
        assertEquals(4, warnings.size());
    }

    @Test
    void levelsReadAsRomanNumerals() {
        assertEquals("II", EffectCommandSet.roman(2));
        assertEquals("X", EffectCommandSet.roman(10));
        assertEquals("11", EffectCommandSet.roman(11));
    }
}
