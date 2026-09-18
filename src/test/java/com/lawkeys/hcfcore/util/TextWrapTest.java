package com.lawkeys.hcfcore.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextWrapTest {

    @Test
    void shortTextIsOneLine() {
        assertEquals(List.of("hello there"), TextWrap.wrap("hello there", 20));
    }

    @Test
    void textBreaksAtSpaces() {
        assertEquals(List.of("the quick", "brown fox", "jumps"),
                TextWrap.wrap("the quick brown fox jumps", 10));
    }

    @Test
    void aLineMayBeExactlyTheWidth() {
        assertEquals(List.of("abcde", "fghij"), TextWrap.wrap("abcde fghij", 5));
    }

    /** A word wider than a line is cut, rather than overflowing the lore it was wrapped for. */
    @Test
    void aWordWiderThanALineIsCut() {
        assertEquals(List.of("see", "abcdefghij", "klm ok"),
                TextWrap.wrap("see abcdefghijklm ok", 10));
    }

    @Test
    void runsOfSpacesCollapse() {
        assertEquals(List.of("a b"), TextWrap.wrap("  a    b  ", 10));
    }

    @Test
    void blankTextIsOneEmptyLine() {
        assertEquals(List.of(""), TextWrap.wrap("", 10));
        assertEquals(List.of(""), TextWrap.wrap(null, 10));
    }

    @Test
    void aWidthBelowOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> TextWrap.wrap("x", 0));
    }
}
