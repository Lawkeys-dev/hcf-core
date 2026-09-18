package com.lawkeys.hcfcore.stats.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.stats.PlayerStats;
import com.lawkeys.hcfcore.stats.StatsMessages;
import com.lawkeys.hcfcore.stats.StatsModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** {@code /stats} - a player's kills, deaths, streak and playtime. */
public final class StatsCommand implements TabExecutor {

    private final StatsModule module;

    public StatsCommand(StatsModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        Optional<PlayerStats> found;
        String requested;
        if (args.length > 0) {
            requested = args[0];
            found = module.getManager().findByName(requested);
        } else if (sender instanceof Player player) {
            requested = player.getName();
            found = Optional.of(module.getManager().get(player.getUniqueId(), player.getName()));
        } else {
            module.getLang().send(sender, "general.player-only");
            return true;
        }
        if (found.isEmpty()) {
            module.getLang().send(sender, StatsMessages.UNKNOWN_PLAYER, "player", requested);
            return true;
        }

        PlayerStats stats = found.get();
        long now = System.currentTimeMillis();
        module.getLang().send(sender, StatsMessages.HEADER, "player", stats.getName());
        module.getLang().send(sender, StatsMessages.KILLS, "value", String.valueOf(stats.getKills()));
        module.getLang().send(sender, StatsMessages.DEATHS, "value", String.valueOf(stats.getDeaths()));
        module.getLang().send(sender, StatsMessages.RATIO,
                "value", String.format(Locale.ROOT, "%.2f", stats.killDeathRatio()));
        module.getLang().send(sender, StatsMessages.KILLSTREAK,
                "value", String.valueOf(stats.getKillstreak()),
                "best", String.valueOf(stats.getHighestKillstreak()));
        module.getLang().send(sender, StatsMessages.PLAYTIME,
                "value", Durations.format(stats.playtimeSeconds(now)));
        if (stats.getFirstSeen() > 0) {
            module.getLang().send(sender, StatsMessages.FIRST_SEEN,
                    "value", Durations.format((now - stats.getFirstSeen()) / 1000L));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        // Only names the sender can see: completion would otherwise list vanished staff.
        return VisiblePlayers.names(sender, args[0]);
    }
}
