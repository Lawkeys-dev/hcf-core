package com.lawkeys.hcfcore.general.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.general.BasicCommands;
import com.lawkeys.hcfcore.general.GeneralMessages;
import com.lawkeys.hcfcore.general.GeneralModule;
import com.lawkeys.hcfcore.general.MoveSpeeds;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The everyday commands an essentials plugin gives, for a server that runs HCFCore
 * alone: {@code /clearinventory}, {@code /feed}, {@code /fly}, {@code /god},
 * {@code /flyspeed}, {@code /walkspeed}, {@code /hat}, {@code /suicide},
 * {@code /extinguish}, {@code /workbench}, {@code /anvil}, {@code /enderchest},
 * {@code /i}, {@code /tphere}, {@code /tppos}, {@code /gmc} {@code /gms}
 * {@code /gma} {@code /gmsp}, {@code /day} {@code /night} {@code /sun} {@code /rain}.
 *
 * <p>Each has its own permission, {@code hcfcore.general.<command>}, and those that
 * can target somebody else a second one, {@code .others}. Left out on purpose:
 * {@code /tpa}, {@code /home}, {@code /back} - a way out of a fight or a raid - and
 * {@code /near}, which tells where the enemy is.
 */
public final class BasicsCommand implements TabExecutor {

    public static final String PERMISSION_PREFIX = "hcfcore.general.";
    private static final int MAX_ITEM_AMOUNT = 64 * 36;

    private final GeneralModule module;

