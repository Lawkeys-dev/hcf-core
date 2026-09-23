package com.lawkeys.hcfcore.integration.discord;

import com.lawkeys.hcfcore.api.event.TeamRaidableEvent;
import com.lawkeys.hcfcore.config.ConfigManager;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.Announcements;
import com.lawkeys.hcfcore.util.ColorCodes;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Sends the plugin's announcements to Discord through webhooks ({@code discord.yml},
 * the project owner's request of 23/09/2026): an event started or won, a map phase,
 * a bounty collected, a team going raidable - whatever {@code forward} routes.
 *
 * <p>Nothing leaves the server until a webhook URL is set: the file ships switched
 * off. Requests go one at a time on a thread of their own - never the main one - and
 * a rate limit from Discord is waited out once, then the message is dropped: a
 * Discord outage must never pile up in the server's memory.
 */
public final class DiscordIntegration implements Listener {

    /** Messages waiting past this are dropped rather than queued for ever. */
    private static final int MAX_QUEUED = 100;

    private final Plugin plugin;
    private final LangManager lang;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "HCFCore-Discord");
        thread.setDaemon(true);
        return thread;
    });
    private final java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();
    private volatile DiscordRules rules = DiscordRules.off();
    private volatile boolean warned;

    private DiscordIntegration(Plugin plugin, LangManager lang) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    public static DiscordIntegration start(Plugin plugin, LangManager lang) {
        DiscordIntegration discord = new DiscordIntegration(plugin, lang);
        discord.reload();
        plugin.getServer().getPluginManager().registerEvents(discord, plugin);
        Announcements.listen(discord::onAnnouncement);
        return discord;
    }

    /** Re-reads {@code discord.yml}. */
    public void reload() {
        this.rules = load(ConfigManager.loadFile(plugin, "discord.yml"));
        if (rules.enabled()) {
            plugin.getLogger().info("Discord: " + rules.webhooks().size() + " webhook(s), "
                    + rules.forward().size() + " forwarding rule(s).");
        }
    }

    public void disable() {
        Announcements.reset();
        sender.shutdown();
        try {
            // A last announcement - a win at shutdown - still goes, briefly.
            sender.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A team's DTR made it raidable, or protected again: public news on an HCF server. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRaidable(TeamRaidableEvent event) {
        String key = event.isRaidable() ? DiscordMessages.TEAM_RAIDABLE : DiscordMessages.TEAM_PROTECTED;
        onAnnouncement(key, lang.get(key, "team", event.getTeam().getName()));
    }

    private void onAnnouncement(String key, String message) {
        DiscordRules current = rules;
        current.urlFor(key).ifPresent(url -> {
            String text = ColorCodes.strip(message).trim();
            if (text.isEmpty() || queued.get() >= MAX_QUEUED) {
                return;
            }
            queued.incrementAndGet();
            sender.execute(() -> {
                try {
                    send(url, current.body(text), true);
                } finally {
                    queued.decrementAndGet();
                }
            });
        });
    }

    private void send(String url, String body, boolean mayRetry) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 && mayRetry) {
                long wait = response.headers().firstValue("Retry-After")
                        .map(DiscordIntegration::seconds).orElse(2.0).longValue();
                Thread.sleep(Math.min(30L, Math.max(1L, wait)) * 1000L);
                send(url, body, false);
            } else if (response.statusCode() >= 300) {
                warnOnce("Discord answered " + response.statusCode() + " - check the webhook URL in discord.yml.", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            warnOnce("Could not reach Discord (further failures are not logged).", e);
        }
    }

    private static double seconds(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 2.0;
        }
    }

    private void warnOnce(String message, Exception cause) {
        if (!warned) {
            warned = true;
            plugin.getLogger().log(Level.WARNING, message, cause);
        }
    }

    private DiscordRules load(ConfigurationSection section) {
        if (section == null) {
            return DiscordRules.off();
        }
        Map<String, String> webhooks = new LinkedHashMap<>();
        ConfigurationSection hooks = section.getConfigurationSection("webhooks");
        if (hooks != null) {
            for (String name : hooks.getKeys(false)) {
                String url = hooks.getString(name, "");
                if (url != null && !url.isBlank() && !url.trim().startsWith("https://")) {
                    plugin.getLogger().warning("discord.yml: webhook '" + name + "' is not an https:// URL; ignored.");
                    continue;
                }
                webhooks.put(name, url == null ? "" : url.trim());
            }
        }
        List<DiscordRules.Rule> forward = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("forward")) {
            Object keys = raw.get("keys");
            if (keys == null) {
                plugin.getLogger().warning("discord.yml: a forward rule has no keys; ignored.");
                continue;
            }
            Object to = raw.get("to");
            forward.add(new DiscordRules.Rule(keys.toString(), to == null ? "" : to.toString()));
        }
        return new DiscordRules(section.getBoolean("enabled", false), webhooks, forward,
                section.getString("username", ""), section.getString("avatar-url", ""));
    }
}
