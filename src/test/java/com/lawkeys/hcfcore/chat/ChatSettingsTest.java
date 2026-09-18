package com.lawkeys.hcfcore.chat;

import com.lawkeys.hcfcore.util.ColorCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chat template, which is the whole of the feature's logic. */
class ChatSettingsTest {

    private final ChatSettings settings = ChatSettings.defaults();

    /** Asked by the owner on 13/09/2026: staff must be able to read team chat back. */
    @Test
    void teamChatIsLoggedByDefault() {
        assertTrue(settings.logTeamChat());
    }

    @Test
    void aLineCarriesPrefixNameSuffixAndMessage() {
        String line = settings.render("&c[Admin] ", "Alice", " &7*", 0, "hello");
        assertTrue(line.contains("[Admin]"));
        assertTrue(line.contains("Alice"));
        assertTrue(line.contains("*"));
        assertTrue(line.endsWith("hello"));
    }

    /** The classic HCF format: the kill count in brackets before the name. */
    @Test
    void theKillCountShowsWhenThereIsOne() {
        assertTrue(settings.render("", "Alice", "", 50, "hi").contains("50"));
    }

    /**
     * A fresh player reading "[0]" beside their name is noise, and every server in
     * the genre hides it. Zero drops the whole part, not just the number.
     */
    @Test
    void aPlayerWithNoKillsShowsNoBracket() {
        String line = settings.render("", "Alice", "", 0, "hi");
        assertFalse(line.contains("["));
        assertFalse(line.contains("0"));
    }

    @Test
    void aNegativeCountIsTreatedAsNone() {
        assertFalse(settings.render("", "Alice", "", -3, "hi").contains("["));
    }

    /**
     * The ordering rule: the message goes in last, so a player typing a placeholder
     * does not get it expanded. Without it, "%player%" in chat would print somebody
     * else's name, and a kills placeholder would print their score.
     */
    @Test
    void aPlaceholderTypedInChatIsNotExpanded() {
        String line = settings.render("", "Alice", "", 7, "my name is %player% and %kills%");
        assertTrue(line.contains("my name is %player% and %kills%"),
                "the message must survive verbatim");
        // The real name still appears once, from the template rather than the message.
        assertTrue(line.indexOf("Alice") < line.indexOf("my name is"));
    }

    @Test
    void nullsAreRenderedAsNothingRatherThanAsTheWordNull() {
        String line = settings.render(null, "Alice", null, 0, "hi");
        assertFalse(line.contains("null"));
    }

    @Test
    void aMissingMessageLeavesTheRestIntact() {
        assertTrue(settings.render("", "Alice", "", 0, null).contains("Alice"));
    }

    @Test
    void theTemplateIsFreeToReorderEverything() {
        ChatSettings reordered = new ChatSettings(true,
                "%message% &8<- &f%player%%suffix% %kills%", "(%value%)", 0, true);
        String line = reordered.render("", "Alice", " [VIP]", 3, "hello");
        assertTrue(line.startsWith("hello"));
        assertTrue(line.contains("[VIP]"));
        assertTrue(line.contains("(3)"));
    }

    @Test
    void anEmptyKillsFormatRemovesTheCountEntirely() {
        ChatSettings quiet = new ChatSettings(true, "%kills%%player%: %message%", "", 0, true);
        assertEquals("Alice: hi", quiet.render("", "Alice", "", 99, "hi"));
    }

    /**
     * The line is coloured after it is rendered, so the listener escapes what a
     * player without the colour permission typed. The template and the LuckPerms
     * prefix still colour; the player's own codes come out as the characters typed.
     */
    @Test
    void anEscapedMessageKeepsItsCodesAsText() {
        ChatSettings plain = new ChatSettings(true, "%prefix%%player%&7: &f%message%", "", 0, true);
        String line = ColorCodes.translate(
                plain.render("&c[Admin] ", "Alice", "", 0, ColorCodes.escape("&kspam &4fake")));
        assertEquals("§c[Admin] Alice§7: §f&kspam &4fake", line);
    }
}
