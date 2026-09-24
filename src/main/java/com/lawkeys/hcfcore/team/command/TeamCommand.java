package com.lawkeys.hcfcore.team.command;

import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.team.TeamModule;
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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entry point for {@code /team} (aliases {@code /f}, {@code /faction}).
 *
 * <p>Dispatches to a {@link TeamSubCommand} after handling everything common to
 * all of them: unknown subcommands, the player-only check, permissions and
 * argument-count validation.
 */
public final class TeamCommand implements TabExecutor {

    /**
     * {@code /team help} lists the subcommands, as {@code /team} alone does. It was
     * missing until 13/09/2026 while the unknown-subcommand message, the usage in
     * plugin.yml and the default tip all sent players to it - so the one word they
     * were told to type answered "Unknown subcommand 'help'. Try /team help."
     */
    private static final String HELP = "help";

    private final TeamModule module;
    /**
     * Copy-on-write because other modules add to it after the command is already
     * registered: {@code claim/} contributes {@code /team claim}, {@code map},
     * {@code hq}... rather than the team module knowing about territory.
     */
    private final List<TeamSubCommand> subCommands = new CopyOnWriteArrayList<>();

    public TeamCommand(TeamModule module) {
        this.module = Objects.requireNonNull(module, "module");
        TeamSubCommands.all().forEach(this::register);
    }

    /**
     * Adds a subcommand contributed by another module. Later registrations appear
     * after the built-in ones in {@code /team help}. The word {@code help} itself is
     * taken by the help, and a subcommand using it is reported like any collision.
     *
     * <p>A name or alias already taken is reported in the console: the first
     * registered wins every lookup, so the later one is silently unreachable by that
     * word. {@code c} was once both {@code create} and {@code chat}, and
     * {@code /team c team} - the HCF habit for team chat - founded a team called
     * "team" (found in game, 13/09/2026).
     */
    public void register(TeamSubCommand subCommand) {
        Objects.requireNonNull(subCommand, "subCommand");
        List<String> words = new ArrayList<>(subCommand.getAliases());
        words.addFirst(subCommand.getName());
        for (String word : words) {
            if (word.equalsIgnoreCase(HELP)) {
                module.getPlugin().getLogger().warning("/team help lists the subcommands, so /team "
                        + subCommand.getName() + " cannot be reached by it.");
            }
            find(word).ifPresent(taken -> module.getPlugin().getLogger().warning("/team " + word
                    + " already means /team " + taken.getName() + ", so /team "
                    + subCommand.getName() + " cannot be reached by it."));
        }
        this.subCommands.add(subCommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Once, here, for every subcommand - including those claim/, dtr/ and
        // economy/ graft on: none may run on a cache that is still loading.
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase(HELP)) {
            sendHelp(sender, label);
            return true;
        }

        Optional<TeamSubCommand> found = find(args[0]);
        if (found.isEmpty()) {
            module.getLang().send(sender, TeamMessages.COMMAND_UNKNOWN_SUBCOMMAND,
                    "input", args[0], "label", label);
            return true;
        }

        TeamSubCommand sub = found.get();
        if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (sub.isPlayerOnly() && !(sender instanceof Player)) {
            module.getLang().send(sender, "general.player-only");
            return true;
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        if (rest.length < sub.getMinimumArgs()) {
            module.getLang().send(sender, TeamMessages.COMMAND_USAGE,
                    "label", label, "usage", sub.getName() + " " + sub.getUsage());
            return true;
        }

        sub.execute(module, sender, label, rest);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            if (HELP.startsWith(prefix)) {
                names.add(HELP);
            }
            for (TeamSubCommand sub : subCommands) {
                if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) {
                    continue;
                }
                if (sub.getName().startsWith(prefix)) {
                    names.add(sub.getName());
                }
            }
            return names;
        }

        Optional<TeamSubCommand> sub = find(args[0]);
        if (sub.isEmpty()
                || (sub.get().getPermission() != null && !sender.hasPermission(sub.get().getPermission()))) {
            return List.of();
        }
        return sub.get().tabComplete(module, sender, Arrays.copyOfRange(args, 1, args.length));
    }

    /**
     * A subcommand by its name or one of its own aliases, and failing that by a word
     * of {@code shortcuts.subcommands} ({@code /team i}): the plugin's own words
     * always win, so a shortcut can never hide a subcommand.
     */
    Optional<TeamSubCommand> find(String input) {
        for (TeamSubCommand sub : subCommands) {
            if (sub.matches(input)) {
                return Optional.of(sub);
            }
        }
        var shortcuts = module.getSettings().shortcuts();
        String target = shortcuts.enabled() ? shortcuts.subcommands().get(input.toLowerCase(Locale.ROOT)) : null;
        if (target != null) {
            for (TeamSubCommand sub : subCommands) {
                if (sub.matches(target)) {
                    return Optional.of(sub);
                }
            }
        }
        return Optional.empty();
    }

    /** @return whether this word is a subcommand's own name or alias, shortcuts aside */
    boolean isOwnWord(String word) {
        return word.equalsIgnoreCase(HELP) || subCommands.stream().anyMatch(sub -> sub.matches(word));
    }

    private void sendHelp(CommandSender sender, String label) {
        module.getLang().send(sender, TeamMessages.COMMAND_HELP_HEADER);
        for (TeamSubCommand sub : subCommands) {
            if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) {
                continue;
            }
            String usage = sub.getUsage().isEmpty() ? sub.getName() : sub.getName() + " " + sub.getUsage();
            module.getLang().send(sender, TeamMessages.COMMAND_HELP_ENTRY,
                    "label", label, "usage", usage, "description", sub.getDescription());
        }
    }
}
