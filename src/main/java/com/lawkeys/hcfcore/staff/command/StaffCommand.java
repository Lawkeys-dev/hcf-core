package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * {@code /staff} - the staff mode toggle, and the moderation verbs hung off it.
 *
 * <p><strong>Subcommands rather than top-level {@code /tp}, {@code /near} and the
 * rest.</strong> Those names are taken: {@code /tp} is a vanilla command, and
 * {@code /near} is one of the most commonly claimed names on a Bukkit server.
 * Claiming them would either lose to whatever registered first or silently take
 * them from a plugin the operator meant to keep. Under {@code /staff} they belong
 * to this plugin unambiguously, and the toolbar binds them by that name.
 */
public final class StaffCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "list", "tp", "tphere", "tpall", "tploc", "tpnearest", "randomtp",
            "near", "telllocation", "end", "nether");

    /** Default radius for {@code /staff near} when none is given. */
    private static final int DEFAULT_NEAR_RADIUS = 50;

    private final StaffModule module;
    private final Random random = new Random();

    public StaffCommand(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!module.getSettings().enabled()) {
            module.getLang().send(sender, StaffMessages.DISABLED);
            return true;
        }
        // Held inventories are loaded data: entering staff mode before they land
        // could stash somebody's items over a row that is about to be read back.
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }

        if (args.length == 0) {
            toggleMode(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "tp" -> teleportTo(sender, args, label);
            case "tphere" -> teleportHere(sender, args, label);
            case "tpall" -> teleportAll(sender);
            case "tploc" -> teleportToLocation(sender, args, label);
            case "tpnearest" -> teleportToNearest(sender);
            case "randomtp" -> teleportToRandom(sender);
            case "near" -> near(sender, args);
            case "telllocation" -> tellLocation(sender, args, label);
            case "end" -> dimension(sender, World.Environment.THE_END, "The End");
            case "nether" -> dimension(sender, World.Environment.NETHER, "The Nether");
            default -> module.getLang().send(sender, "general.unknown-command", "label", label);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The mode itself
    // ------------------------------------------------------------------

    private void toggleMode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (module.getManager().isInStaffMode(player.getUniqueId())) {
            module.leaveStaffMode(player, true);
        } else {
            module.enterStaffMode(player);
        }
    }

    private void list(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (var playerId : module.getManager().staffModePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                names.add(player.getName());
            }
        }
        if (names.isEmpty()) {
            module.getLang().send(sender, StaffMessages.MODE_LIST_EMPTY);
            return;
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        module.getLang().send(sender, StaffMessages.MODE_LIST_HEADER, "count", String.valueOf(names.size()));
        for (String name : names) {
            module.getLang().send(sender, StaffMessages.MODE_LIST_ENTRY, "player", name);
        }
    }

    // ------------------------------------------------------------------
    // Teleports
    // ------------------------------------------------------------------

    private void teleportTo(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " tp <player>");
            return;
        }
        Player target = module.requireOnline(sender, args[1]);
        if (target != null) {
            teleport(player, target.getLocation(), StaffMessages.TELEPORTED_TO, target.getName());
        }
    }

    private void teleportHere(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (args.length < 2) {
            module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " tphere <player>");
            return;
        }
        Player target = module.requireOnline(sender, args[1]);
        if (target != null) {
            target.teleportAsync(player.getLocation());
            module.getLang().send(player, StaffMessages.TELEPORTED_HERE, "player", target.getName());
        }
    }

    private void teleportAll(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        Location here = player.getLocation();
        int moved = 0;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.teleportAsync(here);
                moved++;
            }
        }
        module.getLang().send(player, StaffMessages.TELEPORTED_ALL, "count", String.valueOf(moved));
    }

    private void teleportToLocation(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (args.length < 4) {
            module.getLang().send(sender, StaffMessages.USAGE,
                    "usage", "/" + label + " tploc <x> <y> <z> [world]");
            return;
        }
        double x;
        double y;
        double z;
        try {
            x = Double.parseDouble(args[1]);
            y = Double.parseDouble(args[2]);
            z = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            module.getLang().send(sender, StaffMessages.INVALID_NUMBER, "input", args[1] + " " + args[2] + " " + args[3]);
            return;
        }
        // NaN and Infinity parse as doubles; teleportAsync then throws on them
        // (Location#checkFinite), which read as "An internal error occurred".
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            module.getLang().send(sender, StaffMessages.INVALID_NUMBER, "input", args[1] + " " + args[2] + " " + args[3]);
            return;
        }
        World world = args.length > 4 ? Bukkit.getWorld(args[4]) : player.getWorld();
        if (world == null) {
            module.getLang().send(sender, StaffMessages.DIMENSION_MISSING, "world", args[4]);
            return;
        }
        Location destination = new Location(world, x, y, z, player.getYaw(), player.getPitch());
        player.teleportAsync(destination);
        module.getLang().send(player, StaffMessages.TELEPORTED_LOCATION,
                "x", format(x), "y", format(y), "z", format(z), "world", world.getName());
    }

    private void teleportToNearest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || !other.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = other.getLocation().distanceSquared(player.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = other;
            }
        }
        if (nearest == null) {
            module.getLang().send(player, StaffMessages.NOBODY_ELSE_ONLINE);
            return;
        }
        teleport(player, nearest.getLocation(), StaffMessages.TELEPORTED_TO, nearest.getName());
    }

    private void teleportToRandom(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        List<Player> others = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                others.add(other);
            }
        }
        if (others.isEmpty()) {
            module.getLang().send(player, StaffMessages.NOBODY_ELSE_ONLINE);
            return;
        }
        Player target = others.get(random.nextInt(others.size()));
        teleport(player, target.getLocation(), StaffMessages.TELEPORTED_TO, target.getName());
    }

    private void teleport(Player player, Location destination, String message, String targetName) {
        player.teleportAsync(destination);
        module.getLang().send(player, message, "player", targetName);
    }

    // ------------------------------------------------------------------
    // Where is everybody
    // ------------------------------------------------------------------

    private void near(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        int radius = DEFAULT_NEAR_RADIUS;
        if (args.length > 1) {
            try {
                radius = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                module.getLang().send(sender, StaffMessages.INVALID_NUMBER, "input", args[1]);
                return;
            }
        }
        double radiusSquared = (double) radius * radius;
        List<Player> found = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || !other.getWorld().equals(player.getWorld())) {
                continue;
            }
            if (other.getLocation().distanceSquared(player.getLocation()) <= radiusSquared) {
                found.add(other);
            }
        }
        if (found.isEmpty()) {
            module.getLang().send(player, StaffMessages.NEAR_EMPTY, "radius", String.valueOf(radius));
            return;
        }
        found.sort(Comparator.comparingDouble(o -> o.getLocation().distanceSquared(player.getLocation())));
        module.getLang().send(player, StaffMessages.NEAR_HEADER,
                "count", String.valueOf(found.size()), "radius", String.valueOf(radius));
        for (Player other : found) {
            module.getLang().send(player, StaffMessages.NEAR_ENTRY,
                    "player", other.getName(),
                    "distance", String.valueOf((int) Math.round(
                            other.getLocation().distance(player.getLocation()))));
        }
    }

    private void tellLocation(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            module.getLang().send(sender, StaffMessages.USAGE,
                    "usage", "/" + label + " telllocation <player>");
            return;
        }
        Player target = module.requireOnline(sender, args[1]);
        if (target == null) {
            return;
        }
        Location at = target.getLocation();
        module.getLang().send(sender, StaffMessages.LOCATION_TOLD,
                "player", target.getName(),
                "x", format(at.getX()), "y", format(at.getY()), "z", format(at.getZ()),
                "world", at.getWorld().getName());
    }

    /**
     * Lists who is in a dimension.
     *
     * <p>By {@link World.Environment} rather than by world name, so a server with
     * several nether worlds - or one whose worlds are not named the usual way - is
     * answered correctly rather than by guessing at "world_nether".
     */
    private void dimension(CommandSender sender, World.Environment environment, String label) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == environment) {
                names.add(player.getName() + " (" + player.getWorld().getName() + ")");
            }
        }
        if (names.isEmpty()) {
            module.getLang().send(sender, StaffMessages.DIMENSION_EMPTY, "dimension", label);
            return;
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        module.getLang().send(sender, StaffMessages.DIMENSION_HEADER,
                "dimension", label, "count", String.valueOf(names.size()));
        for (String name : names) {
            module.getLang().send(sender, StaffMessages.DIMENSION_ENTRY, "player", name);
        }
    }

    private static String format(double value) {
        return String.valueOf(Math.round(value));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    options.add(sub);
                }
            }
            return options;
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "tp", "tphere", "telllocation" -> VisiblePlayers.names(sender, args[1]);
                default -> List.of();
            };
        }
        return List.of();
    }
}
