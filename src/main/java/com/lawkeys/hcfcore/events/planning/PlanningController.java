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
     */
    public record MenuRules(boolean enabled, String todayMaterial, String dayMaterial, String emptyMaterial) {

        public MenuRules {
            Objects.requireNonNull(todayMaterial, "todayMaterial");
            Objects.requireNonNull(dayMaterial, "dayMaterial");
            Objects.requireNonNull(emptyMaterial, "emptyMaterial");
        }

        public static MenuRules defaults() {
            return new MenuRules(true, "CLOCK", "PAPER", "GRAY_DYE");
        }
    }

    /** {@code weekly-schedule:} in {@code events.yml}. */
    public record Settings(boolean enabled, List<Integer> announceBeforeMinutes, WeeklySchedule schedule,
                           MenuRules menu) {

        public Settings {
            announceBeforeMinutes = List.copyOf(announceBeforeMinutes);
            Objects.requireNonNull(schedule, "schedule");
            Objects.requireNonNull(menu, "menu");
        }

        public static Settings defaults() {
            return new Settings(true, List.of(15, 5, 1), WeeklySchedule.empty(), MenuRules.defaults());
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
        MenuRules menuRules = menu == null ? MenuRules.defaults() : new MenuRules(
                menu.getBoolean("enabled", MenuRules.defaults().enabled()),
                menu.getString("today-material", MenuRules.defaults().todayMaterial()),
                menu.getString("day-material", MenuRules.defaults().dayMaterial()),
                menu.getString("empty-material", MenuRules.defaults().emptyMaterial()));
        return new Settings(section.getBoolean("enabled", true),
                section.contains("announce-before-minutes") ? before : Settings.defaults().announceBeforeMinutes(),
                WeeklySchedule.parse(days, warn), menuRules);
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
