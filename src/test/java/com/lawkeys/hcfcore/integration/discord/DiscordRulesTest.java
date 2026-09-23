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
}
