package com.lawkeys.hcfcore.events.planning;

import com.lawkeys.hcfcore.events.EventLauncher;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Runs the weekly schedule: starts each planned event at its time, exactly as
 * {@code /events start} would, and announces every start - weekly or daily - a few
 * minutes ahead ({@code weekly-schedule.announce-before-minutes}).
 *
 * <p>Ticked by {@link EventModule} with the events, on the main thread. The first
 * tick after a start or a reload fires nothing: a time that passed while the
 * server was down, or before the reload, is not caught up.
 */
public final class PlanningController {

    /**
     * {@code weekly-schedule.menu:} - {@code /schedule} as a window rather than a
     * list in the chat.
     *
     * @param todayMaterial the item standing for today, then a day with events, then
     *                      a day with none
     * @param colors        the colour each event is written in, by event id or by kind
     *                      ({@code koth}, {@code citadel}, {@code ktk}, {@code conquest},
     *                      {@code dtc}, {@code lastbreak}, {@code slide}, {@code totem},
     *                      {@code minitotem}) - an id wins over its kind - so the week
     *                      reads at a glance (the owner's request of 28/09/2026)
     */
    public record MenuRules(boolean enabled, String todayMaterial, String dayMaterial, String emptyMaterial,
                            Map<String, String> colors) {

        public MenuRules {
            Objects.requireNonNull(todayMaterial, "todayMaterial");
            Objects.requireNonNull(dayMaterial, "dayMaterial");
            Objects.requireNonNull(emptyMaterial, "emptyMaterial");
            colors = Map.copyOf(Objects.requireNonNull(colors, "colors"));
        }

        public MenuRules(boolean enabled, String todayMaterial, String dayMaterial, String emptyMaterial) {
            this(enabled, todayMaterial, dayMaterial, emptyMaterial, DEFAULT_COLORS);
        }

        /** One colour per kind of event, as shipped. */
        public static final Map<String, String> DEFAULT_COLORS = Map.of(
                "koth", "&6", "citadel", "&5", "ktk", "&c", "conquest", "&b", "dtc", "&9",
                "lastbreak", "&4", "slide", "&a", "totem", "&e", "minitotem", "&3");

        public static MenuRules defaults() {
            return new MenuRules(true, "CLOCK", "PAPER", "GRAY_DYE", DEFAULT_COLORS);
        }

        /** @return the colour codes to write this event in; empty for its own name's colours */
        public String colorOf(String eventId, String kind) {
            String byId = eventId == null ? null : colors.get(eventId.toLowerCase(java.util.Locale.ROOT));
            if (byId != null) {
                return byId;
            }
            return kind == null ? "" : colors.getOrDefault(kind, "");
        }
    }

    /**
     * What a planned start does to an event still running when it comes (the owner's
     * request of 28/09/2026: the 14:00 KOTH not over at 15:00, when a Citadel is due).
     */
    public enum Overlap {
        /** The running event is stopped with no winner, and the planned one starts. */
        REPLACE,
        /** The running event goes on, and the planned one is not started. */
        SKIP,
        /** Both run - different events only; one cannot run twice. */
        BOTH;

        static java.util.Optional<Overlap> of(String raw) {
            if (raw == null || raw.isBlank()) {
                return java.util.Optional.empty();
            }
            try {
                return java.util.Optional.of(valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return java.util.Optional.empty();
            }
        }
    }

    /** {@code weekly-schedule:} in {@code events.yml}. */
    public record Settings(boolean enabled, List<Integer> announceBeforeMinutes, WeeklySchedule schedule,
                           MenuRules menu, Overlap overlap) {

        public Settings {
            announceBeforeMinutes = List.copyOf(announceBeforeMinutes);
            Objects.requireNonNull(schedule, "schedule");
            Objects.requireNonNull(menu, "menu");
            Objects.requireNonNull(overlap, "overlap");
        }

        public Settings(boolean enabled, List<Integer> announceBeforeMinutes, WeeklySchedule schedule, MenuRules menu) {
            this(enabled, announceBeforeMinutes, schedule, menu, Overlap.REPLACE);
        }

        public static Settings defaults() {
            return new Settings(true, List.of(15, 5, 1), WeeklySchedule.empty(), MenuRules.defaults(), Overlap.REPLACE);
        }
    }

