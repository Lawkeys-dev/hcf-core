package com.lawkeys.hcfcore.ui.tab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadTagTest {

    @Test
    void aLineWithoutATagHasTheDefaultHead() {
        HeadTag tag = HeadTag.parse("{primary}&lTeam");
        assertEquals(HeadTag.Kind.NONE, tag.kind());
        assertEquals("{primary}&lTeam", tag.text());
    }

    @Test
    void theTagIsTakenOffTheLine() {
        HeadTag tag = HeadTag.parse("[head:self]{primary}&lPlayer Info");
        assertEquals(HeadTag.Kind.SELF, tag.kind());
        assertEquals("{primary}&lPlayer Info", tag.text());
    }

    @Test
    void membersAndTopTeamsAreNumberedFromOne() {
        HeadTag member = HeadTag.parse("[head:member:3]%member_3%");
        assertEquals(HeadTag.Kind.MEMBER, member.kind());
        assertEquals(3, member.index());
        assertEquals("%member_3%", member.text());
        assertEquals(HeadTag.Kind.TOP, HeadTag.parse("[head:TOP:1]x").kind());
        assertEquals(HeadTag.Kind.NONE, HeadTag.parse("[head:member:0]x").kind(), "there is no member 0");
        assertEquals(HeadTag.Kind.NONE, HeadTag.parse("[head:member:two]x").kind());
    }

    @Test
    void anythingElseIsAnAccountName() {
        HeadTag tag = HeadTag.parse("[head:MHF_Chest]{primary}&lTeam");
        assertEquals(HeadTag.Kind.ACCOUNT, tag.kind());
        assertEquals("MHF_Chest", tag.name());
        assertEquals(HeadTag.Kind.NONE, HeadTag.parse("[head:not a name!]x").kind(), "no such account can exist");
    }

    @Test
    void everyCellFallsOnTheSameDefaultSkin() {
        for (int i = 0; i < TabGrid.SIZE; i++) {
            assertEquals(TabGrid.DEFAULT_SKIN, Math.floorMod(TabGrid.id(i).hashCode(), TabGrid.DEFAULT_SKINS));
        }
    }
}
