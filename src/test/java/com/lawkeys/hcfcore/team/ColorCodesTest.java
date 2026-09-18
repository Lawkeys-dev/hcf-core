package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.util.ColorCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ColorCodesTest {

    @Test
    void translatesColourAndFormatCodes() {
        assertEquals("§aYour team has been created.",
                ColorCodes.translate("&aYour team has been created."));
        assertEquals("§c§lBold red", ColorCodes.translate("&c&lBold red"));
        assertEquals("§rreset", ColorCodes.translate("&rreset"));
    }

    @Test
    void aLetterThatHappensToBeACodeIsStillTranslated() {
        // "&d" is a real colour code, so "R&D" becomes dark-pink "R" - worth knowing
        // when writing messages, and the reason && exists.
        assertEquals("R" + ColorCodes.COLOR_CHAR + "D".toLowerCase(), ColorCodes.translate("R&D"));
    }

    @Test
    void codesAreNormalisedToLowercase() {
        assertEquals("§a§l", ColorCodes.translate("&A&L"));
    }

    @Test
    void aDoubleAmpersandEscapesALiteralOne() {
        assertEquals("Fish & chips", ColorCodes.translate("Fish && chips"));
    }

    @Test
    void anAmpersandThatIsNotACodeIsLeftAlone() {
        assertEquals("Fish & chips", ColorCodes.translate("Fish & chips"));
        assertEquals("AT&T", ColorCodes.translate("AT&T"));
        assertEquals("trailing&", ColorCodes.translate("trailing&"));
    }

    @Test
    void inputWithoutAmpersandsIsReturnedUnchanged() {
        String plain = "no codes here";
        assertEquals(plain, ColorCodes.translate(plain));
        assertNull(ColorCodes.translate(null));
    }

    @Test
    void stripRemovesTranslatedCodes() {
        assertEquals("Bold red", ColorCodes.strip(ColorCodes.translate("&c&lBold red")));
        assertEquals("plain", ColorCodes.strip("plain"));
        assertNull(ColorCodes.strip(null));
    }

    /**
     * What a player types into a report lands inside a template that is translated
     * afterwards. Escaped, it comes out exactly as typed - codes, literal
     * ampersands and all - instead of recolouring or obfuscating the staff line.
     */
    @Test
    void escapedTextComesOutExactlyAsTyped() {
        String typed = "&kgarbled &cred && R&D";
        assertEquals(typed, ColorCodes.translate(ColorCodes.escape(typed)));
        assertEquals("§7" + typed, ColorCodes.translate("&7" + ColorCodes.escape(typed)),
                "the template's own codes still work around it");
        assertNull(ColorCodes.escape(null));
    }

    /** A section sign typed straight into a command argument is already a code: it is dropped. */
    @Test
    void escapingDropsARawSectionSign() {
        assertEquals("4lbold red", ColorCodes.translate(ColorCodes.escape("§4§lbold red")));
        assertEquals("x", ColorCodes.strip(ColorCodes.translate(ColorCodes.escape("§§x"))));
    }
}
