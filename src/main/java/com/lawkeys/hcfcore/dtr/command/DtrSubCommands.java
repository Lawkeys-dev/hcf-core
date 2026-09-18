package com.lawkeys.hcfcore.dtr.command;

import com.lawkeys.hcfcore.dtr.DtrManager;
import com.lawkeys.hcfcore.dtr.DtrMessages;
import com.lawkeys.hcfcore.dtr.DtrModule;
import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.team.command.TeamSubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The DTR subcommands grafted onto {@code /team}: a status readout for players,
 * and the {@code setdtr}/{@code setregen} overrides FEATURES.md section 3 asks
 * for.
 *
 * <p>Registered through {@link TeamModule#registerSubCommand}, so the team module
 * stays unaware of DTR.
 */
public final class DtrSubCommands {

    /** Same node as the other team staff overrides, so one grant covers them all. */
    static final String ADMIN_PERMISSION = "hcfcore.team.admin";

    private DtrSubCommands() {
    }

    public static List<TeamSubCommand> all(DtrModule dtr) {
        Objects.requireNonNull(dtr, "dtr");
        return List.of(new Status(dtr), new SetDtr(dtr), new SetRegen(dtr));
    }

    private static List<String> teamNames(TeamModule module, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Team team : module.getManager().getTeams()) {
            if (team.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(team.getName());
            }
        }
        return names;
    }

    /** {@code /team dtr [team]} - current DTR, protection state and what happens next. */
    private static final class Status extends TeamSubCommand {

        private final DtrModule dtr;

        Status(DtrModule dtr) {
            super("dtr", Set.of(), null, "[team]", "Show a team's DTR", false, 0);
            this.dtr = dtr;
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            DtrManager manager = dtr.getManager();
            if (manager == null) {
                module.getLang().send(sender, DtrMessages.DISABLED);
                return;
            }

            Optional<Team> team;
            if (args.length > 0) {
                team = requireTeamByName(module, sender, args[0]);
            } else if (sender instanceof Player player) {
                team = requireTeam(module, sender, player);
            } else {
                module.getLang().send(sender, com.lawkeys.hcfcore.team.TeamMessages.COMMAND_USAGE,
                        "label", label, "usage", "dtr <team>");
                return;
            }
            if (team.isEmpty()) {
                return;
            }
            // A server team has no DTR: "0.00/0.00, protected, at full DTR" means nothing.
            if (team.get().getType().isSystem()) {
                module.getLang().send(sender, DtrMessages.SYSTEM_TEAM, "team", team.get().getName());
                return;
            }
            render(module, sender, manager, team.get());
        }

        private void render(TeamModule module, CommandSender sender, DtrManager manager, Team team) {
            var lang = module.getLang();
            lang.send(sender, DtrMessages.INFO_HEADER, "team", team.getName());
            lang.send(sender, DtrMessages.INFO_CURRENT,
                    "dtr", DtrManager.format(manager.getDtr(team)),
                    "max", DtrManager.format(manager.getMaximum(team)));

            if (manager.isRaidable(team.getId())) {
                lang.send(sender, DtrMessages.INFO_RAIDABLE);
                manager.getSecondsUntilProtected(team).ifPresent(seconds ->
                        lang.send(sender, DtrMessages.INFO_REGENERATING,
                                "time", formatDuration(seconds)));
                return;
            }
            // Its DTR would protect it, but EOTW or the Purge opens every claim.
            if (dtr.getRaidOverride().everyTeamRaidable()) {
                lang.send(sender, DtrMessages.INFO_RAIDABLE);
                lang.send(sender, DtrMessages.INFO_MAP_WIDE_RAID);
                return;
            }

            lang.send(sender, DtrMessages.INFO_PROTECTED);
            if (manager.isFrozen(team)) {
                lang.send(sender, DtrMessages.INFO_FROZEN,
                        "time", formatDuration(manager.getFreezeRemainingSeconds(team)));
            } else if (manager.getDtr(team) >= manager.getMaximum(team)) {
                lang.send(sender, DtrMessages.INFO_AT_MAXIMUM);
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? teamNames(module, args[0]) : List.of();
        }
    }

    /** {@code /team setdtr <team> <value>} - the staff override of FEATURES.md section 3. */
    private static final class SetDtr extends TeamSubCommand {

        private final DtrModule dtr;

        SetDtr(DtrModule dtr) {
            super("setdtr", Set.of(), ADMIN_PERMISSION, "<team> <value>",
                    "Override a team's DTR", false, 2);
            this.dtr = dtr;
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Optional<Team> team = requireTeamByName(module, sender, args[0]);
            if (team.isEmpty() || dtr.getManager() == null) {
                return;
            }
            try {
                TeamResult result = dtr.getManager().setDtr(team.get(), Double.parseDouble(args[1]));
                module.getLang().send(sender, result.getMessageKey(), result.getPlaceholders());
                dtr.pollAnnouncements();
            } catch (NumberFormatException e) {
                module.getLang().send(sender, DtrMessages.INVALID_NUMBER, "input", args[1]);
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? teamNames(module, args[0]) : List.of();
        }
    }

    /** {@code /team setregen <team> <seconds>} - the {@code setregen} of FEATURES.md section 3. */
    private static final class SetRegen extends TeamSubCommand {

        private final DtrModule dtr;

        SetRegen(DtrModule dtr) {
            super("setregen", Set.of(), ADMIN_PERMISSION, "<team> <seconds>",
                    "Set how long until a team's DTR starts regenerating", false, 2);
            this.dtr = dtr;
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Optional<Team> team = requireTeamByName(module, sender, args[0]);
            if (team.isEmpty() || dtr.getManager() == null) {
                return;
            }
            try {
                long seconds = Long.parseLong(args[1]);
                if (seconds > Durations.MAX_SECONDS) {
                    // Past a century, now + seconds * 1000 overflowed into the past.
                    throw new NumberFormatException(args[1]);
                }
                TeamResult result = dtr.getManager().setRegenSeconds(team.get(), seconds);
                module.getLang().send(sender, result.getMessageKey(), result.getPlaceholders());
                dtr.pollAnnouncements();
            } catch (NumberFormatException e) {
                module.getLang().send(sender, DtrMessages.INVALID_NUMBER, "input", args[1]);
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? teamNames(module, args[0]) : List.of();
        }
    }

    /** @return a compact {@code 1h 5m 30s} form, skipping the leading units that are zero */
    static String formatDuration(long totalSeconds) {
        return Durations.formatWithSeconds(totalSeconds);
    }
}
