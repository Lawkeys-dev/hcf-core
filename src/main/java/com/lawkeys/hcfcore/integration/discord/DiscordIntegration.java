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
        discord.sense("server", DiscordMessages.SERVER_STARTED);
        return discord;
    }

    /** Posts a sensor's message, when that sensor is switched on. */
    private void sense(String sensor, String key, String... placeholders) {
        if (rules.hears(sensor)) {
            onAnnouncement(key, lang.get(key, placeholders));
        }
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
        sense("server", DiscordMessages.SERVER_STOPPING);
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
        sense("raids", key, "team", event.getTeam().getName(),
                "raider", event.getRaider().map(com.lawkeys.hcfcore.team.Team::getName).orElse(""));
    }

    // ------------------------------------------------------------------
    // Sensors: what Discord hears besides the announcements (discord.yml, sensors)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(org.bukkit.event.entity.PlayerDeathEvent event) {
        org.bukkit.entity.Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) {
            return;
        }
        sense("kills", DiscordMessages.KILL, "killer", killer.getName(), "victim", event.getEntity().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoggerKill(com.lawkeys.hcfcore.api.event.CombatLoggerDeathEvent event) {
        event.getKiller().ifPresent(killer ->
                sense("kills", DiscordMessages.KILL, "killer", killer.getName(), "victim", event.getVictimName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeamCreated(com.lawkeys.hcfcore.api.event.TeamCreateEvent event) {
        if (!event.getTeam().getType().isSystem()) {
            sense("teams", DiscordMessages.TEAM_CREATED, "team", event.getTeam().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeamDisbanded(com.lawkeys.hcfcore.api.event.TeamDisbandEvent event) {
        if (!event.getTeam().getType().isSystem()) {
            sense("teams", DiscordMessages.TEAM_DISBANDED, "team", event.getTeam().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAlliance(com.lawkeys.hcfcore.api.event.TeamAllianceChangeEvent event) {
        sense("alliances", event.isAllied() ? DiscordMessages.ALLIANCE_FORMED : DiscordMessages.ALLIANCE_BROKEN,
                "team", event.getTeam().getName(), "other", event.getOtherTeam().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        sense("joins", DiscordMessages.PLAYER_JOINED, "player", event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        sense("joins", DiscordMessages.PLAYER_LEFT, "player", event.getPlayer().getName());
    }

    private void onAnnouncement(String key, String message) {
        DiscordRules current = rules;
        current.routeFor(key).ifPresent(route -> {
            String text = ColorCodes.strip(message).trim();
            if (text.isEmpty() || queued.get() >= MAX_QUEUED) {
                return;
            }
            queued.incrementAndGet();
            sender.execute(() -> {
                try {
                    send(route.url(), current.body(text, route), true);
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
            Object mention = raw.get("mention");
            forward.add(new DiscordRules.Rule(keys.toString(), to == null ? "" : to.toString(),
                    mention == null ? "" : mention.toString(), color(raw.get("color"))));
        }
        java.util.Set<String> sensors = new java.util.HashSet<>();
        ConfigurationSection sensorSection = section.getConfigurationSection("sensors");
        if (sensorSection != null) {
            for (String sensor : sensorSection.getKeys(false)) {
                if (sensorSection.getBoolean(sensor, false)) {
                    sensors.add(sensor);
                }
            }
        } else {
            // A file written before the sensors: the raid news it always had.
            sensors.add("raids");
        }
        return new DiscordRules(section.getBoolean("enabled", false), webhooks, forward,
                section.getString("username", ""), section.getString("avatar-url", ""),
                "embed".equalsIgnoreCase(section.getString("style", "text")), sensors);
    }

    /** {@code "#ffaa00"} or {@code "ffaa00"} to its value; {@code -1} for none or nonsense. */
    private int color(Object raw) {
        if (raw == null) {
            return -1;
        }
        String hex = raw.toString().trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            return hex.isEmpty() ? -1 : Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("discord.yml: color '" + raw + "' is not a hex colour like #ffaa00; ignored.");
            return -1;
        }
    }
}
