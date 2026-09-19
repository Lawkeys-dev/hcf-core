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
    void aMineskinSkinIsGivenByIdOrByLink() {
        String id = "95be659c13424f49a23485bf65b0d4a0";
        for (String line : new String[] {"[head:mineskin:" + id + "]x", "[head:https://mineskin.org/skins/" + id + "]x",
                "[head:mineskin.org/skins/95be659c-1342-4f49-a234-85bf65b0d4a0]x"}) {
            HeadTag tag = HeadTag.parse(line);
            assertEquals(HeadTag.Kind.MINESKIN, tag.kind(), line);
            assertEquals(id, tag.name(), line);
            assertEquals("x", tag.text());
        }
        assertEquals(HeadTag.Kind.NONE, HeadTag.parse("[head:mineskin:12345]x").kind(), "not an id");
    }

    @Test
    void everyCellFallsOnTheSameDefaultSkin() {
        for (int i = 0; i < TabGrid.SIZE; i++) {
            assertEquals(TabGrid.DEFAULT_SKIN, Math.floorMod(TabGrid.id(i).hashCode(), TabGrid.DEFAULT_SKINS));
        }
    }
}
