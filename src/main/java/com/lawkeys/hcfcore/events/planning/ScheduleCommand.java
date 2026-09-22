package com.lawkeys.hcfcore.events.planning;

import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.events.setup.EventYamlStore;
import com.lawkeys.hcfcore.util.ColorCodes;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code /schedule} - every event start of the next seven days, day by day: the
 * weekly schedule and each event's daily times. Staff edit the weekly schedule
 * with {@code /schedule add|remove}, which write {@code events.yml}
 * ({@code weekly-schedule.days}) like the other {@code /events} setup commands.
 */
public final class ScheduleCommand implements TabExecutor {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EventModule module;

    public ScheduleCommand(EventModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("add") || action.equals("remove")) {
            if (!sender.hasPermission(EventModule.ADMIN_PERMISSION)) {
                module.getLang().send(sender, "general.no-permission");
                return true;
            }
            return action.equals("add") ? add(sender, args) : remove(sender, args);
        }
        list(sender);
        return true;
    }

    private void list(CommandSender sender) {
        PlanningController planning = module.getPlanning();
        long now = System.currentTimeMillis();
        List<WeekAgenda.Entry> week = planning.weekAhead(now);
        module.getLang().send(sender, PlanningMessages.HEADER);
        if (week.isEmpty()) {
            module.getLang().send(sender, PlanningMessages.EMPTY);
        }
        LocalDate today = LocalDate.now(module.getSettings().timeZone());
        DateTimeFormatter dates = dateFormat();
        LocalDate shown = null;
        for (WeekAgenda.Entry entry : week) {
            LocalDate date = entry.at().toLocalDate();
            if (!date.equals(shown)) {
                shown = date;
                String day = date.equals(today) ? PlanningMessages.TODAY
                        : date.equals(today.plusDays(1)) ? PlanningMessages.TOMORROW
                        : PlanningMessages.day(date.getDayOfWeek());
                module.getLang().send(sender, PlanningMessages.DAY,
                        "day", module.getLang().get(day), "date", dates.format(date));
            }
            module.getLang().send(sender, PlanningMessages.ENTRY, "time", TIME.format(entry.at()),
                    "event", module.getLauncher().displayName(entry.eventId()).orElse(entry.eventId()));
        }
        if (!planning.getSettings().enabled()) {
            module.getLang().send(sender, PlanningMessages.DISABLED);
        }
    }

    /** {@code /schedule add <day> <HH:mm> <event>} */
    private boolean add(CommandSender sender, String[] args) {
        if (args.length < 4) {
            module.getLang().send(sender, PlanningMessages.USAGE);
            return true;
        }
        Optional<DayOfWeek> day = day(sender, args[1]);
        Optional<LocalTime> time = day.isEmpty() ? Optional.empty() : time(sender, args[2]);
        if (time.isEmpty()) {
            return true;
        }
        Optional<String> name = module.getLauncher().displayName(args[3]);
        if (name.isEmpty()) {
            module.getLang().send(sender, PlanningMessages.UNKNOWN_EVENT, "event", args[3]);
            return true;
        }
        WeeklySchedule.PlannedStart start = new WeeklySchedule.PlannedStart(day.get(), time.get(),
                args[3].toLowerCase(Locale.ROOT));
        if (module.getPlanning().getSettings().schedule().starts().contains(start)) {
            module.getLang().send(sender, PlanningMessages.ALREADY_PLANNED, "event", name.get(),
                    "day", dayName(day.get()), "time", TIME.format(time.get()));
            return true;
        }
        boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection days = daysSection(root);
            String key = day.get().name().toLowerCase(Locale.ROOT);
            List<String> entries = new ArrayList<>(days.getStringList(key));
            entries.add(start.entry());
            days.set(key, entries);
            return true;
        });
        if (!written) {
            module.getLang().send(sender, PlanningMessages.WRITE_FAILED);
            return true;
        }
        module.reloadSettings();
        module.getLang().send(sender, PlanningMessages.ADDED, "event", name.get(),
                "day", dayName(day.get()), "time", TIME.format(time.get()));
        return true;
    }

    /** {@code /schedule remove <day> <HH:mm> [event]} - every start at that time, or only that event's */
    private boolean remove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            module.getLang().send(sender, PlanningMessages.USAGE);
            return true;
        }
        Optional<DayOfWeek> day = day(sender, args[1]);
        Optional<LocalTime> time = day.isEmpty() ? Optional.empty() : time(sender, args[2]);
        if (time.isEmpty()) {
            return true;
        }
        String only = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : null;
        int[] removed = {0};
        boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection days = daysSection(root);
            for (String key : days.getKeys(false)) {
                if (WeeklySchedule.parseDay(key).orElse(null) != day.get()) {
                    continue;
                }
                List<String> kept = new ArrayList<>();
                for (String raw : days.getStringList(key)) {
                    Optional<WeeklySchedule.PlannedStart> start = WeeklySchedule.parseEntry(day.get(), raw);
                    if (start.isPresent() && start.get().time().equals(time.get())
                            && (only == null || start.get().eventId().equals(only))) {
                        removed[0]++;
                    } else {
                        kept.add(raw);
                    }
                }
                days.set(key, kept);
            }
            return removed[0] > 0;
        });
        if (removed[0] == 0) {
            module.getLang().send(sender, PlanningMessages.NOT_PLANNED,
                    "day", dayName(day.get()), "time", TIME.format(time.get()));
            return true;
        }
        if (!written) {
            module.getLang().send(sender, PlanningMessages.WRITE_FAILED);
            return true;
        }
        module.reloadSettings();
        module.getLang().send(sender, PlanningMessages.REMOVED, "count", String.valueOf(removed[0]),
                "day", dayName(day.get()), "time", TIME.format(time.get()));
        return true;
    }

    private static ConfigurationSection daysSection(ConfigurationSection root) {
        ConfigurationSection planning = root.getConfigurationSection("weekly-schedule");
        if (planning == null) {
            planning = root.createSection("weekly-schedule");
        }
        ConfigurationSection days = planning.getConfigurationSection("days");
        return days == null ? planning.createSection("days") : days;
    }

    /** @return the day typed - in English ({@code monday}, {@code mon}) or as the language file names it */
    private Optional<DayOfWeek> day(CommandSender sender, String raw) {
        Optional<DayOfWeek> day = WeeklySchedule.parseDay(raw);
        if (day.isEmpty()) {
            for (DayOfWeek candidate : DayOfWeek.values()) {
                if (dayName(candidate).equalsIgnoreCase(raw.trim())) {
                    day = Optional.of(candidate);
                    break;
                }
            }
        }
        if (day.isEmpty()) {
            module.getLang().send(sender, PlanningMessages.INVALID_DAY, "day", raw);
        }
        return day;
    }

    private Optional<LocalTime> time(CommandSender sender, String raw) {
        Optional<LocalTime> time = WeeklySchedule.parseTime(raw);
        if (time.isEmpty()) {
            module.getLang().send(sender, PlanningMessages.INVALID_TIME, "time", raw);
        }
        return time;
    }

    private String dayName(DayOfWeek day) {
        return ColorCodes.strip(module.getLang().get(PlanningMessages.day(day)));
    }

    private DateTimeFormatter dateFormat() {
        try {
            return DateTimeFormatter.ofPattern(ColorCodes.strip(module.getLang().get(PlanningMessages.DATE_FORMAT)));
        } catch (IllegalArgumentException e) {
            return DateTimeFormatter.ofPattern("dd/MM");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(EventModule.ADMIN_PERMISSION)) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            options.addAll(List.of("add", "remove"));
        } else if (args.length == 2 && (action.equals("add") || action.equals("remove"))) {
            for (DayOfWeek day : DayOfWeek.values()) {
                options.add(day.name().toLowerCase(Locale.ROOT));
            }
        } else if (args.length == 3 && action.equals("remove")) {
            WeeklySchedule.parseDay(args[1]).ifPresent(day -> module.getPlanning().getSettings().schedule().on(day)
                    .forEach(start -> options.add(TIME.format(start.time()))));
        } else if (args.length == 4) {
            options.addAll(module.getEventIds());
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().distinct().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
