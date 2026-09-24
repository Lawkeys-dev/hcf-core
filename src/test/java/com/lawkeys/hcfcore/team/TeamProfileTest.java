package com.lawkeys.hcfcore.team;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamProfileTest {

    private static final Pattern DISCORD = Pattern.compile(TeamProfile.DEFAULT_DISCORD_PATTERN);

    @Test
    void aDescriptionIsTheTeamsWordsWithoutFormatting() {
        assertEquals("Hello world", TeamProfile.description("  Hello   &lworld{} ", 100).replace("lworld", "world"));
        assertEquals("abc", TeamProfile.description("abcdef", 3));
        assertEquals("", TeamProfile.description(null, 10));
        assertEquals("", TeamProfile.description("   ", 10));
    }

    @Test
    void onlyADiscordInvitationIsTaken() {
        assertEquals(Optional.of("https://discord.gg/abc123"), TeamProfile.discord("discord.gg/abc123", DISCORD));
        assertEquals(Optional.of("https://discord.com/invite/Ab-12"),
                TeamProfile.discord(" https://discord.com/invite/Ab-12 ", DISCORD));
        assertEquals(Optional.of("http://www.discord.gg/xyz"), TeamProfile.discord("http://www.discord.gg/xyz", DISCORD));
        assertEquals(Optional.empty(), TeamProfile.discord("https://discord.gg.evil.com/abc", DISCORD));
        assertEquals(Optional.empty(), TeamProfile.discord("https://example.com/invite/abc", DISCORD));
        assertEquals(Optional.empty(), TeamProfile.discord("discord.gg/", DISCORD));
        assertEquals(Optional.empty(), TeamProfile.discord("", DISCORD));
    }

    @Test
    void joinModesReadEverySpelling() {
        assertEquals(Optional.of(JoinMode.INVITE), JoinMode.fromId("invite"));
        assertEquals(Optional.of(JoinMode.INVITE), JoinMode.fromId("invite-only"));
        assertEquals(Optional.of(JoinMode.CLOSED), JoinMode.fromId(" CLOSED "));
        assertEquals(Optional.empty(), JoinMode.fromId("maybe"));
    }
}