    private final EventModule module;
    private volatile Settings settings = Settings.defaults();
    /** The end of the window the last tick looked at; {@code -1} before the first. */
    private long lastTick = -1;

    public PlanningController(EventModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    public Settings getSettings() {
        return settings;
    }

    /** Reads {@code weekly-schedule:}; an absent section is an empty schedule, switched on. */
    public static Settings load(ConfigurationSection section, Consumer<String> warn) {
        if (section == null) {
            return Settings.defaults();
        }
        List<Integer> before = new ArrayList<>();
        for (Integer minutes : section.getIntegerList("announce-before-minutes")) {
            if (minutes != null && minutes > 0) {
                before.add(minutes);
            } else {
                warn.accept("weekly-schedule: ignoring a non-positive announce-before-minutes entry.");
            }
        }
        Map<String, List<String>> days = new LinkedHashMap<>();
        ConfigurationSection daySection = section.getConfigurationSection("days");
        if (daySection != null) {
            for (String day : daySection.getKeys(false)) {
                days.put(day, daySection.getStringList(day));
            }
        }
        ConfigurationSection menu = section.getConfigurationSection("menu");
        Map<String, String> colors = new LinkedHashMap<>(MenuRules.DEFAULT_COLORS);
        ConfigurationSection colorSection = menu == null ? null : menu.getConfigurationSection("colors");
        if (colorSection != null) {
            for (String key : colorSection.getKeys(false)) {
                colors.put(key.toLowerCase(java.util.Locale.ROOT), colorSection.getString(key, ""));
            }
        }
        MenuRules menuRules = menu == null ? MenuRules.defaults() : new MenuRules(
                menu.getBoolean("enabled", MenuRules.defaults().enabled()),
                menu.getString("today-material", MenuRules.defaults().todayMaterial()),
                menu.getString("day-material", MenuRules.defaults().dayMaterial()),
                menu.getString("empty-material", MenuRules.defaults().emptyMaterial()),
                colors);
        String rawOverlap = section.getString("overlap");
        Overlap overlap = rawOverlap == null ? Overlap.REPLACE : Overlap.of(rawOverlap).orElseGet(() -> {
            warn.accept("weekly-schedule: overlap '" + rawOverlap + "' is not replace, skip or both; replace is used.");
            return Overlap.REPLACE;
        });
        return new Settings(section.getBoolean("enabled", true),
                section.contains("announce-before-minutes") ? before : Settings.defaults().announceBeforeMinutes(),
                WeeklySchedule.parse(days, warn), menuRules, overlap);
    }

    /** Takes new settings and warns about planned events that do not exist. */
    public void applySettings(Settings replacement) {
        this.settings = Objects.requireNonNull(replacement, "replacement");
        this.lastTick = -1;
        for (WeeklySchedule.PlannedStart start : replacement.schedule().starts()) {
            if (module.getLauncher().displayName(start.eventId()).isEmpty()) {
                module.getPlugin().getLogger().warning("events.yml: weekly-schedule plans '" + start.eventId()
                        + "' on " + start.day() + " at " + start.time() + ", but no event has that id.");
            }
        }
    }

    public void tick(long now) {
        long from = lastTick;
        lastTick = now;
        Settings current = settings;
        if (from < 0 || !current.enabled() || now <= from) {
            return;
        }
        var zone = module.getSettings().timeZone();
        Map<String, List<LocalTime>> daily = dailyTimes();

        for (int minutes : current.announceBeforeMinutes()) {
            long ahead = minutes * 60_000L;
            for (WeekAgenda.Entry entry : WeekAgenda.between(current.schedule(), daily, zone, from + ahead, now + ahead)) {
                module.getLauncher().displayName(entry.eventId()).ifPresent(name -> module.broadcast(
                        PlanningMessages.SOON, Map.of("event", name, "time", Durations.format(minutes * 60L))));
            }
        }

        for (WeekAgenda.Entry entry : WeekAgenda.between(current.schedule(), Map.of(), zone, from, now)) {
            if (!module.getStartup().isReady()) {
                module.getPlugin().getLogger().warning("Weekly schedule: '" + entry.eventId()
                        + "' not started - the plugin is still loading its data.");
                continue;
            }
            // Kill the King is started by staff only: its time is shown, never acted on.
            if (module.getLauncher().kindOf(entry.eventId())
                    .filter(kind -> kind == com.lawkeys.hcfcore.events.setup.EventKind.KING).isPresent()) {
                module.getPlugin().getLogger().info("Weekly schedule: '" + entry.eventId()
                        + "' is due - Kill the King is started by staff, with /events start " + entry.eventId() + ".");
                continue;
            }
            List<String> running = new ArrayList<>();
            for (String id : module.getEventIds()) {
                if (!id.equalsIgnoreCase(entry.eventId()) && module.isRunning(id)) {
                    running.add(id);
                }
            }
            if (!running.isEmpty() && current.overlap() == Overlap.SKIP) {
                module.getPlugin().getLogger().info("Weekly schedule: '" + entry.eventId() + "' not started - "
                        + String.join(", ", running) + " still running (overlap: skip).");
                continue;
            }
            if (!running.isEmpty() && current.overlap() == Overlap.REPLACE) {
                for (String id : running) {
                    module.getLauncher().stop(id);
                    module.getPlugin().getLogger().info("Weekly schedule: '" + id + "' stopped for '"
                            + entry.eventId() + "' (overlap: replace).");
                }
            }
            EventLauncher.Result result = module.getLauncher().start(entry.eventId());
            if (!result.done()) {
                module.getPlugin().getLogger().warning("Weekly schedule: '" + entry.eventId() + "' could not start ("
                        + (result.messageKey() == null ? "called off" : com.lawkeys.hcfcore.util.ColorCodes.strip(module.getLang().get(
                                result.messageKey(), "event", result.event()))) + ").");
            }
        }
    }

    /** @return the week ahead: every start in the next seven days, weekly and daily */
    public List<WeekAgenda.Entry> weekAhead(long now) {
        return WeekAgenda.between(settings.schedule(), dailyTimes(), module.getSettings().timeZone(),
                now, now + 7L * 24 * 3600 * 1000);
    }

    /** @return each event's own daily times, whatever its engine */
    private Map<String, List<LocalTime>> dailyTimes() {
        Map<String, List<LocalTime>> daily = new LinkedHashMap<>();
        if (module.getManager() == null || !module.getSettings().enabled()) {
            return daily;
        }
        module.getSettings().definitions().forEach(d -> daily.put(d.id(), d.schedule()));
        if (module.getKing() != null) {
            module.getKing().getSettings().definitions().forEach(d -> daily.put(d.id(), d.schedule()));
        }
        if (module.getConquest() != null) {
            module.getConquest().getSettings().definitions().forEach(d -> daily.put(d.id(), d.schedule()));
        }
        if (module.getCore() != null) {
            module.getCore().getSettings().definitions().forEach(d -> daily.put(d.id(), d.schedule()));
        }
        if (module.getSlide() != null) {
            module.getSlide().getSettings().definitions().forEach(d -> daily.put(d.id(), d.schedule()));
        }
        if (module.getTotem() != null) {
            module.getTotem().getSettings().definitions().forEach(d -> daily.put(d.id(), d.schedule()));
        }
        return daily;
    }
}
