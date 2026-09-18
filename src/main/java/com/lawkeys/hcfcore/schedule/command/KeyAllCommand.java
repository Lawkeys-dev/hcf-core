package com.lawkeys.hcfcore.schedule.command;

import com.lawkeys.hcfcore.schedule.ScheduleMessages;
import com.lawkeys.hcfcore.schedule.ScheduleModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * {@code /keyall [countdown]} - the configured key-all commands, once for every
 * player online, now or when a countdown everybody can see runs out.
 *
 * <p>This plugin has no crates, so a key-all is whatever the commands under
 * {@code key-all} in {@code schedule.yml} give: the crate plugin's own command,
 * money, a kit. With none configured it refuses rather than announcing a key-all
 * that hands out nothing.
 */
public final class KeyAllCommand implements TabExecutor {

    private final ScheduleModule module;

    public KeyAllCommand(ScheduleModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ScheduleModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!module.getSettings().enabled()) {
            module.getLang().send(sender, ScheduleMessages.DISABLED);
            return true;
        }
        if (module.getSettings().keyAll().commands().isEmpty()) {
            module.getLang().send(sender, ScheduleMessages.KEYALL_NOT_CONFIGURED);
            return true;
        }
        if (args.length == 0) {
            int count = module.keyAll();
            module.getLang().send(sender, ScheduleMessages.KEYALL_DONE, "count", String.valueOf(count));
            return true;
        }
        OptionalLong seconds = Durations.parse(args[0]);
        if (seconds.isEmpty()) {
            module.getLang().send(sender, ScheduleMessages.TIMER_INVALID_DURATION, "input", args[0]);
            return true;
        }
        if (!module.startTimer(ScheduleModule.KEY_ALL_TIMER, seconds.getAsLong(), null)) {
            module.getLang().send(sender, ScheduleMessages.TIMER_ALREADY, "timer", ScheduleModule.KEY_ALL_TIMER);
            return true;
        }
        module.getLang().send(sender, ScheduleMessages.KEYALL_SCHEDULED,
                "time", Durations.formatWithSeconds(seconds.getAsLong()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
