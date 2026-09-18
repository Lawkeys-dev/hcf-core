package com.lawkeys.hcfcore.hologram.command;

import com.lawkeys.hcfcore.hologram.Hologram;
import com.lawkeys.hcfcore.hologram.HologramMessages;
import com.lawkeys.hcfcore.hologram.HologramModule;
import com.lawkeys.hcfcore.hologram.Holograms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code /hologram} - create, write, move, list and delete holograms.
 *
 * <p>A hologram is created where the command is typed, at eye height, and a line
 * number is counted from 1 as {@code list} shows it.
 */
public final class HologramCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS =
            List.of("create", "addline", "setline", "removeline", "movehere", "tp", "list", "delete");

    private final HologramModule module;

    public HologramCommand(HologramModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(HologramModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!module.isEnabled()) {
            module.getLang().send(sender, HologramMessages.DISABLED);
            return true;
        }
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "create" -> create(sender, args, label);
            case "addline" -> addLine(sender, args, label);
            case "setline" -> setLine(sender, args, label);
            case "removeline" -> removeLine(sender, args, label);
            case "movehere" -> moveHere(sender, args, label);
            case "tp" -> teleport(sender, args, label);
            case "delete" -> delete(sender, args, label);
            default -> module.getLang().send(sender, HologramMessages.USAGE,
                    "usage", "/" + label + " <" + String.join("|", SUBCOMMANDS) + ">");
        }
        return true;
    }

    private Holograms holograms() {
        return module.getHolograms();
    }

    private void create(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (args.length < 3) {
            module.getLang().send(sender, HologramMessages.USAGE, "usage", "/" + label + " create <id> <first line>");
            return;
        }
        if (Holograms.normalize(args[1]) == null) {
            module.getLang().send(sender, HologramMessages.INVALID_ID, "id", args[1]);
            return;
        }
        Location at = player.getEyeLocation();
        if (!holograms().create(args[1], at.getWorld().getName(), at.getX(), at.getY(), at.getZ(),
                List.of(rest(args, 2)))) {
            module.getLang().send(sender, HologramMessages.EXISTS, "id", args[1]);
            return;
        }
        changed(args[1]);
        module.getLang().send(sender, HologramMessages.CREATED, "id", args[1]);
    }

    private void addLine(CommandSender sender, String[] args, String label) {
        if (args.length < 3) {
            module.getLang().send(sender, HologramMessages.USAGE, "usage", "/" + label + " addline <id> <text>");
            return;
        }
        existing(sender, args[1]).ifPresent(hologram -> {
            List<String> lines = new ArrayList<>(hologram.lines());
            lines.add(rest(args, 2));
            write(sender, hologram, lines);
        });
    }

    private void setLine(CommandSender sender, String[] args, String label) {
        if (args.length < 4) {
            module.getLang().send(sender, HologramMessages.USAGE, "usage", "/" + label + " setline <id> <line> <text>");
            return;
        }
        existing(sender, args[1]).ifPresent(hologram -> lineIndex(sender, hologram, args[2]).ifPresent(index -> {
            List<String> lines = new ArrayList<>(hologram.lines());
            lines.set(index, rest(args, 3));
            write(sender, hologram, lines);
        }));
    }

    private void removeLine(CommandSender sender, String[] args, String label) {
        if (args.length != 3) {
            module.getLang().send(sender, HologramMessages.USAGE, "usage", "/" + label + " removeline <id> <line>");
            return;
        }
        existing(sender, args[1]).ifPresent(hologram -> lineIndex(sender, hologram, args[2]).ifPresent(index -> {
            List<String> lines = new ArrayList<>(hologram.lines());
            lines.remove((int) index);
            write(sender, hologram, lines);
        }));
    }

    private void write(CommandSender sender, Hologram hologram, List<String> lines) {
        if (!holograms().setLines(hologram.id(), lines)) {
            module.getLang().send(sender, HologramMessages.TOO_MANY_LINES, "max", String.valueOf(Holograms.MAX_LINES));
            return;
        }
        changed(hologram.id());
        module.getLang().send(sender, HologramMessages.UPDATED, "id", hologram.id());
    }

    private void moveHere(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (args.length != 2) {
            module.getLang().send(sender, HologramMessages.USAGE, "usage", "/" + label + " movehere <id>");
            return;
        }
        Location at = player.getEyeLocation();
        if (!holograms().move(args[1], at.getWorld().getName(), at.getX(), at.getY(), at.getZ())) {
            module.getLang().send(sender, HologramMessages.UNKNOWN, "id", args[1]);
            return;
        }
        changed(args[1]);
        module.getLang().send(sender, HologramMessages.MOVED, "id", args[1]);
    }

    private void teleport(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (args.length != 2) {
            module.getLang().send(sender, HologramMessages.USAGE, "usage", "/" + label + " tp <id>");
            return;
        }
        existing(sender, args[1]).ifPresent(hologram -> {
            World world = Bukkit.getWorld(hologram.world());
            if (world == null) {
                module.getLang().send(sender, HologramMessages.WORLD_GONE, "world", hologram.world());
                return;
            }
            player.teleportAsync(new Location(world, hologram.x(), hologram.y(), hologram.z(),
                    player.getYaw(), player.getPitch()));
        });
    }

    private void delete(CommandSender sender, String[] args, String label) {
        if (args.length != 2) {
            module.getLang().send(sender, HologramMessages.USAGE, "usage", "/" + label + " delete <id>");
            return;
        }
        if (!holograms().delete(args[1])) {
            module.getLang().send(sender, HologramMessages.UNKNOWN, "id", args[1]);
            return;
        }
        changed(args[1]);
        module.getLang().send(sender, HologramMessages.DELETED, "id", args[1]);
    }

    private void list(CommandSender sender) {
        List<Hologram> all = holograms().list();
        if (all.isEmpty()) {
            module.getLang().send(sender, HologramMessages.LIST_EMPTY);
            return;
        }
        module.getLang().send(sender, HologramMessages.LIST_HEADER, "count", String.valueOf(all.size()));
        for (Hologram hologram : all) {
            module.getLang().send(sender, HologramMessages.LIST_ENTRY,
                    "id", hologram.id(), "world", hologram.world(),
                    "x", String.valueOf((int) Math.floor(hologram.x())),
                    "y", String.valueOf((int) Math.floor(hologram.y())),
                    "z", String.valueOf((int) Math.floor(hologram.z())),
                    "lines", String.valueOf(hologram.lines().size()));
        }
    }

    /** Redraws it and writes it: the database is the only copy (see the module). */
    private void changed(String id) {
        module.redraw(Holograms.normalize(id));
        module.flushSoon();
    }

    private Optional<Hologram> existing(CommandSender sender, String id) {
        Optional<Hologram> hologram = holograms().get(id);
        if (hologram.isEmpty()) {
            module.getLang().send(sender, HologramMessages.UNKNOWN, "id", id);
        }
        return hologram;
    }

    private Optional<Integer> lineIndex(CommandSender sender, Hologram hologram, String input) {
        try {
            int number = Integer.parseInt(input);
            if (number >= 1 && number <= hologram.lines().size()) {
                return Optional.of(number - 1);
            }
        } catch (NumberFormatException ignored) {
            // Reported below.
        }
        module.getLang().send(sender, HologramMessages.NO_SUCH_LINE, "id", hologram.id(), "line", input);
        return Optional.empty();
    }

    private static String rest(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(HologramModule.ADMIN_PERMISSION)) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("list")) {
            holograms().list().forEach(hologram -> options.add(hologram.id()));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
