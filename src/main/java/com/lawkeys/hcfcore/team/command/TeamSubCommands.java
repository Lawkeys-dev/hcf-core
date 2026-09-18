package com.lawkeys.hcfcore.team.command;

import com.lawkeys.hcfcore.command.KnownPlayers;
import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.team.ChatChannel;
import com.lawkeys.hcfcore.team.SystemZone;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamResult;
import com.lawkeys.hcfcore.team.TeamRole;
import com.lawkeys.hcfcore.util.WorldPosition;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Every {@code /team} subcommand.
 *
 * <p>Grouped in one file on purpose: each one is a handful of lines of argument
 * parsing followed by a single {@code TeamManager} call, and keeping them
 * together makes the whole command surface readable at a glance rather than
 * spread over two dozen near-empty files.
 *
 * <p>Permission node for staff overrides: {@code hcfcore.team.admin}. Those
 * subcommands pass a {@code null} actor to the manager, which is its documented
 * "bypass role checks" signal.
 */
final class TeamSubCommands {

    static final String ADMIN_PERMISSION = "hcfcore.team.admin";

    private TeamSubCommands() {
    }

    /** Declaration order is the order shown by {@code /team help}. */
    static List<TeamSubCommand> all() {
        return List.of(
                new Create(),
                new Disband(),
                new Rename(),
                new Invite(),
                new Uninvite(),
                new Join(),
                new Leave(),
                new Kick(),
                new Promote(),
                new Demote(),
                new Transfer(),
                new Info(),
                new ListTeams(),
                new Chat(),
                new Ally(),
                new Unally(),
                new Focus(),
                new Unfocus(),
                new Rally(),
                new Unrally(),
                new SetPoints(),
                new AddPoints(),
                new ForceDisband(),
                new ResetKoth(),
                new ForceJoin(),
                new ForceKick(),
                new ForcePromote(),
                new ForceDemote(),
                new CreateSystem(),
                new SetZone());
    }

    // ------------------------------------------------------------------
    // Shared argument helpers
    // ------------------------------------------------------------------

