package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
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
 * {@code /invsee} - look inside a player's inventory.
 *
 * <p><strong>Read-only unless the viewer holds {@link #EDIT_PERMISSION}</strong>,
 * by the project owner's decision of 12/09/2026. On a server where the items are
 * the whole economy, an accidental drag in somebody's inventory is unrecoverable
 * and looks exactly like staff theft, so the default has to be the safe one.
 *
 * <p><strong>The target's live inventory is opened, not a copy.</strong> A copy
 * would need writing back on close, and a write-back is precisely the duplication
 * vector worth avoiding: two staff viewing at once, or a player moving items while
 * viewed, and the copy overwrites reality. Opening the real inventory has neither
 * problem - what an editor changes is already the real thing.
 *
 * <p>The cost is that armour and the off-hand are not in the opened window, which
 * only shows the 36 storage slots. They are printed as a line of text instead, so
 * the question a check actually asks - what are they wearing - is still answered.
 */
public final class InvseeCommand implements TabExecutor {

    /** Needed on top of the staff node to change anything seen through this command. */
    public static final String EDIT_PERMISSION = "hcfcore.staff.invsee.edit";

    private final StaffModule module;

    public InvseeCommand(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            module.getLang().send(sender, "general.player-only");
            return true;
        }
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!module.getSettings().enabled() || !module.getSettings().invsee().enabled()) {
            module.getLang().send(sender, StaffMessages.DISABLED);
            return true;
        }
        if (args.length == 0) {
            module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " <player>");
            return true;
        }
        Player target = module.requireOnline(sender, args[0]);
        if (target == null) {
            return true;
        }
        if (target.equals(viewer)) {
            module.getLang().send(viewer, StaffMessages.INVSEE_SELF);
            return true;
        }

        boolean editable = viewer.hasPermission(EDIT_PERMISSION);
        if (viewer.openInventory(target.getInventory()) == null) {
            // Documented as nullable; say so rather than leave them looking at nothing.
            module.getLang().send(viewer, StaffMessages.DISABLED);
            return true;
        }
        module.getInvsee().open(viewer.getUniqueId(), target.getUniqueId(), editable);

        module.getLang().send(viewer, StaffMessages.INVSEE_OPENED, "player", target.getName());
        module.getLang().send(viewer,
                editable ? StaffMessages.INVSEE_EDITABLE : StaffMessages.INVSEE_READ_ONLY);
        module.getLang().send(viewer, StaffMessages.INVSEE_ARMOUR,
                "armour", describe(target.getInventory().getArmorContents()),
                "offhand", describe(target.getInventory().getItemInOffHand()));
        return true;
    }

    private static String describe(ItemStack[] items) {
        List<String> parts = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.isEmpty()) {
                parts.add(describe(item));
            }
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    /** @return a short description: the material, and how many enchantments it carries */
    private static String describe(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return "none";
        }
        String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        int enchantments = item.getEnchantments().size();
        return enchantments == 0 ? name : name + " (" + enchantments + " ench.)";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            return List.of();
        }
        return args.length == 1 ? VisiblePlayers.names(sender, args[0]) : List.of();
    }
}
