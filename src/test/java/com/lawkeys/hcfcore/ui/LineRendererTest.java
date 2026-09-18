package com.lawkeys.hcfcore.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scoreboard's rendering rules, which is all of its logic. */
class LineRendererTest {

    @Test
    void placeholdersAreFilledIn() {
        String line = new LineRenderer().with("%team%", "Wizards").with("%kills%", 7)
                .render("Team: %team% (%kills%)");
        assertEquals("Team: Wizards (7)", line);
    }

    /**
     * The rule the whole conditional-row design rests on: a row that renders to
     * nothing is absent, not blank. That is how a combat timer appears only while
     * the timer runs, and how a server missing a module needs no config edits.
     */
    @Test
    void aRowThatRendersEmptyIsDropped() {
        List<String> out = new LineRenderer()
                .with("%combat_line%", "")
                .with("%team%", "Wizards")
                .renderAll(List.of("Team: %team%", "%combat_line%", "End"));

        assertEquals(List.of("Team: Wizards", "End"), out);
    }

    /** What ui.yml promises: a row whose placeholders all came out empty is dropped, text and all. */
    @Test
    void aRowWhosePlaceholdersAllCameOutEmptyIsDropped() {
        List<String> out = new LineRenderer()
                .with("%dtr_coloured%", "")
                .with("%balance%", "")
                .renderAll(List.of("&cDTR: %dtr_coloured%", "&cBalance: &a$%balance%", "&7----"));
        assertEquals(List.of("&7----"), out, "a separator names no placeholder and always stays");
    }

    @Test
    void oneFilledPlaceholderKeepsTheRow() {
        List<String> out = new LineRenderer().with("%a%", "").with("%b%", "x")
                .renderAll(List.of("%a%|%b%"));
        assertEquals(List.of("|x"), out);
    }

    /**
     * The caller translates colours after rendering, so codes a value carries -
     * %dtr_coloured% brings its own - come through as codes rather than being
     * printed raw because the template was translated first.
     */
    @Test
    void aValueKeepsItsColourCodesForTheCallerToTranslate() {
        assertEquals(List.of("DTR: &41.10"),
                new LineRenderer().with("%dtr_coloured%", "&41.10").renderAll(List.of("DTR: %dtr_coloured%")));
    }

    @Test
    void aRowOfOnlyWhitespaceCountsAsEmpty() {
        assertTrue(new LineRenderer().renderAll(List.of("   ")).isEmpty());
    }

    /** Identical rows all stay, unchanged: the board keys its rows by position, not text. */
    @Test
    void identicalRowsAllSurviveAsTheyAre() {
        List<String> out = new LineRenderer()
                .renderAll(List.of("&7----", "middle", "&7----", "&7----"));

        assertEquals(List.of("&7----", "middle", "&7----", "&7----"), out);
    }

    /** A board cannot show more than fifteen rows, so the rest are never drawn. */
    @Test
    void theBoardStopsAtFifteenRows() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add("line " + i);
        }
        assertEquals(LineRenderer.MAX_LINES, new LineRenderer().renderAll(many).size());
    }

    /** Dropped rows do not count towards the limit - fifteen VISIBLE rows fit. */
    @Test
    void emptyRowsDoNotUseUpTheLimit() {
        List<String> templates = new ArrayList<>();
        for (int i = 0; i < LineRenderer.MAX_LINES; i++) {
            templates.add("line " + i);
            templates.add("%gone%");
        }
        List<String> out = new LineRenderer().with("%gone%", "").renderAll(templates);
        assertEquals(LineRenderer.MAX_LINES, out.size());
        assertTrue(out.contains("line 14"));
    }

    @Test
    void aNullValueRendersAsNothingRatherThanTheWordNull() {
        assertEquals(List.of(), new LineRenderer().with("%x%", (String) null).renderAll(List.of("%x%")));
    }

    @Test
    void anUnknownPlaceholderIsLeftAloneRatherThanBlanking() {
        // It shows literally, which is what tells an operator they mistyped it.
        assertEquals(List.of("%nope%"), new LineRenderer().renderAll(List.of("%nope%")));
    }

    @Test
    void anEmptyTemplateListRendersNothing() {
        assertEquals(List.of(), new LineRenderer().renderAll(Collections.emptyList()));
    }
}
