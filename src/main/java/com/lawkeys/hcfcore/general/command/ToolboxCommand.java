package com.lawkeys.hcfcore.general.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.general.GeneralMessages;
import com.lawkeys.hcfcore.general.GeneralModule;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.ItemText;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * {@code /heal}, {@code /kill}, {@code /gamemode}, {@code /rename}, {@code /more},
 * {@code /repair}, {@code /ping}, {@code /logout}.
 */
public final class ToolboxCommand implements TabExecutor {

    public static final String ADMIN_PERMISSION = "hcfcore.general.admin";
    public static final String RENAME_PERMISSION = "hcfcore.general.rename";
    public static final String MORE_PERMISSION = "hcfcore.general.more";
    public static final String REPAIR_PERMISSION = "hcfcore.general.repair";
    public static final String REPAIR_ALL_PERMISSION = "hcfcore.general.repair.all";

    private static final List<String> GAMEMODES = List.of("survival", "creative", "adventure", "spectator");

    private final GeneralModule module;

    public ToolboxCommand(GeneralModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.getSettings().enabled()) {
            module.getLang().send(sender, GeneralMessages.DISABLED);
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "heal" -> heal(sender, args);
            case "kill" -> kill(sender, args);
            case "gamemode" -> gamemode(sender, args, label);
            case "rename" -> rename(sender, args, label);
            case "more" -> more(sender);
            case "repair" -> repair(sender, args);
            case "ping" -> ping(sender, args);
            case "logout" -> logout(sender);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    private void heal(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return;
        }
        Player target = resolve(sender, args);
        if (target == null) {
            return;
        }
        // getValue() rather than a hardcoded 20: a server may have raised max health,
        // and healing somebody to less than full would be a strange kind of heal.
        var maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        target.setHealth(maxHealth == null ? target.getHealth() : maxHealth.getValue());
        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.setFireTicks(0);

        module.getLang().send(target, GeneralMessages.HEALED);
        if (!target.equals(sender)) {
            module.getLang().send(sender, GeneralMessages.HEALED_OTHER, "player", target.getName());
        }
    }

