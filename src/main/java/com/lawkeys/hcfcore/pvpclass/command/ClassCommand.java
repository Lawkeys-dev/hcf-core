package com.lawkeys.hcfcore.pvpclass.command;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.pvpclass.ClassManager;
import com.lawkeys.hcfcore.pvpclass.ClassMessages;
import com.lawkeys.hcfcore.pvpclass.ClassModule;
import com.lawkeys.hcfcore.pvpclass.PvpClass;
import com.lawkeys.hcfcore.pvpclass.ClassEffect;
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
import java.util.stream.Collectors;

/**
 * {@code /class} - your class, energy and warmup; {@code /class list} - the classes
 * and their armour; {@code /class info <class>} - everything a class does. Open to
 * everybody: a class is chosen by what one wears, not by a command.
 */
public final class ClassCommand implements TabExecutor {

    private final ClassModule module;

    public ClassCommand(ClassModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LangManager lang = module.getLang();
        if (!module.getSettings().enabled()) {
            lang.send(sender, ClassMessages.DISABLED);
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                status(player);
            } else {
                lang.send(sender, ClassMessages.USAGE, "usage", "/" + label + " <list|info <class>>");
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "info" -> {
                if (args.length < 2) {
                    lang.send(sender, ClassMessages.USAGE, "usage", "/" + label + " info <class>");
                } else {
                    info(sender, args[1]);
                }
            }
            default -> {
                // "/class bard" reads as "tell me about the bard".
                if (module.getSettings().find(args[0]).isPresent()) {
                    info(sender, args[0]);
                } else {
                    lang.send(sender, ClassMessages.USAGE, "usage", "/" + label + " [list|info <class>]");
                }
            }
        }
        return true;
    }

    private void status(Player player) {
        LangManager lang = module.getLang();
        ClassManager manager = module.getManager();
        long now = System.currentTimeMillis();
        Optional<PvpClass> active = manager.active(player.getUniqueId());
        if (active.isPresent()) {
            lang.send(player, ClassMessages.STATUS_ACTIVE, "class", active.get().displayName());
            if (active.get().hasEnergy()) {
                lang.send(player, ClassMessages.STATUS_ENERGY,
                        "energy", String.valueOf((int) Math.floor(manager.energy(player.getUniqueId(), now))),
                        "max", ClassModule.formatNumber(active.get().energy().max()));
            }
            return;
        }
        Optional<PvpClass> pending = manager.pending(player.getUniqueId());
        if (pending.isPresent()) {
            lang.send(player, ClassMessages.STATUS_WARMUP, "class", pending.get().displayName(),
                    "time", Durations.formatWithSeconds(manager.warmupRemaining(player.getUniqueId(), now)));
            return;
        }
        lang.send(player, ClassMessages.STATUS_NONE);
    }

    private void list(CommandSender sender) {
        LangManager lang = module.getLang();
        List<PvpClass> classes = module.getSettings().classes();
        if (classes.isEmpty()) {
            lang.send(sender, ClassMessages.LIST_EMPTY);
            return;
        }
        lang.send(sender, ClassMessages.LIST_HEADER);
        for (PvpClass pvpClass : classes) {
            Optional<String> set = pvpClass.armorSet();
            if (set.isPresent()) {
                lang.send(sender, ClassMessages.LIST_ENTRY_SET, "class", pvpClass.displayName(), "id", pvpClass.id(),
                        "set", set.get());
            } else {
                lang.send(sender, ClassMessages.LIST_ENTRY, "class", pvpClass.displayName(), "id", pvpClass.id(),
                        "armor", pvpClass.armorPieces());
            }
        }
    }

    private void info(CommandSender sender, String id) {
        LangManager lang = module.getLang();
        Optional<PvpClass> found = module.getSettings().find(id);
        if (found.isEmpty()) {
            lang.send(sender, ClassMessages.UNKNOWN, "class", id);
            return;
        }
        PvpClass pvpClass = found.get();
        lang.send(sender, ClassMessages.INFO_HEADER, "class", pvpClass.displayName());
        lang.send(sender, ClassMessages.INFO_ARMOR, "armor", pvpClass.armorPieces());
        if (!pvpClass.passiveEffects().isEmpty()) {
            lang.send(sender, ClassMessages.INFO_PASSIVE, "effects", pvpClass.passiveEffects().entrySet().stream()
                    .map(entry -> ClassEffect.displayName(entry.getKey()) + " " + ClassEffect.roman(entry.getValue()))
                    .collect(Collectors.joining(", ")));
        }
        if (pvpClass.hasEnergy()) {
            lang.send(sender, ClassMessages.INFO_ENERGY,
                    "max", ClassModule.formatNumber(pvpClass.energy().max()),
                    "per-second", ClassModule.formatNumber(pvpClass.energy().perSecond()));
        }
        pvpClass.heldEffects().forEach((item, held) -> lang.send(sender, ClassMessages.INFO_HELD,
                "item", PvpClass.readableItem(item), "effect", held.effect().displayName(),
                "targets", held.target().configName(), "radius", ClassModule.formatNumber(held.radius())));
        pvpClass.clickEffects().forEach((item, click) -> lang.send(sender, ClassMessages.INFO_CLICK,
                "item", PvpClass.readableItem(item), "effect", click.effect().displayName(),
                "seconds", String.valueOf(click.effect().seconds()),
                "targets", click.target().configName(),
                "cost", click.energyCost() <= 0 ? ""
                        : lang.get(ClassMessages.INFO_COST, "energy", String.valueOf(click.energyCost())),
                "cooldown", click.cooldownSeconds() <= 0 ? ""
                        : lang.get(ClassMessages.INFO_COOLDOWN, "seconds", String.valueOf(click.cooldownSeconds()))));
        if (pvpClass.archerTag() != null) {
            lang.send(sender, ClassMessages.INFO_ARCHER_TAG,
                    "percent", String.valueOf(pvpClass.archerTag().percent()),
                    "seconds", String.valueOf(pvpClass.archerTag().seconds()));
        }
        if (pvpClass.backstab() != null) {
            lang.send(sender, ClassMessages.INFO_BACKSTAB,
                    "item", PvpClass.readableItem(pvpClass.backstab().weapon()),
                    "hearts", ClassModule.formatNumber(pvpClass.backstab().damage() / 2.0),
                    "cooldown", String.valueOf(pvpClass.backstab().cooldownSeconds()));
        }
        if (pvpClass.invisibleBelowY() != null) {
            lang.send(sender, ClassMessages.INFO_INVISIBLE, "y", String.valueOf(pvpClass.invisibleBelowY()));
        }
        if (pvpClass.maxPerTeam() > 0) {
            lang.send(sender, ClassMessages.INFO_LIMIT, "max", String.valueOf(pvpClass.maxPerTeam()));
        }
        if (!pvpClass.permission().isEmpty()) {
            lang.send(sender, ClassMessages.INFO_PERMISSION, "permission", pvpClass.permission());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> ids = module.getSettings().classes().stream().map(PvpClass::id).toList();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("list");
            options.add("info");
            options.addAll(ids);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            options.addAll(ids);
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(typed)).toList();
    }
}
