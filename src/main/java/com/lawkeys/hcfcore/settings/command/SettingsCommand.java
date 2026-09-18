package com.lawkeys.hcfcore.settings.command;

import com.lawkeys.hcfcore.settings.PlayerSetting;
import com.lawkeys.hcfcore.settings.SettingsMenu;
import com.lawkeys.hcfcore.settings.SettingsMessages;
import com.lawkeys.hcfcore.settings.SettingsModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code /settings} - the menu, or {@code /settings <setting> [on|off]} and
 * {@code /settings list} for players who would rather type - and {@code /cobble},
 * the classic HCF shortcut for the cobblestone switch.
 */
public final class SettingsCommand implements TabExecutor {

    private final SettingsModule module;

    public SettingsCommand(SettingsModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return true;
        }
        if (!module.isEnabled()) {
            module.getLang().send(sender, SettingsMessages.DISABLED);
            return true;
        }
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        if (command.getName().equalsIgnoreCase("cobble")) {
            change(player, PlayerSetting.COBBLESTONE, args.length > 0 ? args[0] : null);
            return true;
        }
        if (args.length == 0) {
            SettingsMenu.open(module, player);
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            list(player);
            return true;
        }
        Optional<PlayerSetting> setting = module.parse(args[0]);
        if (setting.isEmpty()) {
            module.getLang().send(player, SettingsMessages.UNKNOWN, "input", args[0],
                    "settings", String.join(", ", module.offered().stream().map(PlayerSetting::key).toList()));
            return true;
        }
        change(player, setting.get(), args.length > 1 ? args[1] : null);
        return true;
    }

    /** @param wanted "on", "off", or {@code null} to flip it */
    private void change(Player player, PlayerSetting setting, String wanted) {
        if (!module.offered().contains(setting)) {
            module.getLang().send(player, SettingsMessages.DISABLED);
            return;
        }
        boolean on;
        if (wanted == null) {
            on = module.toggle(player, setting);
        } else if (wanted.equalsIgnoreCase("on") || wanted.equalsIgnoreCase("off")) {
            on = wanted.equalsIgnoreCase("on");
            module.set(player, setting, on);
        } else {
            module.getLang().send(player, SettingsMessages.USAGE,
                    "usage", "/settings " + setting.key() + " [on|off]");
            return;
        }
        module.getLang().send(player, SettingsMessages.CHANGED, "setting", module.displayName(setting),
                "state", module.getLang().get(on ? SettingsMessages.STATE_ON : SettingsMessages.STATE_OFF));
    }

    private void list(Player player) {
        module.getLang().send(player, SettingsMessages.LIST_HEADER);
        for (PlayerSetting setting : module.offered()) {
            module.getLang().send(player, SettingsMessages.LIST_ENTRY,
                    "setting", module.displayName(setting), "key", setting.key(),
                    "state", module.getLang().get(module.isOn(player, setting)
                            ? SettingsMessages.STATE_ON : SettingsMessages.STATE_OFF));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("cobble")) {
            if (args.length == 1) {
                options.addAll(List.of("on", "off"));
            }
        } else if (args.length == 1) {
            options.add("list");
            module.offered().forEach(setting -> options.add(setting.key()));
        } else if (args.length == 2 && module.parse(args[0]).isPresent()) {
            options.addAll(List.of("on", "off"));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