    private void kill(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return;
        }
        Player target = resolve(sender, args);
        if (target == null) {
            return;
        }
        // setHealth(0) rather than a damage call, so the death goes through
        // PlayerDeathEvent and every module that watches it - deathban, DTR, stats -
        // sees it exactly as it would see any other death.
        target.setHealth(0.0);
        if (!target.equals(sender)) {
            module.getLang().send(sender, GeneralMessages.KILLED_OTHER, "player", target.getName());
        }
    }

    private void gamemode(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            module.getLang().send(sender, GeneralMessages.USAGE,
                    "usage", "/" + label + " <survival|creative|adventure|spectator> [player]");
            return;
        }
        GameMode mode = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "survival", "s", "0" -> GameMode.SURVIVAL;
            case "creative", "c", "1" -> GameMode.CREATIVE;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            case "spectator", "sp", "3" -> GameMode.SPECTATOR;
            default -> null;
        };
        if (mode == null) {
            module.getLang().send(sender, GeneralMessages.GAMEMODE_UNKNOWN, "mode", args[0]);
            return;
        }
        Player target = resolve(sender, args.length > 1 ? new String[] {args[1]} : new String[0]);
        if (target == null) {
            return;
        }
        target.setGameMode(mode);
        module.getLang().send(target, GeneralMessages.GAMEMODE_SET, "mode", mode.name().toLowerCase(Locale.ROOT));
        if (!target.equals(sender)) {
            module.getLang().send(sender, GeneralMessages.GAMEMODE_SET_OTHER,
                    "player", target.getName(), "mode", mode.name().toLowerCase(Locale.ROOT));
        }
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    private void rename(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (!player.hasPermission(RENAME_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            module.getLang().send(player, GeneralMessages.RENAME_NOTHING_HELD);
            return;
        }
        if (args.length == 0) {
            module.getLang().send(player, GeneralMessages.USAGE, "usage", "/" + label + " <name>");
            return;
        }
        String name = String.join(" ", args);
        if (name.length() > module.getSettings().renameMaxLength()) {
            module.getLang().send(player, GeneralMessages.RENAME_TOO_LONG,
                    "max", String.valueOf(module.getSettings().renameMaxLength()));
            return;
        }
        held.editMeta(meta -> meta.customName(ItemText.line(LangManager.colorize(name))));
        module.getLang().send(player, GeneralMessages.RENAMED, "name", name);
    }

    private void more(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        if (!player.hasPermission(MORE_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            module.getLang().send(player, GeneralMessages.RENAME_NOTHING_HELD);
            return;
        }
        held.setAmount(held.getMaxStackSize());
        module.getLang().send(player, GeneralMessages.MORE_FILLED);
    }

    /**
     * Repairs the held item, or with {@code all} everything carried and worn.
     *
     * <p>Two permissions, because they are two different perks: fixing the sword in
     * your hand, and resetting a whole set of armour and tools in one go.
     */
    private void repair(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        boolean all = args.length > 0 && args[0].equalsIgnoreCase("all");
        if (!player.hasPermission(all ? REPAIR_ALL_PERMISSION : REPAIR_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return;
        }
        if (!all) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!repairItem(held)) {
                module.getLang().send(player, GeneralMessages.REPAIR_NOTHING);
                return;
            }
            player.getInventory().setItemInMainHand(held);
            module.getLang().send(player, GeneralMessages.REPAIRED);
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        int repaired = 0;
        for (ItemStack item : contents) {
            if (repairItem(item)) {
                repaired++;
            }
        }
        if (repaired == 0) {
            module.getLang().send(player, GeneralMessages.REPAIR_NOTHING);
            return;
        }
        player.getInventory().setContents(contents);
        module.getLang().send(player, GeneralMessages.REPAIRED_ALL, "count", String.valueOf(repaired));
    }

    /**
     * @return whether the item was worn and is now as new. {@code resetDamage} rather
     *         than {@code setDamage(0)}: the javadoc says the first removes the damage
     *         component and the second writes one holding zero, and only the first
     *         leaves the item exactly like an unused one - which matters to anything
     *         that compares items, such as a stack.
     */
    private static boolean repairItem(ItemStack item) {
        if (item == null || item.isEmpty()
                || !(item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable worn)
                || !worn.hasDamage()) {
            return false;
        }
        item.editMeta(org.bukkit.inventory.meta.Damageable.class,
                org.bukkit.inventory.meta.Damageable::resetDamage);
        return true;
    }

    // ------------------------------------------------------------------
    // Everybody
    // ------------------------------------------------------------------

    private void ping(CommandSender sender, String[] args) {
        if (args.length > 0) {
            // Not found, for somebody the sender cannot see: a ping is a way to ask
            // whether a vanished staff member is on, like /msg.
            Player target = VisiblePlayers.find(sender, args[0]).orElse(null);
            if (target == null) {
                module.getLang().send(sender, GeneralMessages.PLAYER_NOT_FOUND, "player", args[0]);
                return;
            }
            module.getLang().send(sender, GeneralMessages.PING_OTHER,
                    "player", target.getName(), "ping", String.valueOf(target.getPing()));
            return;
        }
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        module.getLang().send(player, GeneralMessages.PING_OWN, "ping", String.valueOf(player.getPing()));
    }

    /**
     * A safe logout: a countdown that any damage or movement cancels.
     *
     * <p>The point is not convenience. Closing the client mid-fight is how a player
     * escapes one, and the combat tag answers that by killing them - so a way to
     * leave that is visibly not an escape has to exist, or the only safe option is
     * to stand still for thirty seconds and hope.
     */
    private void logout(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        // Refused while tagged, before and after the countdown (see LogoutGuard): the
        // kick would be a combat log, and the anti-logout rule kills for those.
        if (module.refusesLogout(player)) {
            return;
        }
        long seconds = module.getSettings().logoutSeconds();
        if (seconds <= 0) {
            module.kickLoggingOut(player);
            return;
        }
        if (!module.beginLogoutWarmup(player, seconds)) {
            module.getLang().send(player, GeneralMessages.SPAWN_ALREADY);
            return;
        }
        module.getLang().send(player, GeneralMessages.LOGOUT_STARTED, "time", String.valueOf(seconds));
    }

    /** @return the target named, the sender themselves if none, or {@code null} after complaining */
    private Player resolve(CommandSender sender, String[] args) {
        if (args.length > 0) {
            Player named = Bukkit.getPlayerExact(args[0]);
            if (named == null) {
                module.getLang().send(sender, GeneralMessages.PLAYER_NOT_FOUND, "player", args[0]);
            }
            return named;
        }
        if (sender instanceof Player player) {
            return player;
        }
        module.getLang().send(sender, "general.player-only");
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("gamemode") && args.length == 1) {
            return prefixed(GAMEMODES, args[0]);
        }
        if ((name.equals("heal") || name.equals("kill") || name.equals("ping")) && args.length == 1) {
            // /ping is for everybody, so it must not list vanished staff.
            return VisiblePlayers.names(sender, args[0]);
        }
        if (name.equals("repair") && args.length == 1 && sender.hasPermission(REPAIR_ALL_PERMISSION)) {
            return prefixed(List.of("all"), args[0]);
        }
        return List.of();
    }

    private static List<String> prefixed(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matched.add(option);
            }
        }
        return matched;
    }
}
