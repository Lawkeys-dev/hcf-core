package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.claim.listener.ClaimProtectionListener;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * {@code /staffbuild} - lets a staff member build through territory protection.
 *
 * <p>Deliberately <strong>not</strong> part of staff mode, by the project owner's
 * decision of 12/09/2026: watching a raid and editing the world are two different
 * intentions, and bundling them means every staff member who goes invisible is also
 * one misclick away from breaking somebody's wall.
 *
 * <p>The toggle is answered to {@code claim/} through its
 * {@link com.lawkeys.hcfcore.claim.BuildOverride} seam. Without the claim module
 * running there is no protection to lift, and the toggle simply does nothing.
 *
 * <p>It lifts protection only for a holder of {@code hcfcore.claim.bypass}: the
 * permission says the rank may, the toggle says they are choosing to. Staff without
 * the permission can still turn it on - the limiter's own bypass reads the same
 * toggle - and are told that territory protection still applies to them.
 */
public final class StaffBuildCommand implements TabExecutor {

    private final StaffModule module;

    public StaffBuildCommand(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return true;
        }
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!module.getSettings().enabled()) {
            module.getLang().send(sender, StaffMessages.DISABLED);
            return true;
        }
        boolean on = module.getManager().toggleStaffBuild(player.getUniqueId());
        if (!on) {
            module.getLang().send(player, StaffMessages.BUILD_OFF);
        } else if (player.hasPermission(ClaimProtectionListener.BYPASS_PERMISSION)) {
            module.getLang().send(player, StaffMessages.BUILD_ON);
        } else {
            module.getLang().send(player, StaffMessages.BUILD_ON_NO_BYPASS);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
