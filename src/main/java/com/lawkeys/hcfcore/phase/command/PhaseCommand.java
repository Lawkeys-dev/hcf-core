package com.lawkeys.hcfcore.phase.command;

import com.lawkeys.hcfcore.phase.PhaseManager;
import com.lawkeys.hcfcore.phase.PhaseMessages;
import com.lawkeys.hcfcore.phase.PhaseModule;
import com.lawkeys.hcfcore.phase.PhaseResult;
import com.lawkeys.hcfcore.phase.PhaseSettings;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * {@code /sotw [enable|start [duration]|stop]}, {@code /eotw [start|stop]} and
 * {@code /purge [start [duration]|stop]}.
 *
 * <p>Parses and renders; every rule lives in {@link PhaseManager}.
 */
public final class PhaseCommand implements TabExecutor {

    private final PhaseModule module;

    public PhaseCommand(PhaseModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // The phase is loaded data: nothing is started, stopped or read before it is in.
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        boolean sotw = command.getName().equalsIgnoreCase("sotw");
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        PhaseManager manager = module.getManager();
        if (command.getName().equalsIgnoreCase("purge")) {
            return purge(sender, args, action, label, manager);
        }

        switch (action) {
            case "" -> {
                if (sotw) {
                    sotwStatus(sender, manager);
                } else {
                    eotwStatus(sender, manager);
                }
            }
            case "enable" -> {
                if (!sotw) {
                    return unknown(sender, label);
                }
                if (!(sender instanceof Player player)) {
                    module.getLang().send(sender, "general.player-only");
                    return true;
                }
                render(sender, manager.enablePvp(player.getUniqueId()));
            }
            case "start" -> {
                if (!isStaff(sender)) {
                    return true;
                }
                if (!sotw) {
                    render(sender, manager.startEotw());
                    return true;
                }
                OptionalLong seconds = args.length > 1
                        ? Durations.parse(args[1])
                        : OptionalLong.of(module.getSettings().sotwDurationSeconds());
                if (seconds.isEmpty()) {
                    module.getLang().send(sender, PhaseMessages.INVALID_DURATION, "input", args[1]);
                    return true;
                }
                render(sender, manager.startSotw(seconds.getAsLong()));
            }
            case "stop" -> {
                if (isStaff(sender)) {
                    render(sender, sotw ? manager.stopSotw() : manager.stopEotw());
                }
            }
            default -> {
                return unknown(sender, label);
            }
        }
        return true;
    }

    private boolean purge(CommandSender sender, String[] args, String action, String label, PhaseManager manager) {
        switch (action) {
            case "" -> purgeStatus(sender, manager);
            case "start" -> {
                if (!isStaff(sender)) {
                    return true;
                }
                OptionalLong seconds = args.length > 1
                        ? Durations.parse(args[1])
                        : OptionalLong.of(module.getSettings().purge().durationSeconds());
                if (seconds.isEmpty()) {
                    module.getLang().send(sender, PhaseMessages.INVALID_DURATION, "input", args[1]);
                    return true;
                }
                render(sender, manager.startPurge(seconds.getAsLong()));
            }
            case "stop" -> {
                if (isStaff(sender)) {
                    render(sender, manager.stopPurge());
                }
            }
            default -> {
                return unknown(sender, label);
            }
        }
        return true;
    }

    private void purgeStatus(CommandSender sender, PhaseManager manager) {
        if (manager.isPurge()) {
            module.getLang().send(sender, PhaseMessages.PURGE_STATUS_ACTIVE,
                    "time", Durations.format(manager.getPurgeRemainingSeconds()));
            return;
        }
        Optional<String> next = module.nextPurge();
        if (next.isPresent()) {
            module.getLang().send(sender, PhaseMessages.PURGE_STATUS_SCHEDULED, "time", next.get());
        } else {
            module.getLang().send(sender, PhaseMessages.PURGE_STATUS_INACTIVE);
        }
    }

    private void sotwStatus(CommandSender sender, PhaseManager manager) {
        PhaseSettings settings = module.getSettings();
        if (manager.isSotw()) {
            module.getLang().send(sender, PhaseMessages.SOTW_STATUS_ACTIVE,
                    "time", Durations.format(manager.getSotwRemainingSeconds()));
            if (sender instanceof Player player) {
                module.getLang().send(sender, manager.hasEnabledPvp(player.getUniqueId())
                        ? PhaseMessages.SOTW_STATUS_ENABLED : PhaseMessages.SOTW_STATUS_PROTECTED);
            }
        } else if (settings.sotwScheduledAt() > System.currentTimeMillis()) {
            module.getLang().send(sender, PhaseMessages.SOTW_STATUS_SCHEDULED,
                    "time", module.formatDate(settings.sotwScheduledAt()));
        } else {
            module.getLang().send(sender, PhaseMessages.SOTW_STATUS_INACTIVE);
        }
    }

    private void eotwStatus(CommandSender sender, PhaseManager manager) {
        PhaseSettings settings = module.getSettings();
        if (manager.isEotw()) {
            module.getLang().send(sender, PhaseMessages.EOTW_STATUS_ACTIVE);
        } else if (settings.eotwScheduledAt() > System.currentTimeMillis()) {
            module.getLang().send(sender, PhaseMessages.EOTW_STATUS_SCHEDULED,
                    "time", module.formatDate(settings.eotwScheduledAt()));
        } else {
            module.getLang().send(sender, PhaseMessages.EOTW_STATUS_INACTIVE);
        }
    }

    private void render(CommandSender sender, PhaseResult result) {
        if (result.messageKey() != null) {
            module.getLang().send(sender, result.messageKey(), result.placeholders());
        }
        result.broadcast().ifPresent(module::broadcast);
        if (result.success()) {
            module.flushIfPending();
        }
    }

    private boolean isStaff(CommandSender sender) {
        if (sender.hasPermission(PhaseModule.ADMIN_PERMISSION)) {
            return true;
        }
        module.getLang().send(sender, "general.no-permission");
        return false;
    }

    private boolean unknown(CommandSender sender, String label) {
        module.getLang().send(sender, "general.unknown-command", "label", label);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("sotw")) {
            options.add("enable");
        }
        // start and stop for staff, whichever of the three commands it is.
        if (sender.hasPermission(PhaseModule.ADMIN_PERMISSION)) {
            options.add("start");
            options.add("stop");
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(prefix));
        return options;
    }
}