    public BasicsCommand(GeneralModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /** Every command this class answers, as named in plugin.yml. */
    public static List<String> commands() {
        return BasicCommands.NAMES;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.getSettings().enabled()) {
            module.getLang().send(sender, GeneralMessages.DISABLED);
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "clearinventory" -> onTarget(sender, name, args, target -> {
                target.getInventory().clear();
                return GeneralMessages.CLEARED;
            });
            case "feed" -> onTarget(sender, name, args, target -> {
                target.setFoodLevel(20);
                target.setSaturation(20f);
                target.setExhaustion(0f);
                return GeneralMessages.FED;
            });
            case "fly" -> onTarget(sender, name, args, target -> {
                boolean flying = !target.getAllowFlight();
                target.setAllowFlight(flying);
                if (!flying) {
                    target.setFlying(false);
                }
                return flying ? GeneralMessages.FLY_ENABLED : GeneralMessages.FLY_DISABLED;
            });
            case "god" -> onTarget(sender, name, args, target ->
                    module.toggleGod(target.getUniqueId()) ? GeneralMessages.GOD_ENABLED : GeneralMessages.GOD_DISABLED);
            case "extinguish" -> onTarget(sender, name, args, target -> {
                target.setFireTicks(0);
                return GeneralMessages.EXTINGUISHED;
            });
            case "flyspeed", "walkspeed" -> speed(sender, name, label, args);
            case "hat" -> hat(sender);
            case "suicide" -> selfOnly(sender, name).ifPresent(player -> player.setHealth(0.0));
            case "workbench" -> selfOnly(sender, name).ifPresent(player ->
                    player.openInventory(MenuType.CRAFTING.builder().checkReachable(false).build(player)));
            case "anvil" -> selfOnly(sender, name).ifPresent(player ->
                    player.openInventory(MenuType.ANVIL.builder().checkReachable(false).build(player)));
            case "enderchest" -> enderChest(sender, args);
            case "i" -> item(sender, label, args);
            case "tphere" -> tpHere(sender, label, args);
            case "tppos" -> tpPos(sender, label, args);
            case "gmc" -> gameMode(sender, name, args, GameMode.CREATIVE);
            case "gms" -> gameMode(sender, name, args, GameMode.SURVIVAL);
            case "gma" -> gameMode(sender, name, args, GameMode.ADVENTURE);
            case "gmsp" -> gameMode(sender, name, args, GameMode.SPECTATOR);
            case "day", "night" -> time(sender, name, args);
            case "sun", "rain" -> weather(sender, name, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** What a command does to its target; returns the message key telling them. */
    @FunctionalInterface
    private interface TargetAction {
        String apply(Player target);
    }

    /**
     * A command done to oneself, or to another player with {@code .others}. The target
     * is told; the sender too when it is somebody else.
     */
    private void onTarget(CommandSender sender, String name, String[] args, TargetAction action) {
        Optional<Player> target = target(sender, name, args, 0);
        if (target.isEmpty()) {
            return;
        }
        String key = action.apply(target.get());
        module.getLang().send(target.get(), key);
        if (!target.get().equals(sender)) {
            module.getLang().send(sender, GeneralMessages.DONE_FOR_OTHER, "player", target.get().getName());
        }
    }

    /**
     * The player a command acts on: the one named at {@code index} - which needs
     * {@code .others} - or the sender. Tells the sender why when there is none.
     */
    private Optional<Player> target(CommandSender sender, String name, String[] args, int index) {
        String permission = PERMISSION_PREFIX + name;
        if (!sender.hasPermission(permission)) {
            module.getLang().send(sender, "general.no-permission");
            return Optional.empty();
        }
        if (args.length > index) {
            if (!sender.hasPermission(permission + ".others")) {
                module.getLang().send(sender, "general.no-permission");
                return Optional.empty();
            }
            Optional<Player> named = VisiblePlayers.find(sender, args[index]);
            if (named.isEmpty()) {
                module.getLang().send(sender, GeneralMessages.PLAYER_NOT_FOUND, "player", args[index]);
            }
            return named;
        }
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        module.getLang().send(sender, "general.player-only");
        return Optional.empty();
    }

    /** A command a player uses on themself only. */
    private Optional<Player> selfOnly(CommandSender sender, String name) {
        if (!sender.hasPermission(PERMISSION_PREFIX + name)) {
            module.getLang().send(sender, "general.no-permission");
            return Optional.empty();
        }
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        module.getLang().send(sender, "general.player-only");
        return Optional.empty();
    }

    // ------------------------------------------------------------------

    private void speed(CommandSender sender, String name, String label, String[] args) {
        if (args.length == 0) {
            module.getLang().send(sender, GeneralMessages.USAGE, "usage", "/" + label + " <0-10> [player]");
            return;
        }
        float value;
        try {
            value = Float.parseFloat(args[0]);
        } catch (NumberFormatException notANumber) {
            value = Float.NaN;
        }
        if (!(value >= 0f && value <= MoveSpeeds.MAX_USER_SPEED)) {
            module.getLang().send(sender, GeneralMessages.SPEED_INVALID, "value", args[0]);
            return;
        }
        // The speed permission covers both: /flyspeed and /walkspeed are one tool.
        Optional<Player> target = target(sender, "speed", args, 1);
        if (target.isEmpty()) {
            return;
        }
        boolean fly = name.equals("flyspeed");
        float game = MoveSpeeds.toGame(value, fly ? MoveSpeeds.DEFAULT_FLY : MoveSpeeds.DEFAULT_WALK);
        if (fly) {
            target.get().setFlySpeed(game);
        } else {
            target.get().setWalkSpeed(game);
        }
        String shown = value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
        module.getLang().send(target.get(), fly ? GeneralMessages.SPEED_FLY : GeneralMessages.SPEED_WALK,
                "speed", shown);
        if (!target.get().equals(sender)) {
            module.getLang().send(sender, GeneralMessages.DONE_FOR_OTHER, "player", target.get().getName());
        }
    }

    /** The held item goes on the head; what was there comes into the hand. */
    private void hat(CommandSender sender) {
        Optional<Player> player = selfOnly(sender, "hat");
        if (player.isEmpty()) {
            return;
        }
        PlayerInventory inventory = player.get().getInventory();
        ItemStack held = inventory.getItemInMainHand();
        if (held.isEmpty()) {
            module.getLang().send(player.get(), GeneralMessages.HAT_NOTHING);
            return;
        }
        ItemStack helmet = inventory.getHelmet();
        inventory.setHelmet(held);
        inventory.setItemInMainHand(helmet);
        module.getLang().send(player.get(), GeneralMessages.HAT_ON);
    }

    /** One's own ender chest, or another player's - live, as {@code /invsee} is. */
    private void enderChest(CommandSender sender, String[] args) {
        Optional<Player> viewer = selfOnly(sender, "enderchest");
        if (viewer.isEmpty()) {
            return;
        }
        Player owner = viewer.get();
        if (args.length > 0) {
            if (!sender.hasPermission(PERMISSION_PREFIX + "enderchest.others")) {
                module.getLang().send(sender, "general.no-permission");
                return;
            }
            Optional<Player> named = VisiblePlayers.find(sender, args[0]);
            if (named.isEmpty()) {
                module.getLang().send(sender, GeneralMessages.PLAYER_NOT_FOUND, "player", args[0]);
                return;
            }
            owner = named.get();
        }
        viewer.get().openInventory(owner.getEnderChest());
    }

    private void item(CommandSender sender, String label, String[] args) {
        Optional<Player> player = selfOnly(sender, "item");
        if (player.isEmpty()) {
            return;
        }
        if (args.length == 0) {
            module.getLang().send(sender, GeneralMessages.USAGE, "usage", "/" + label + " <item> [amount]");
            return;
        }
        Material material = Material.matchMaterial(args[0]);
        if (material == null || !material.isItem() || material.isAir()) {
            module.getLang().send(sender, GeneralMessages.ITEM_UNKNOWN, "item", args[0]);
            return;
        }
        int amount = material.getMaxStackSize();
        if (args.length > 1) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException notANumber) {
                amount = 0;
            }
            if (amount < 1 || amount > MAX_ITEM_AMOUNT) {
                module.getLang().send(sender, GeneralMessages.ITEM_AMOUNT, "max", String.valueOf(MAX_ITEM_AMOUNT));
                return;
            }
        }
        Player target = player.get();
        target.getInventory().addItem(ItemStack.of(material, amount)).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        module.getLang().send(target, GeneralMessages.ITEM_GIVEN, "amount", String.valueOf(amount),
                "item", material.getKey().getKey());
    }

    private void tpHere(CommandSender sender, String label, String[] args) {
        Optional<Player> player = selfOnly(sender, "tphere");
        if (player.isEmpty()) {
            return;
        }
        if (args.length == 0) {
            module.getLang().send(sender, GeneralMessages.USAGE, "usage", "/" + label + " <player>");
            return;
        }
        Optional<Player> named = VisiblePlayers.find(sender, args[0]);
        if (named.isEmpty()) {
            module.getLang().send(sender, GeneralMessages.PLAYER_NOT_FOUND, "player", args[0]);
            return;
        }
        named.get().teleport(player.get().getLocation(), PlayerTeleportEvent.TeleportCause.COMMAND);
        module.getLang().send(sender, GeneralMessages.DONE_FOR_OTHER, "player", named.get().getName());
    }

    private void tpPos(CommandSender sender, String label, String[] args) {
        Optional<Player> player = selfOnly(sender, "tppos");
        if (player.isEmpty()) {
            return;
        }
        if (args.length < 3) {
            module.getLang().send(sender, GeneralMessages.USAGE, "usage", "/" + label + " <x> <y> <z> [world]");
            return;
        }
        World world = args.length > 3 ? Bukkit.getWorld(args[3]) : player.get().getWorld();
        if (world == null) {
            module.getLang().send(sender, GeneralMessages.WORLD_UNKNOWN, "world", args[3]);
            return;
        }
        double[] xyz = new double[3];
        for (int i = 0; i < 3; i++) {
            try {
                xyz[i] = Double.parseDouble(args[i]);
            } catch (NumberFormatException notANumber) {
                module.getLang().send(sender, GeneralMessages.TPPOS_INVALID, "value", args[i]);
                return;
            }
            if (!Double.isFinite(xyz[i]) || Math.abs(xyz[i]) > 30_000_000) {
                module.getLang().send(sender, GeneralMessages.TPPOS_INVALID, "value", args[i]);
                return;
            }
        }
        Location current = player.get().getLocation();
        player.get().teleport(new Location(world, xyz[0], xyz[1], xyz[2], current.getYaw(), current.getPitch()),
                PlayerTeleportEvent.TeleportCause.COMMAND);
    }

    /** {@code /gmc} and the others: {@code /gamemode}'s permission, and {@code .others} to set somebody else's. */
    private void gameMode(CommandSender sender, String name, String[] args, GameMode mode) {
        if (!sender.hasPermission(ToolboxCommand.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return;
        }
        Player target;
        if (args.length > 0) {
            Optional<Player> named = VisiblePlayers.find(sender, args[0]);
            if (named.isEmpty()) {
                module.getLang().send(sender, GeneralMessages.PLAYER_NOT_FOUND, "player", args[0]);
                return;
            }
            target = named.get();
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        target.setGameMode(mode);
        String modeName = mode.name().toLowerCase(Locale.ROOT);
        module.getLang().send(target, GeneralMessages.GAMEMODE_SET, "mode", modeName);
        if (!target.equals(sender)) {
            module.getLang().send(sender, GeneralMessages.GAMEMODE_SET_OTHER, "player", target.getName(),
                    "mode", modeName);
        }
    }

    private void time(CommandSender sender, String name, String[] args) {
        Optional<World> world = world(sender, "time", name, args);
        if (world.isEmpty()) {
            return;
        }
        // 1000 is when the game's /time set day lands, 13000 its night.
        world.get().setTime(name.equals("day") ? 1000L : 13000L);
        module.getLang().send(sender, name.equals("day") ? GeneralMessages.TIME_DAY : GeneralMessages.TIME_NIGHT,
                "world", world.get().getName());
    }

    private void weather(CommandSender sender, String name, String[] args) {
        Optional<World> world = world(sender, "weather", name, args);
        if (world.isEmpty()) {
            return;
        }
        boolean rain = name.equals("rain");
        world.get().setStorm(rain);
        world.get().setThundering(false);
        module.getLang().send(sender, rain ? GeneralMessages.WEATHER_RAIN : GeneralMessages.WEATHER_SUN,
                "world", world.get().getName());
    }

    /** The world named, or the sender's own. */
    private Optional<World> world(CommandSender sender, String permission, String name, String[] args) {
        if (!sender.hasPermission(PERMISSION_PREFIX + permission)) {
            module.getLang().send(sender, "general.no-permission");
            return Optional.empty();
        }
        if (args.length > 0) {
            World named = Bukkit.getWorld(args[0]);
            if (named == null) {
                module.getLang().send(sender, GeneralMessages.WORLD_UNKNOWN, "world", args[0]);
            }
            return Optional.ofNullable(named);
        }
        if (sender instanceof Player player) {
            return Optional.of(player.getWorld());
        }
        module.getLang().send(sender, GeneralMessages.USAGE, "usage", "/" + name + " <world>");
        return Optional.empty();
    }

    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "clearinventory", "feed", "fly", "god", "extinguish", "enderchest", "tphere",
                    "gmc", "gms", "gma", "gmsp" -> {
                return args.length == 1 ? VisiblePlayers.names(sender, args[0]) : List.of();
            }
            case "flyspeed", "walkspeed" -> {
                if (args.length == 1) {
                    return prefixed(List.of("1", "2", "3", "5", "10"), args[0]);
                }
                return args.length == 2 ? VisiblePlayers.names(sender, args[1]) : List.of();
            }
            case "i" -> {
                if (args.length != 1 || args[0].length() < 2) {
                    return List.of();
                }
                String prefix = args[0].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (Material material : Material.values()) {
                    if (material.isItem() && !material.isAir() && !material.name().startsWith("LEGACY_")) {
                        String key = material.getKey().getKey();
                        if (key.startsWith(prefix)) {
                            out.add(key);
                            if (out.size() >= 50) {
                                break;
                            }
                        }
                    }
                }
                return out;
            }
            case "tppos" -> {
                return args.length == 4 ? worlds(args[3]) : List.of();
            }
            case "day", "night", "sun", "rain" -> {
                return args.length == 1 ? worlds(args[0]) : List.of();
            }
            default -> {
                return List.of();
            }
        }
    }

    private static List<String> worlds(String prefix) {
        return prefixed(Bukkit.getWorlds().stream().map(World::getName).toList(), prefix);
    }

    private static List<String> prefixed(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
