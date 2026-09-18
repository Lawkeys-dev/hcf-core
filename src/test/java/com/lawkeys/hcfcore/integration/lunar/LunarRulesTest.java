package com.lawkeys.hcfcore.integration.lunar;

import com.lawkeys.hcfcore.team.TeamRelation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Lunar Client integration's rules, without a server or a client. */
class LunarRulesTest {

    @Test
    void onlyWhatChangedIsSent() {
        SentState<String> sent = new SentState<>();
        SentState.Plan<String> first = sent.reconcile(Map.of("hq", "0,64,0", "rally", "5,64,5"), String::equals);
        assertEquals(Map.of("hq", "0,64,0", "rally", "5,64,5"), first.send());
        assertTrue(first.remove().isEmpty());

        assertTrue(sent.reconcile(Map.of("hq", "0,64,0", "rally", "5,64,5"), String::equals).isEmpty(),
                "nothing changed, nothing sent");

        SentState.Plan<String> moved = sent.reconcile(Map.of("hq", "0,64,0", "rally", "9,64,9"), String::equals);
        assertEquals(Map.of("rally", "9,64,9"), moved.send());
        assertEquals(Set.of("rally"), moved.replaced(), "already on the client: a replacement");
        assertTrue(first.replaced().isEmpty(), "the first time, nothing is replaced");

        SentState.Plan<String> gone = sent.reconcile(Map.of("hq", "0,64,0"), String::equals);
        assertEquals(Set.of("rally"), gone.remove());
        assertEquals(Set.of("hq"), sent.names());
    }

    /**
     * A cooldown's end is recomputed from whole seconds every update: within the
     * tolerance it is the same cooldown, measured against what was sent, so the small
     * differences never add up to a resend.
     */
    @Test
    void aToleranceIsMeasuredAgainstWhatWasSent() {
        SentState<Long> sent = new SentState<>();
        java.util.function.BiPredicate<Long, Long> within = (a, b) -> Math.abs(a - b) < 1500;
        sent.reconcile(Map.of("combat", 10_000L), within);
        assertTrue(sent.reconcile(Map.of("combat", 11_000L), within).isEmpty());
        assertTrue(sent.reconcile(Map.of("combat", 11_400L), within).isEmpty());
        assertEquals(Map.of("combat", 11_600L), sent.reconcile(Map.of("combat", 11_600L), within).send(),
                "1.6 s from what the client shows, even though each step was small");
    }

    @Test
    void aTargetWhoLeftIsForgottenWithoutARemoval() {
        SentState<String> sent = new SentState<>();
        sent.reconcile(Map.of("bob", "x"), String::equals);
        sent.forget("bob");
        assertEquals(Map.of("bob", "x"), sent.reconcile(Map.of("bob", "x"), String::equals).send(),
                "back again, sent again");
    }

    @Test
    void focusBeatsEverythingButOnesOwnTeam() {
        assertEquals(NametagStyle.Relation.SELF, NametagStyle.relation(TeamRelation.SELF, true));
        assertEquals(NametagStyle.Relation.FOCUS, NametagStyle.relation(TeamRelation.ENEMY, true));
        assertEquals(NametagStyle.Relation.FOCUS, NametagStyle.relation(TeamRelation.NEUTRAL, true));
        assertEquals(NametagStyle.Relation.ALLY, NametagStyle.relation(TeamRelation.ALLY, false));
        assertEquals(NametagStyle.Relation.ENEMY, NametagStyle.relation(TeamRelation.ENEMY, false));
        assertEquals(NametagStyle.Relation.NEUTRAL, NametagStyle.relation(TeamRelation.NEUTRAL, false));
        assertEquals(NametagStyle.Relation.NEUTRAL, NametagStyle.relation(TeamRelation.SYSTEM, false));
    }

    @Test
    void aNametagHasATeamLineOnlyForAPlayerWithATeam() {
        NametagStyle style = LunarSettings.defaults().nametags().style();
        assertEquals(List.of("&cWizards &7| &e1.10", "&cbob"),
                style.lines(NametagStyle.Relation.ENEMY, "Wizards", "1.10", "bob"));
        assertEquals(List.of("&fbob"), style.lines(NametagStyle.Relation.NEUTRAL, null, null, "bob"));
        NametagStyle nameOnly = new NametagStyle("", "%color%%player%", Map.of());
        assertEquals(List.of("bob"), nameOnly.lines(NametagStyle.Relation.ALLY, "Wizards", "1.10", "bob"),
                "a blank team line is no team line, and a relation with no colour has none");
    }

    @Test
    void coloursAreWrittenAsHex() {
        assertEquals(OptionalInt.of(0xFFAA00), LunarSettings.parseRgb("#FFAA00"));
        assertEquals(OptionalInt.of(0x55ff55), LunarSettings.parseRgb("55ff55"));
        assertTrue(LunarSettings.parseRgb("orange").isEmpty());
        assertTrue(LunarSettings.parseRgb("#FFF").isEmpty());
        assertTrue(LunarSettings.parseRgb(null).isEmpty());
    }
}
