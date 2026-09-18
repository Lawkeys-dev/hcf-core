package com.lawkeys.hcfcore.pvpclass.command;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.pvpclass.ClassMessages;
import com.lawkeys.hcfcore.pvpclass.ClassModule;
import com.lawkeys.hcfcore.pvpclass.DyesMenu;
import com.lawkeys.hcfcore.pvpclass.PvpClass;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * {@code /dyes} - which colour of a class's leather set gives its arrows which
 * effect: a menu for a player, the same list in chat for the console. Open to
 * everybody.
 */
public final class DyesCommand implements TabExecutor {

    private final ClassModule module;

    public DyesCommand(ClassModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LangManager lang = module.getLang();
        if (!module.getSettings().enabled()) {
            lang.send(sender, ClassMessages.DISABLED);
            return true;
        }
        if (sender instanceof Player player) {
            if (!DyesMenu.open(module, player)) {
                lang.send(sender, ClassMessages.DYES_NONE);
            }
            return true;
        }
        List<DyesMenu.Entry> entries = DyesMenu.entries(module.getSettings());
        if (entries.isEmpty()) {
            lang.send(sender, ClassMessages.DYES_NONE);
            return true;
        }
        for (DyesMenu.Entry entry : entries) {
            lang.send(sender, ClassMessages.INFO_DYE, "colour", PvpClass.readableItem(entry.colour()),
                    "effect", entry.effect().effect().displayName(),
                    "chance", ClassModule.formatNumber(entry.effect().chance()),
                    "seconds", String.valueOf(entry.effect().effect().seconds()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
