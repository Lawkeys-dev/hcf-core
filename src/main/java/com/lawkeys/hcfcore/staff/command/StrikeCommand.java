package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import com.lawkeys.hcfcore.staff.strike.Strike;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.util.ColorCodes;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.util.UUID;

/**
 * {@code /strike} - strikes against teams: {@code add <team|player> <reason>} and
 * {@code pardon <id>} for staff, {@code list [team|player]} for everybody.
 *
 * <p>A player's name strikes their team and records them as the reason - a cheater
 * banned typically. What the strike costs the team is {@code staff.yml}'s ladder,
 * applied by {@link StaffModule#strikeTeam}.
 */
public final class StrikeCommand implements TabExecutor {

    public static final String PERMISSION = "hcfcore.staff.strike";

    private static final List<String> STAFF_SUBCOMMANDS = List.of("add", "pardon");

    private final StaffModule module;

    public StrikeCommand(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /**
     * What a typed name means.
     *
     * @param team    the team as it is now, or {@code null} for one that no longer exists
     * @param teamId  its id, for the strike records
     * @param name    its name - today's, or the one its last strike recorded
     * @param subject the player named, or blank when a team was named
     */
    private record Target(Team team, UUID teamId, String name, String subject) {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.getSettings().enabled() || !module.getSettings().strikes().enabled()
                || module.getTeams() == null) {
            module.getLang().send(sender, StaffMessages.DISABLED);
            return true;
        }
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        if (STAFF_SUBCOMMANDS.contains(sub) && !sender.hasPermission(PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        switch (sub) {
            case "add" -> add(sender, args, label);
            case "list" -> list(sender, args, label);
            case "pardon" -> pardon(sender, args, label);
            default -> module.getLang().send(sender, StaffMessages.USAGE,
                    "usage", "/" + label + " <list [team|player]|add <team|player> <reason>|pardon <id>>");
        }
        return true;
    }

    private void add(CommandSender sender, String[] args, String label) {
        if (args.length < 3) {
            module.getLang().send(sender, StaffMessages.USAGE,
                    "usage", "/" + label + " add <team|player> <reason>");
            return;
        }
        Target target = resolve(sender, args[1]).orElse(null);
        if (target == null) {
            return;
        }
        if (target.team() == null) {
            // Only a record left: a disbanded team cannot be struck again.
            module.getLang().send(sender, StaffMessages.STRIKE_UNKNOWN_TEAM, "team", args[1]);
            return;
        }
        if (target.team().getType().isSystem()) {
            module.getLang().send(sender, StaffMessages.STRIKE_SYSTEM_TEAM, "team", target.name());
            return;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        StaffModule.StrikeOutcome outcome = module.strikeTeam(target.team(), target.subject(), reason,
                sender.getName());

        String team = target.name();
        String count = String.valueOf(outcome.active());
        String max = module.getStrikeLadder().disbandAt().isPresent()
                ? "/" + module.getStrikeLadder().disbandAt().getAsInt() : "";
        String shownReason = ColorCodes.escape(outcome.strike().reason());
        module.getLang().send(sender, StaffMessages.STRIKE_ISSUED,
                "team", team, "count", count, "id", String.valueOf(outcome.strike().id()));
        module.sendToStaffChannel(module.getLang().get(StaffMessages.STRIKE_ANNOUNCE,
                "team", team, "staff", sender.getName(), "count", count, "reason", shownReason,
                "subject", subject(target.subject())));
        broadcast(StaffMessages.STRIKE_BROADCAST, "team", team, "count", count, "max", max,
                "reason", shownReason);
        if (outcome.pointsLost() > 0) {
            broadcast(StaffMessages.STRIKE_POINTS_LOST, "team", team, "points", String.valueOf(outcome.pointsLost()));
        }
        if (outcome.disbanded()) {
            broadcast(StaffMessages.STRIKE_DISBANDED, "team", team, "count", count);
        }
        if (outcome.disbandRefused()) {
            module.getLang().send(sender, StaffMessages.STRIKE_DISBAND_REFUSED, "team", team);
        }
    }

    private void broadcast(String key, String... placeholders) {
        String message = module.getLang().get(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private void list(CommandSender sender, String[] args, String label) {
        Target target;
        if (args.length >= 2) {
            target = resolve(sender, args[1]).orElse(null);
        } else if (sender instanceof Player player) {
            Team own = module.getTeams().getManager().getTeamOf(player.getUniqueId()).orElse(null);
            if (own == null) {
                module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " list <team|player>");
                return;
            }
            target = new Target(own, own.getId(), own.getName(), "");
        } else {
            module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " list <team|player>");
            return;
        }
        if (target == null) {
            return;
        }
        List<Strike> history = module.getStrikes().history(target.teamId());
        if (history.isEmpty()) {
            module.getLang().send(sender, StaffMessages.STRIKE_NONE, "team", target.name());
            return;
        }
        long now = System.currentTimeMillis();
        module.getLang().send(sender, StaffMessages.STRIKE_LIST_HEADER,
                "team", target.name(),
                "active", String.valueOf(module.getStrikes().activeCount(target.teamId())),
                "total", String.valueOf(history.size()));
        for (Strike strike : history) {
            module.getLang().send(sender, StaffMessages.STRIKE_LIST_ENTRY,
                    "id", String.valueOf(strike.id()),
                    "staff", strike.issuedBy(),
                    "age", Durations.format(Math.max(0L, (now - strike.issuedAt()) / 1000L)),
                    "state", strike.isActiveAt(now) ? "active" : "expired",
                    "reason", ColorCodes.escape(strike.reason()),
                    "subject", subject(strike.subject()));
        }
    }

    private void pardon(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " pardon <id>");
            return;
        }
        String raw = args[1].startsWith("#") ? args[1].substring(1) : args[1];
        long id;
        try {
            id = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            module.getLang().send(sender, StaffMessages.INVALID_NUMBER, "input", args[1]);
            return;
        }
        if (!module.getStrikes().pardon(id)) {
            module.getLang().send(sender, StaffMessages.STRIKE_UNKNOWN, "id", String.valueOf(id));
            return;
        }
        module.flushSoon();
        module.getLang().send(sender, StaffMessages.STRIKE_PARDONED, "id", String.valueOf(id));
    }

    private String subject(String subject) {
        return subject == null || subject.isBlank() ? ""
                : module.getLang().get(StaffMessages.STRIKE_SUBJECT, "player", subject);
    }

    /**
     * Finds what a typed name means: a team by its name first, then a player's team -
     * without ever waiting on the network ({@code getOfflinePlayerIfCached}, never
     * {@code getOfflinePlayer(String)}, whose javadoc warns of a blocking web request)
     * - and last a team that no longer exists, by the name its strikes recorded.
     */
    private Optional<Target> resolve(CommandSender sender, String name) {
        TeamModule teams = module.getTeams();
        Optional<Team> byName = teams.getManager().getTeamByName(name);
        if (byName.isPresent()) {
            Team team = byName.get();
            return Optional.of(new Target(team, team.getId(), team.getName(), ""));
        }
        Player online = VisiblePlayers.find(sender, name).orElse(null);
        UUID playerId = null;
        String playerName = name;
        if (online != null) {
            playerId = online.getUniqueId();
            playerName = online.getName();
        } else {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            if (cached != null) {
                playerId = cached.getUniqueId();
                playerName = Objects.requireNonNullElse(cached.getName(), name);
            }
        }
        if (playerId != null) {
            Optional<Team> theirs = teams.getManager().getTeamOf(playerId);
            if (theirs.isEmpty()) {
                module.getLang().send(sender, StaffMessages.STRIKE_NO_TEAM, "player", playerName);
                return Optional.empty();
            }
            Team team = theirs.get();
            return Optional.of(new Target(team, team.getId(), team.getName(), playerName));
        }
        Optional<Target> recorded = module.getStrikes().latestUnderTeamName(name)
                .map(strike -> new Target(null, strike.teamId(), strike.teamName(), ""));
        if (recorded.isEmpty()) {
            module.getLang().send(sender, StaffMessages.STRIKE_UNKNOWN_TEAM, "team", name);
        }
        return recorded;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        boolean staff = sender.hasPermission(PERMISSION);
        if (args.length == 1) {
            options.add("list");
            if (staff) {
                options.addAll(STAFF_SUBCOMMANDS);
            }
        } else if (args.length == 2 && module.getTeams() != null
                && (args[0].equalsIgnoreCase("list") || (staff && args[0].equalsIgnoreCase("add")))) {
            module.getTeams().getManager().getTeams().stream()
                    .filter(team -> !team.getType().isSystem())
                    .forEach(team -> options.add(team.getName()));
            options.addAll(VisiblePlayers.names(sender, args[1]));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().toList();
    }
}
