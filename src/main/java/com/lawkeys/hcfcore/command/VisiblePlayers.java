package com.lawkeys.hcfcore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Online players as a command sender may see them.
 *
 * <p>A player command that looks somebody up by name goes through here, so a
 * vanished staff member is neither completed nor found: "player not found" is what
 * an offline player gets, and any other answer tells the sender that somebody
 * hidden is there. The console, and any sender that is not a player, sees everybody.
 *
 * <p>The rule was written down on 12/09/2026 and still missed by three commands -
 * {@code /team}, {@code /pay} and {@code /stats} (found in review, 14/09/2026). It
 * lives in one place now.
 */
public final class VisiblePlayers {

    private VisiblePlayers() {
    }

    /** @return whether the sender may see that player */
    public static boolean canSee(CommandSender sender, Player player) {
        return !(sender instanceof Player viewer) || viewer.canSee(player);
    }

    /** @return the online player of exactly that name, unless the sender cannot see them */
    public static Optional<Player> find(CommandSender sender, String name) {
        Player player = Bukkit.getPlayerExact(name);
        return player != null && canSee(sender, player) ? Optional.of(player) : Optional.empty();
    }

    /** @return the names of the online players the sender can see that start with the prefix, case aside */
    public static List<String> names(CommandSender sender, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canSee(sender, player) && player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(player.getName());
            }
        }
        return names;
    }
}
