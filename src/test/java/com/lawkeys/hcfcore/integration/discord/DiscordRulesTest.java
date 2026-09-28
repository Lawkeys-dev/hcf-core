package com.lawkeys.hcfcore.integration.discord;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordRulesTest {

    private static final String URL = "https://discord.com/api/webhooks/1/abc";

    private static DiscordRules rules(DiscordRules.Rule... forward) {
        return new DiscordRules(true, Map.of("announcements", URL), List.of(forward), "HCF", "");
    }

    @Test
    void theFirstMatchingRuleDecides() {
        DiscordRules rules = rules(new DiscordRules.Rule("events.*.milestone*", ""),
                new DiscordRules.Rule("events.*", "announcements"));
        assertEquals(Optional.of(URL), rules.urlFor("events.dtc.won"));
        assertTrue(rules.urlFor("events.dtc.milestone-per-team").isEmpty(), "silenced before the catch-all");
        assertTrue(rules.urlFor("phase.sotw.started").isEmpty(), "nothing matched");
    }

    @Test
    void anUnknownOrEmptyWebhookSendsNowhere() {
        assertTrue(rules(new DiscordRules.Rule("events.*", "elsewhere")).urlFor("events.dtc.won").isEmpty());
        DiscordRules blank = new DiscordRules(true, Map.of("announcements", " "),
                List.of(new DiscordRules.Rule("events.*", "announcements")), "", "");
        assertTrue(blank.urlFor("events.dtc.won").isEmpty());
    }

    @Test
    void offSendsNothing() {
        assertTrue(DiscordRules.off().urlFor("events.dtc.won").isEmpty());
    }

    @Test
    void theBodyIsEscapedCutAndMentionsNobody() {
        String body = rules().body("Team \"@everyone\" won\nthe KOTH");
        assertEquals("{\"content\":\"Team \\\"@everyone\\\" won\\nthe KOTH\",\"username\":\"HCF\","
                + "\"allowed_mentions\":{\"parse\":[]}}", body);
        assertTrue(rules().body("x".repeat(3000)).contains("x".repeat(1997) + "..."));
    }

    @Test
    void onlyARulesOwnMentionMayPing() {
        DiscordRules rules = new DiscordRules(true, java.util.Map.of("announcements", URL), java.util.List.of(
                new DiscordRules.Rule("events.*.started", "announcements", "<@&42>", 0xFFAA00)), "", "");
        DiscordRules.Route route = rules.routeFor("events.koth.started").orElseThrow();
        String body = rules.body("@everyone KOTH started", route);
        assertTrue(body.contains("\"allowed_mentions\":{\"parse\":[],\"roles\":[\"42\"]}"), body);
        assertTrue(body.startsWith("{\"content\":\"<@&42> @everyone KOTH started\""),
                "the ping goes with the text, and @everyone is text: nothing parses it");
        assertTrue(rules.body("hello").contains("\"allowed_mentions\":{\"parse\":[]}"), "no rule, no ping");
    }

    @Test
    void anEmbedCarriesTheTextAndItsColour() {
        DiscordRules rules = new DiscordRules(true, java.util.Map.of("announcements", URL), java.util.List.of(
                new DiscordRules.Rule("events.*", "announcements", "", 0x00FF00)), "HCF", "", true, java.util.Set.of());
        String body = rules.body("KOTH won", rules.routeFor("events.koth.won").orElseThrow());
        assertTrue(body.contains("\"embeds\":[{\"description\":\"KOTH won\",\"color\":65280}]"), body);
        assertTrue(!body.contains("\"content\""), "nobody to ping, no content line");
    }

    @Test
    void aSensorIsHeardOnlyWhenSwitchedOnAndDiscordToo() {
        DiscordRules on = new DiscordRules(true, java.util.Map.of(), java.util.List.of(), "", "", false,
                java.util.Set.of("teams"));
        assertTrue(on.hears("teams"));
        assertTrue(!on.hears("kills"));
        DiscordRules off = new DiscordRules(false, java.util.Map.of(), java.util.List.of(), "", "", false,
                java.util.Set.of("teams"));
        assertTrue(!off.hears("teams"));
    }
}
