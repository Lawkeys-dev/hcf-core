package com.lawkeys.hcfcore.integration.discord;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DiscordIntegrationTest {

    @Test
    void testHttpRequestBuilding() {
        String url = "https://discord.com/api/webhooks/123/abc";
        String body = "{\"content\":\"test\"}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        assertEquals(URI.create(url), request.uri());
        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void testTickCalculationForRateLimit() {
        double waitSeconds = 2.5;
        long wait = (long) waitSeconds;
        long ticks = Math.min(30L, Math.max(1L, wait)) * 20L;

        assertEquals(40L, ticks);
    }
}
