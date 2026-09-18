package com.lawkeys.hcfcore.general.command;

import com.lawkeys.hcfcore.general.GeneralMessages;
import com.lawkeys.hcfcore.general.GeneralModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * {@code /spawn}, {@code /world} and {@code /top} (FEATURES.md section 10).
 *
 * <p>One class for the three because they share the question that matters: none of
 * them may be a way out of a fight. {@code /spawn} takes a warmup that damage or
 * movement cancels; the other two are refused outright while a player is combat
 * tagged, through the same {@code TeleportGuard} the claim module already asks.
 */
public final class TeleportCommand implements TabExecutor {

    public static final String WORLD_PERMISSION = "hcfcore.general.world";
    public static final String TOP_PERMISSION = "hcfcore.general.top";

    private final GeneralModule module;

    public TeleportCommand(GeneralModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return true;
        }
        if (!module.getSettings().enabled()) {
            module.getLang().send(sender, GeneralMessages.DISABLED);
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawn(player);
            case "world" -> world(player, args, label);
            case "top" -> top(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void spawn(Player player) {
        if (!module.getSettings().spawn().enabled()) {
            module.getLang().send(player, GeneralMessages.DISABLED);
            return;
        }
        if (module.isBlockedByCombat(player) || module.refusesSpawn(player)) {
            return;
        }
        long warmup = module.getSettings().spawn().warmupSeconds();
        if (warmup <= 0) {
            module.sendToSpawn(player);
            return;
        }
        if (!module.beginSpawnWarmup(player, warmup)) {
            module.getLang().send(player, GeneralMessages.SPAWN_ALREADY);
            return;
        }
        module.getLang().send(player, GeneralMessages.SPAWN_WARMUP, "time", String.valueOf(warmup));
    }

    private void world(Player player, String[] args, String label) {
        if (!player.hasPermission(WORLD_PERMISSION)) {
            module.getLang().send(player, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            module.getLang().send(player, GeneralMessages.WORLD_LIST,
                    "worlds", String.join(", ", worldNames()));
            return;
        }
        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            module.getLang().send(player, GeneralMessages.WORLD_UNKNOWN, "world", args[0]);
            return;
        }
        if (module.isBlockedByCombat(player)) {
            return;
        }
        player.teleportAsync(world.getSpawnLocation());
        module.getLang().send(player, GeneralMessages.WORLD_MOVED, "world", world.getName());
    }

    /**
     * Puts the player on the highest block above where they stand.
     *
     * <p>{@code getHighestBlockYAt} answers with the world's floor when the column is
     * empty, so a player standing in the void is told there is nothing above rather
     * than being dropped back where they were and left wondering.
     */
    private void top(Player player) {
        if (!player.hasPermission(TOP_PERMISSION)) {
            module.getLang().send(player, "general.no-permission");
            return;
        }
        if (module.isBlockedByCombat(player)) {
            return;
        }
        Location from = player.getLocation();
        int highest = from.getWorld().getHighestBlockYAt(from.getBlockX(), from.getBlockZ());
        if (highest <= from.getWorld().getMinHeight() || highest <= from.getBlockY()) {
            module.getLang().send(player, GeneralMessages.TOP_NOTHING);
            return;
        }
        Location destination = new Location(from.getWorld(), from.getX(), highest + 1.0, from.getZ(),
                from.getYaw(), from.getPitch());
        player.teleportAsync(destination);
        module.getLang().send(player, GeneralMessages.TOP_MOVED, "y", String.valueOf(highest + 1));
    }

    private static List<String> worldNames() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        return names;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("world") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (String name : worldNames()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(name);
            }
        }
        return names;
    }
}