    /** Only the players the sender can see: vanished staff are not completed (see {@link VisiblePlayers}). */
    private static List<String> onlinePlayerNames(CommandSender sender, String prefix) {
        return VisiblePlayers.names(sender, prefix);
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

    private static List<String> memberNames(TeamModule module, CommandSender sender, String prefix) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        Optional<Team> team = module.getManager().getTeamOf(player.getUniqueId());
        if (team.isEmpty()) {
            return List.of();
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (UUID member : team.get().getMemberIds()) {
            String name = module.nameOf(member);
            if (name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * @return the online player, or empty after reporting that nobody by that name is
     *         online - which is also the answer for a player the sender cannot see
     */
    private static Optional<Player> onlinePlayer(TeamModule module, CommandSender sender, String name) {
        Optional<Player> target = VisiblePlayers.find(sender, name);
        if (target.isEmpty()) {
            module.getLang().send(sender, TeamMessages.PLAYER_NOT_FOUND, "player", name);
        }
        return target;
    }

    /**
     * @return the player by that name, online or not, or empty after reporting that
     *         nobody by that name is known. For staff overrides only - a player
     *         command must not reach past {@link VisiblePlayers}. Offline names go
     *         through the server's name cache, which never waits on the disk or the
     *         network.
     */
    private static Optional<UUID> anyPlayer(TeamModule module, CommandSender sender, String name) {
        Optional<UUID> id = KnownPlayers.idOf(name);
        if (id.isEmpty()) {
            module.getLang().send(sender, TeamMessages.PLAYER_NOT_FOUND, "player", name);
        }
        return id;
    }

    /** @return the parsed number, or empty after reporting that the input is not one */
    private static Optional<Long> number(TeamModule module, CommandSender sender, String input) {
        try {
            return Optional.of(Long.parseLong(input));
        } catch (NumberFormatException e) {
            module.getLang().send(sender, TeamMessages.COMMAND_INVALID_NUMBER, "input", input);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    private static final class Create extends TeamSubCommand {
        Create() {
            // Not "c": that is /team chat's, the HCF habit (/f c).
            super("create", Set.of(), null, "<name>", "Found a new team", true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            report(module, sender, module.getManager().createTeam(player.getUniqueId(), args[0]));
        }
    }

    private static final class Disband extends TeamSubCommand {
        Disband() {
            super("disband", Set.of(), null, "", "Disband your team", true, 0);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            if (team.isEmpty()) {
                return;
            }
            // Capture the audience first: once the disband goes through the team has
            // no members left to announce it to, and announcing beforehand would lie
            // if the manager refuses the operation.
            List<Player> members = module.getOnlineMembers(team.get());
            TeamResult result = module.getManager().disband(team.get(), player.getUniqueId());
            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcastTo(members, player.getUniqueId(), TeamMessages.DISBAND_BROADCAST,
                        "player", player.getName());
            }
        }
    }

    private static final class Rename extends TeamSubCommand {
        Rename() {
            super("rename", Set.of(), null, "<name>", "Rename your team", true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            if (team.isEmpty()) {
                return;
            }
            TeamResult result = module.getManager().rename(team.get(), player.getUniqueId(), args[0]);
            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcast(team.get(), player.getUniqueId(), TeamMessages.RENAME_BROADCAST,
                        "name", team.get().getName(), "player", player.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // Membership
    // ------------------------------------------------------------------

    private static final class Invite extends TeamSubCommand {
        Invite() {
            super("invite", Set.of("inv"), null, "<player>", "Invite a player to your team", true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            Optional<Player> target = onlinePlayer(module, sender, args[0]);
            if (team.isEmpty() || target.isEmpty()) {
                return;
            }
            TeamResult result = module.getManager()
                    .invite(team.get(), player.getUniqueId(), target.get().getUniqueId());
            report(module, sender, result);
            if (result.isSuccess()) {
                module.getLang().send(target.get(), TeamMessages.INVITE_RECEIVED,
                        "player", player.getName(), "team", team.get().getName());
                module.broadcast(team.get(), player.getUniqueId(), TeamMessages.INVITE_BROADCAST,
                        "player", player.getName(), "target", target.get().getName());
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? onlinePlayerNames(sender, args[0]) : List.of();
        }
    }

    private static final class Uninvite extends TeamSubCommand {
        Uninvite() {
            super("uninvite", Set.of("revoke"), null, "<player>", "Withdraw a pending invitation", true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            Optional<Player> target = onlinePlayer(module, sender, args[0]);
            if (team.isEmpty() || target.isEmpty()) {
                return;
            }
            report(module, sender, module.getManager()
                    .revokeInvite(team.get(), player.getUniqueId(), target.get().getUniqueId()));
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? onlinePlayerNames(sender, args[0]) : List.of();
        }
    }

    private static final class Join extends TeamSubCommand {
        Join() {
            super("join", Set.of(), null, "<team>", "Accept an invitation", true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeamByName(module, sender, args[0]);
            if (team.isEmpty()) {
                return;
            }
            TeamResult result = module.getManager().join(player.getUniqueId(), team.get(), false);
            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcast(team.get(), player.getUniqueId(), TeamMessages.JOIN_BROADCAST,
                        "player", player.getName());
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            if (args.length != 1 || !(sender instanceof Player player)) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            String lower = args[0].toLowerCase(Locale.ROOT);
            for (Team team : module.getManager().getPendingInvites(player.getUniqueId())) {
                if (team.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                    names.add(team.getName());
                }
            }
            return names;
        }
    }

    private static final class Leave extends TeamSubCommand {
        Leave() {
            super("leave", Set.of("quit"), null, "", "Leave your team", true, 0);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = module.getManager().getTeamOf(player.getUniqueId());
            List<Player> members = team.map(module::getOnlineMembers).orElse(List.of());
            TeamResult result = module.getManager().leave(player.getUniqueId());
            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcastTo(members, player.getUniqueId(), TeamMessages.LEAVE_BROADCAST,
                        "player", player.getName());
            }
        }
    }

    private static final class Kick extends TeamSubCommand {
        Kick() {
            super("kick", Set.of(), null, "<player>", "Remove a member from your team", true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            if (team.isEmpty()) {
                return;
            }
            Optional<UUID> target = module.findMemberByName(team.get(), args[0]);
            if (target.isEmpty()) {
                module.getLang().send(sender, TeamMessages.TARGET_NOT_IN_TEAM);
                return;
            }
            String targetName = module.nameOf(target.get());
            TeamResult result = module.getManager().kick(team.get(), player.getUniqueId(), target.get());
            report(module, sender, result);
            if (result.isSuccess()) {
                Player kicked = Bukkit.getPlayer(target.get());
                if (kicked != null) {
                    module.getLang().send(kicked, TeamMessages.KICK_KICKED,
                            "team", team.get().getName(), "player", player.getName());
                }
                module.broadcast(team.get(), player.getUniqueId(), TeamMessages.KICK_BROADCAST,
                        "target", targetName, "player", player.getName());
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? memberNames(module, sender, args[0]) : List.of();
        }
    }

    // ------------------------------------------------------------------
    // Roles
    // ------------------------------------------------------------------

    /** Shared shape of promote/demote/transfer: resolve a member, call, broadcast. */
    private abstract static class MemberTargeting extends TeamSubCommand {
        MemberTargeting(String name, Set<String> aliases, String usage, String description) {
            super(name, aliases, null, usage, description, true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            if (team.isEmpty()) {
                return;
            }
            Optional<UUID> target = module.findMemberByName(team.get(), args[0]);
            if (target.isEmpty()) {
                module.getLang().send(sender, TeamMessages.TARGET_NOT_IN_TEAM);
                return;
            }
            apply(module, sender, player, team.get(), target.get(), module.nameOf(target.get()));
        }

        abstract void apply(TeamModule module, CommandSender sender, Player actor, Team team,
                            UUID target, String targetName);

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? memberNames(module, sender, args[0]) : List.of();
        }
    }

    private static final class Promote extends MemberTargeting {
        Promote() {
            super("promote", Set.of(), "<player>", "Promote a member");
        }

        @Override
        void apply(TeamModule module, CommandSender sender, Player actor, Team team,
                   UUID target, String targetName) {
            TeamResult result = module.getManager().promote(team, actor.getUniqueId(), target);
            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcast(team, null, TeamMessages.PROMOTE_BROADCAST,
                        "target", targetName, "player", actor.getName(),
                        "role", team.getRole(target).map(module::roleName).orElse(""));
            }
        }
    }

    private static final class Demote extends MemberTargeting {
        Demote() {
            super("demote", Set.of(), "<player>", "Demote a member");
        }

        @Override
        void apply(TeamModule module, CommandSender sender, Player actor, Team team,
                   UUID target, String targetName) {
            TeamResult result = module.getManager().demote(team, actor.getUniqueId(), target);
            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcast(team, null, TeamMessages.DEMOTE_BROADCAST,
                        "target", targetName, "player", actor.getName(),
                        "role", team.getRole(target).map(module::roleName).orElse(""));
            }
        }
    }

    private static final class Transfer extends MemberTargeting {
        Transfer() {
            super("transfer", Set.of("setleader"), "<player>", "Hand over leadership");
        }

        @Override
        void apply(TeamModule module, CommandSender sender, Player actor, Team team,
                   UUID target, String targetName) {
            TeamResult result = module.getManager().transferLeadership(team, actor.getUniqueId(), target);
            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcast(team, null, TeamMessages.TRANSFER_BROADCAST, "target", targetName);
            }
        }
    }

    // ------------------------------------------------------------------
    // Information
    // ------------------------------------------------------------------

    private static final class Info extends TeamSubCommand {
        Info() {
            super("info", Set.of("who", "show"), null, "[team]", "Show a team's details", false, 0);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Optional<Team> team;
            if (args.length > 0) {
                team = requireTeamByName(module, sender, args[0]);
            } else if (sender instanceof Player player) {
                team = requireTeam(module, sender, player);
            } else {
                module.getLang().send(sender, TeamMessages.COMMAND_USAGE, "label", label, "usage", "info <team>");
                return;
            }
            if (team.isEmpty()) {
                return;
            }
            render(module, sender, team.get());
        }

        private void render(TeamModule module, CommandSender sender, Team team) {
            var lang = module.getLang();
            var manager = module.getManager();

            lang.send(sender, TeamMessages.INFO_HEADER, "team", team.getName());
            if (team.getType().isSystem()) {
                // Server land has no leader, members or bank: what matters is whether
                // one can be attacked there.
                lang.send(sender, team.isSafeZone() ? TeamMessages.INFO_ZONE_SAFE : TeamMessages.INFO_ZONE_COMBAT);
                return;
            }
            lang.send(sender, TeamMessages.INFO_LEADER,
                    "leader", team.getLeader().map(module::nameOf).orElse(lang.get(TeamMessages.INFO_NONE)));
            lang.send(sender, TeamMessages.INFO_CO_LEADERS,
                    "members", module.describeMembers(team.getMembersWithRole(TeamRole.CO_LEADER)));
            lang.send(sender, TeamMessages.INFO_MEMBERS,
                    "members", module.describeMembers(team.getMembersWithRole(TeamRole.MEMBER)));
            lang.send(sender, TeamMessages.INFO_ONLINE,
                    "online", String.valueOf(module.getOnlineMembers(team).size()),
                    "total", String.valueOf(team.getMemberCount()));
            lang.send(sender, TeamMessages.INFO_BALANCE,
                    "balance", module.getManager().formatAmount(team.getBalance()));
            lang.send(sender, TeamMessages.INFO_POINTS, "points", String.valueOf(team.getPoints()));
            lang.send(sender, TeamMessages.INFO_KOTH_CAPTURES,
                    "captures", String.valueOf(team.getKothCaptures()));

            List<String> allyNames = new ArrayList<>();
            for (UUID allyId : team.getAllies()) {
                manager.getTeam(allyId).ifPresent(ally -> allyNames.add(ally.getName()));
            }
            allyNames.sort(String.CASE_INSENSITIVE_ORDER);
            lang.send(sender, TeamMessages.INFO_ALLIES,
                    "allies", allyNames.isEmpty() ? lang.get(TeamMessages.INFO_NONE) : String.join(", ", allyNames));

            manager.getRally(team).ifPresent(rally -> lang.send(sender, TeamMessages.INFO_RALLY,
                    "world", rally.world(),
                    "x", String.valueOf(Math.round(rally.x())),
                    "y", String.valueOf(Math.round(rally.y())),
                    "z", String.valueOf(Math.round(rally.z()))));
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? teamNames(module, args[0]) : List.of();
        }
    }

    private static final class ListTeams extends TeamSubCommand {
        private static final int DEFAULT_LIMIT = 10;

        ListTeams() {
            super("list", Set.of("top"), null, "[limit]", "Show the team ranking", false, 0);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            int limit = DEFAULT_LIMIT;
            if (args.length > 0) {
                Optional<Long> parsed = number(module, sender, args[0]);
                if (parsed.isEmpty()) {
                    return;
                }
                limit = (int) Math.max(1, Math.min(parsed.get(), 100));
            }

            List<Team> teams = module.getManager().getTopTeamsByPoints(limit);
            if (teams.isEmpty()) {
                module.getLang().send(sender, TeamMessages.LIST_EMPTY);
                return;
            }
            module.getLang().send(sender, TeamMessages.LIST_HEADER);
            for (int i = 0; i < teams.size(); i++) {
                Team team = teams.get(i);
                module.getLang().send(sender, TeamMessages.LIST_ENTRY,
                        "rank", String.valueOf(i + 1),
                        "team", team.getName(),
                        "points", String.valueOf(team.getPoints()),
                        "members", String.valueOf(team.getMemberCount()));
            }
        }
    }

    private static final class Chat extends TeamSubCommand {
        Chat() {
            super("chat", Set.of("c"), null, "[public|team|ally]", "Switch chat channel", true, 0);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            TeamManager manager = module.getManager();
            if (requireTeam(module, sender, player).isEmpty()) {
                return;
            }

            ChatChannel target;
            if (args.length > 0) {
                Optional<ChatChannel> parsed = ChatChannel.fromId(args[0]);
                if (parsed.isEmpty()) {
                    module.getLang().send(sender, TeamMessages.COMMAND_USAGE,
                            "label", label, "usage", "chat " + getUsage());
                    return;
                }
                target = parsed.get();
            } else {
                // No argument cycles public -> team -> ally -> public, the usual shorthand.
                target = switch (manager.getChatChannel(player.getUniqueId())) {
                    case PUBLIC -> ChatChannel.TEAM;
                    case TEAM -> ChatChannel.ALLY;
                    case ALLY -> ChatChannel.PUBLIC;
                };
            }

            manager.setChatChannel(player.getUniqueId(), target);
            module.getLang().send(sender, TeamMessages.CHAT_SWITCHED, "channel", target.name());
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            if (args.length != 1) {
                return List.of();
            }
            String lower = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            for (ChatChannel channel : ChatChannel.values()) {
                String name = channel.name().toLowerCase(Locale.ROOT);
                if (name.startsWith(lower)) {
                    options.add(name);
                }
            }
            return options;
        }
    }

    // ------------------------------------------------------------------
    // Diplomacy
    // ------------------------------------------------------------------

    private static final class Ally extends TeamSubCommand {
        Ally() {
            super("ally", Set.of(), null, "<team>", "Offer or accept an alliance", true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            Optional<Team> other = requireTeamByName(module, sender, args[0]);
            if (team.isEmpty() || other.isEmpty()) {
                return;
            }
            TeamResult result = module.getManager().ally(team.get(), player.getUniqueId(), other.get());
            report(module, sender, result);
            if (!result.isSuccess()) {
                return;
            }
            if (TeamMessages.ALLY_NOW_ALLIED.equals(result.getMessageKey())) {
                module.broadcast(team.get(), player.getUniqueId(), TeamMessages.ALLY_BROADCAST,
                        "team", other.get().getName());
                module.broadcast(other.get(), null, TeamMessages.ALLY_BROADCAST,
                        "team", team.get().getName());
            } else {
                module.broadcast(other.get(), null, TeamMessages.ALLY_RECEIVED,
                        "team", team.get().getName());
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? teamNames(module, args[0]) : List.of();
        }
    }

    private static final class Unally extends TeamSubCommand {
        Unally() {
            super("unally", Set.of(), null, "<team>", "Break an alliance", true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            Optional<Team> other = requireTeamByName(module, sender, args[0]);
            if (team.isEmpty() || other.isEmpty()) {
                return;
            }
            TeamResult result = module.getManager().unally(team.get(), player.getUniqueId(), other.get());
            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcast(team.get(), player.getUniqueId(), TeamMessages.UNALLY_BROADCAST,
                        "team", other.get().getName());
                module.broadcast(other.get(), null, TeamMessages.UNALLY_BROADCAST,
                        "team", team.get().getName());
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? teamNames(module, args[0]) : List.of();
        }
    }

    private static final class Focus extends TeamSubCommand {
        Focus() {
            super("focus", Set.of(), null, "<player|team>", "Mark a target for your team", true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            if (team.isEmpty()) {
                return;
            }

            // A team name wins over a player name: focusing a whole team is the
            // more common intent, and a player can still be targeted by their own name.
            Optional<Team> targetTeam = module.getManager().getTeamByName(args[0]);
            TeamResult result;
            String targetName;
            if (targetTeam.isPresent()) {
                targetName = targetTeam.get().getName();
                result = module.getManager().focusTeam(team.get(), player.getUniqueId(), targetTeam.get());
            } else {
                Optional<Player> targetPlayer = onlinePlayer(module, sender, args[0]);
                if (targetPlayer.isEmpty()) {
                    return;
                }
                targetName = targetPlayer.get().getName();
                result = module.getManager().focusPlayer(team.get(), player.getUniqueId(),
                        targetPlayer.get().getUniqueId(), targetName);
            }

            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcast(team.get(), player.getUniqueId(), TeamMessages.FOCUS_BROADCAST,
                        "target", targetName);
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            if (args.length != 1) {
                return List.of();
            }
            List<String> options = new ArrayList<>(teamNames(module, args[0]));
            options.addAll(onlinePlayerNames(sender, args[0]));
            return options;
        }
    }

    private static final class Unfocus extends TeamSubCommand {
        Unfocus() {
            super("unfocus", Set.of(), null, "<player|team>", "Clear a focus marker", true, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            if (team.isEmpty()) {
                return;
            }
            Optional<Team> targetTeam = module.getManager().getTeamByName(args[0]);
            UUID targetId;
            String targetName;
            if (targetTeam.isPresent()) {
                targetId = targetTeam.get().getId();
                targetName = targetTeam.get().getName();
            } else {
                Optional<Player> targetPlayer = onlinePlayer(module, sender, args[0]);
                if (targetPlayer.isEmpty()) {
                    return;
                }
                targetId = targetPlayer.get().getUniqueId();
                targetName = targetPlayer.get().getName();
            }
            report(module, sender,
                    module.getManager().unfocus(team.get(), player.getUniqueId(), targetId, targetName));
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            if (args.length != 1) {
                return List.of();
            }
            List<String> options = new ArrayList<>(teamNames(module, args[0]));
            options.addAll(onlinePlayerNames(sender, args[0]));
            return options;
        }
    }

    // ------------------------------------------------------------------
    // Rally
    // ------------------------------------------------------------------

    private static final class Rally extends TeamSubCommand {
        Rally() {
            super("rally", Set.of(), null, "", "Set a rally point at your position", true, 0);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            if (team.isEmpty()) {
                return;
            }
            WorldPosition position = TeamModule.toPosition(player.getLocation());
            TeamResult result = module.getManager().setRally(team.get(), player.getUniqueId(), position);
            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcast(team.get(), player.getUniqueId(), TeamMessages.RALLY_BROADCAST,
                        "player", player.getName());
                module.updateCompasses(team.get());
            }
        }
    }

    private static final class Unrally extends TeamSubCommand {
        Unrally() {
            super("unrally", Set.of(), null, "", "Clear your team's rally point", true, 0);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Player player = (Player) sender;
            Optional<Team> team = requireTeam(module, sender, player);
            if (team.isEmpty()) {
                return;
            }
            report(module, sender, module.getManager().clearRally(team.get(), player.getUniqueId()));
        }
    }

    // ------------------------------------------------------------------
    // Staff overrides (a null actor tells the manager to skip role checks)
    // ------------------------------------------------------------------

    private static final class SetPoints extends TeamSubCommand {
        SetPoints() {
            super("setpoints", Set.of(), ADMIN_PERMISSION, "<team> <points>",
                    "Override a team's points", false, 2);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Optional<Team> team = requireTeamByName(module, sender, args[0]);
            Optional<Long> points = number(module, sender, args[1]);
            if (team.isEmpty() || points.isEmpty()) {
                return;
            }
            report(module, sender, module.getManager().setPoints(team.get(), points.get()));
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? teamNames(module, args[0]) : List.of();
        }
    }

    private static final class AddPoints extends TeamSubCommand {
        AddPoints() {
            super("addpoints", Set.of(), ADMIN_PERMISSION, "<team> <points>",
                    "Add or remove team points", false, 2);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Optional<Team> team = requireTeamByName(module, sender, args[0]);
            Optional<Long> points = number(module, sender, args[1]);
            if (team.isEmpty() || points.isEmpty()) {
                return;
            }
            report(module, sender, module.getManager().addPoints(team.get(), points.get()));
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? teamNames(module, args[0]) : List.of();
        }
    }

    /** Starts a new ranking period: every team's counted KOTH captures back to zero. */
    private static final class ResetKoth extends TeamSubCommand {
        ResetKoth() {
            super("resetkoth", Set.of(), ADMIN_PERMISSION, "", "Reset every team's counted KOTH captures", false, 0);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            int reset = module.getManager().resetKothCaptures();
            module.getLang().send(sender, TeamMessages.KOTH_RESET, "count", String.valueOf(reset));
        }
    }

    private static final class ForceDisband extends TeamSubCommand {
        ForceDisband() {
            super("forcedisband", Set.of(), ADMIN_PERMISSION, "<team>", "Disband any team", false, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Optional<Team> team = requireTeamByName(module, sender, args[0]);
            if (team.isEmpty()) {
                return;
            }
            List<Player> members = module.getOnlineMembers(team.get());
            TeamResult result = module.getManager().disband(team.get(), null);
            report(module, sender, result);
            if (result.isSuccess()) {
                module.broadcastTo(members, null, TeamMessages.DISBAND_BROADCAST,
                        "player", sender.getName());
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? teamNames(module, args[0]) : List.of();
        }
    }

    private static final class ForceJoin extends TeamSubCommand {
        ForceJoin() {
            super("forcejoin", Set.of(), ADMIN_PERMISSION, "<player> <team>",
                    "Put a player in a team", false, 2);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Optional<Player> target = onlinePlayer(module, sender, args[0]);
            Optional<Team> team = requireTeamByName(module, sender, args[1]);
            if (target.isEmpty() || team.isEmpty()) {
                return;
            }
            TeamResult result = module.getManager().join(target.get().getUniqueId(), team.get(), true);
            // The manager speaks to whoever joins - "You joined Mysql.", "You are
            // already in a team." - and here that is not the staff member who typed
            // it (found in game, 13/09/2026). Staff get their own lines; the player
            // gets theirs.
            if (!result.isSuccess()) {
                if (TeamMessages.JOIN_ALREADY_IN_TEAM.equals(result.getMessageKey())) {
                    module.getLang().send(sender, TeamMessages.JOIN_FORCED_ALREADY_IN_TEAM,
                            "player", target.get().getName());
                } else {
                    report(module, sender, result);
                }
                return;
            }
            module.getLang().send(sender, TeamMessages.JOIN_FORCED,
                    "player", target.get().getName(), "team", team.get().getName());
            if (!target.get().equals(sender)) {
                report(module, target.get(), result);
            }
            module.broadcast(team.get(), null, TeamMessages.JOIN_BROADCAST,
                    "player", target.get().getName());
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return switch (args.length) {
                case 1 -> onlinePlayerNames(sender, args[0]);
                case 2 -> teamNames(module, args[1]);
                default -> List.of();
            };
        }
    }

    /**
     * Shared shape of the force-kick/promote/demote family: find the player's team, then act on them.
     *
     * <p>Online or not: the player staff most need to remove from a team - a cheater
     * banned in the middle of a raid - is by then offline, and these used to answer
     * that nobody by that name was online (found in the command review, 15/09/2026).
     */
    private abstract static class ForcedMemberAction extends TeamSubCommand {
        ForcedMemberAction(String name, String description) {
            super(name, Set.of(), ADMIN_PERMISSION, "<player>", description, false, 1);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Optional<UUID> target = anyPlayer(module, sender, args[0]);
            if (target.isEmpty()) {
                return;
            }
            UUID targetId = target.get();
            Optional<Team> team = module.getManager().getTeamOf(targetId);
            if (team.isEmpty()) {
                module.getLang().send(sender, TeamMessages.TARGET_NOT_IN_TEAM);
                return;
            }
            Optional<UUID> leaderBefore = team.get().getLeader();
            TeamResult result = apply(module, team.get(), targetId);
            report(module, sender, result);
            // A kicked leader is succeeded by the highest-ranked member (TeamManager#kick):
            // the team, and the staff member who did it, are told who leads now.
            Optional<UUID> leaderAfter = team.get().getLeader();
            if (result.isSuccess() && leaderAfter.isPresent() && !leaderAfter.equals(leaderBefore)) {
                String leader = module.nameOf(leaderAfter.get());
                module.broadcast(team.get(), null, TeamMessages.TRANSFER_BROADCAST, "target", leader);
                module.getLang().send(sender, TeamMessages.TRANSFER_FORCED, "player", leader, "team", team.get().getName());
            }
        }

        abstract TeamResult apply(TeamModule module, Team team, UUID target);

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 1 ? onlinePlayerNames(sender, args[0]) : List.of();
        }
    }

    private static final class ForceKick extends ForcedMemberAction {
        ForceKick() {
            super("forcekick", "Remove a player from their team");
        }

        @Override
        TeamResult apply(TeamModule module, Team team, UUID target) {
            return module.getManager().kick(team, null, target);
        }
    }

    private static final class ForcePromote extends ForcedMemberAction {
        ForcePromote() {
            super("forcepromote", "Promote a player in their team");
        }

        @Override
        TeamResult apply(TeamModule module, Team team, UUID target) {
            return module.getManager().promote(team, null, target);
        }
    }

    private static final class ForceDemote extends ForcedMemberAction {
        ForceDemote() {
            super("forcedemote", "Demote a player in their team");
        }

        @Override
        TeamResult apply(TeamModule module, Team team, UUID target) {
            return module.getManager().demote(team, null, target);
        }
    }

    // ------------------------------------------------------------------
    // Server teams
    // ------------------------------------------------------------------

    /** {@code /team createsystem <name> <safe|combat>} - spawn, warzone, roads, event grounds. */
    private static final class CreateSystem extends TeamSubCommand {
        CreateSystem() {
            super("createsystem", Set.of(), ADMIN_PERMISSION, "<name> <safe|combat>",
                    "Create a server team: a safe zone or a combat zone", false, 2);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Optional<SystemZone> zone = parseZone(module, sender, args[1]);
            if (zone.isPresent()) {
                report(module, sender, module.getManager().createSystemTeam(args[0], zone.get()));
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return args.length == 2 ? zoneIds(args[1]) : List.of();
        }
    }

    /** {@code /team setzone <team> <safe|combat>} - switch a server team between the two. */
    private static final class SetZone extends TeamSubCommand {
        SetZone() {
            super("setzone", Set.of(), ADMIN_PERMISSION, "<team> <safe|combat>",
                    "Make a server team a safe zone or a combat zone", false, 2);
        }

        @Override
        public void execute(TeamModule module, CommandSender sender, String label, String[] args) {
            Optional<Team> team = requireTeamByName(module, sender, args[0]);
            if (team.isEmpty()) {
                return;
            }
            Optional<SystemZone> zone = parseZone(module, sender, args[1]);
            if (zone.isPresent()) {
                report(module, sender, module.getManager().setSystemZone(team.get(), zone.get()));
            }
        }

        @Override
        public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
            return switch (args.length) {
                case 1 -> teamNames(module, args[0]);
                case 2 -> zoneIds(args[1]);
                default -> List.of();
            };
        }
    }

    /** @return the zone named by {@code input}, or empty after saying it names none */
    private static Optional<SystemZone> parseZone(TeamModule module, CommandSender sender, String input) {
        Optional<SystemZone> zone = SystemZone.fromId(input);
        if (zone.isEmpty()) {
            module.getLang().send(sender, TeamMessages.SYSTEM_INVALID_ZONE, "input", input);
        }
        return zone;
    }

    private static List<String> zoneIds(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>();
        for (SystemZone zone : SystemZone.values()) {
            if (zone.id().startsWith(lower)) {
                ids.add(zone.id());
            }
        }
        return ids;
    }
}
