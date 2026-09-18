package com.lawkeys.hcfcore.schedule.command;

import com.lawkeys.hcfcore.schedule.CustomTimers;
import com.lawkeys.hcfcore.schedule.ScheduleMessages;
import com.lawkeys.hcfcore.schedule.ScheduleModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * {@code /timer start <name> <duration> [label]}, {@code /timer stop <name>} and
 * {@code /timer list}.
 *
 * <p>Listing is for everybody - the timers are on everybody's scoreboard anyway.
 * Starting and stopping are staff.
 */
public final class TimerCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("start", "stop", "list");

    private final ScheduleModule module;

    public TimerCommand(ScheduleModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.getSettings().enabled()) {
            module.getLang().send(sender, ScheduleMessages.DISABLED);
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("list") && !sender.hasPermission(ScheduleModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        switch (sub) {
            case "list" -> list(sender);
            case "start" -> start(sender, args, label);
            case "stop" -> stop(sender, args, label);
            default -> module.getLang().send(sender, ScheduleMessages.USAGE,
                    "usage", "/" + label + " <start <name> <duration> [label]|stop <name>|list>");
        }
        return true;
    }

    private void start(CommandSender sender, String[] args, String label) {
        if (args.length < 3) {
            module.getLang().send(sender, ScheduleMessages.USAGE,
                    "usage", "/" + label + " start <name> <duration> [label]");
            return;
        }
        if (CustomTimers.normalize(args[1]).equals(ScheduleModule.KEY_ALL_TIMER)) {
            module.getLang().send(sender, ScheduleMessages.TIMER_RESERVED, "timer", args[1]);
            return;
        }
        OptionalLong seconds = Durations.parse(args[2]);
        if (seconds.isEmpty()) {
            module.getLang().send(sender, ScheduleMessages.TIMER_INVALID_DURATION, "input", args[2]);
            return;
        }
        String shown = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        if (!module.startTimer(args[1], seconds.getAsLong(), shown)) {
            module.getLang().send(sender, ScheduleMessages.TIMER_ALREADY, "timer", args[1]);
            return;
        }
        module.getLang().send(sender, ScheduleMessages.TIMER_STARTED,
                "timer", args[1], "time", Durations.formatWithSeconds(seconds.getAsLong()));
    }

    private void stop(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            module.getLang().send(sender, ScheduleMessages.USAGE, "usage", "/" + label + " stop <name>");
            return;
        }
        if (module.getTimers().stop(args[1]).isEmpty()) {
            module.getLang().send(sender, ScheduleMessages.TIMER_UNKNOWN, "timer", args[1]);
            return;
        }
        module.getLang().send(sender, ScheduleMessages.TIMER_STOPPED, "timer", args[1]);
    }

    private void list(CommandSender sender) {
        List<CustomTimers.Timer> running = module.getTimers().running();
        if (running.isEmpty()) {
            module.getLang().send(sender, ScheduleMessages.TIMER_LIST_EMPTY);
            return;
        }
        long now = System.currentTimeMillis();
        module.getLang().send(sender, ScheduleMessages.TIMER_LIST_HEADER, "count", String.valueOf(running.size()));
        for (CustomTimers.Timer timer : running) {
            module.getLang().send(sender, ScheduleMessages.TIMER_LIST_ENTRY,
                    "name", timer.name(),
                    "time", Durations.formatWithSeconds(timer.remainingSeconds(now)),
                    "label", timer.label());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(prefix)).toList();
        }
        if (args.length == 2 && sender.hasPermission(ScheduleModule.ADMIN_PERMISSION)) {
            List<String> names = new ArrayList<>();
            if (args[0].equalsIgnoreCase("stop")) {
                module.getTimers().running().forEach(timer -> names.add(timer.name()));
            } else if (args[0].equalsIgnoreCase("start")) {
                names.addAll(module.getSettings().presets().keySet());
            }
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return names.stream().filter(name -> name.startsWith(prefix)).sorted().toList();
        }
        return List.of();
    }
}
