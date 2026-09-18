package com.lawkeys.hcfcore.team.command;

import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One {@code /team <sub>} subcommand.
 *
 * <p>Subclasses only parse arguments and render results; the dispatcher handles
 * permissions, the player-only check and usage errors, so none of that is
 * repeated across two dozen subcommands.
 */
public abstract class TeamSubCommand {

    private final String name;
    private final Set<String> aliases;
    private final String permission;
    private final String usage;
    private final String description;
    private final boolean playerOnly;
    private final int minimumArgs;

    /**
     * @param permission  permission node, or {@code null} when anyone may use it
     * @param usage       argument syntax shown on misuse, without the subcommand name
     * @param minimumArgs arguments required after the subcommand name
     */
    protected TeamSubCommand(String name, Set<String> aliases, String permission, String usage,
                             String description, boolean playerOnly, int minimumArgs) {
        this.name = Objects.requireNonNull(name, "name");
        this.aliases = Set.copyOf(aliases);
        this.permission = permission;
        this.usage = usage;
        this.description = description;
        this.playerOnly = playerOnly;
        this.minimumArgs = minimumArgs;
    }

    public String getName() {
        return name;
    }

    public Set<String> getAliases() {
        return aliases;
    }

    public String getPermission() {
        return permission;
    }

    public String getUsage() {
        return usage;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPlayerOnly() {
        return playerOnly;
    }

    public int getMinimumArgs() {
        return minimumArgs;
    }

    public boolean matches(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return name.equals(lower) || aliases.contains(lower);
    }

    /** @param args arguments after the subcommand name */
    public abstract void execute(TeamModule module, CommandSender sender, String label, String[] args);

    /** @return completions for the argument at {@code args.length - 1}; empty by default */
    public List<String> tabComplete(TeamModule module, CommandSender sender, String[] args) {
        return List.of();
    }

    // --- helpers for subclasses ------------------------------------------

    /** Sends the outcome of a manager call, whether it succeeded or not. */
    protected static void report(TeamModule module, CommandSender sender, TeamResult result) {
        module.getLang().send(sender, result.getMessageKey(), module.readable(result.getPlaceholders()));
    }

    /**
     * @return the sender's team, or empty after telling them they have none - so a
     *         subcommand can simply return when this is empty
     */
    protected static Optional<Team> requireTeam(TeamModule module, CommandSender sender, Player player) {
        Optional<Team> team = module.getManager().getTeamOf(player.getUniqueId());
        if (team.isEmpty()) {
            module.getLang().send(sender, TeamMessages.NOT_IN_TEAM);
        }
        return team;
    }

    /** @return the named team, or empty after reporting that it does not exist */
    protected static Optional<Team> requireTeamByName(TeamModule module, CommandSender sender, String name) {
        Optional<Team> team = module.getManager().getTeamByName(name);
        if (team.isEmpty()) {
            module.getLang().send(sender, TeamMessages.NOT_FOUND, "name", name);
        }
        return team;
    }
}
