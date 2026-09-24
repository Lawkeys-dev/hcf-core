package com.lawkeys.hcfcore.team.command;

import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.team.TeamSettings;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * {@code shortcuts.commands} of {@code teams.yml}: commands of their own for team
 * subcommands - {@code /hq} for {@code /team hq}. Each runs {@code /team} exactly as
 * typed in full, permissions, checks and messages included.
 *
 * <p>Made at runtime in the server's command map, since their names are the
 * operator's: a reload takes the old ones out and puts the new ones in, and tells
 * online players' clients. A name some other command already has - another
 * plugin's, the server's, one of ours - is left to it and reported: a shortcut
 * never takes a command away.
 */
public final class ShortcutCommands {

    private static final String PREFIX = "hcfcore";

    private final TeamModule module;
    private final TeamCommand teamCommand;
    private final Command team;
    private final List<Command> registered = new ArrayList<>();

    public ShortcutCommands(TeamModule module, TeamCommand teamCommand, Command team) {
        this.module = Objects.requireNonNull(module, "module");
        this.teamCommand = Objects.requireNonNull(teamCommand, "teamCommand");
        this.team = Objects.requireNonNull(team, "team");
    }

    /** Replaces the shortcut commands with these; main thread. */
    public void apply(TeamSettings.Shortcuts shortcuts) {
        CommandMap map = Bukkit.getCommandMap();
        boolean changed = !registered.isEmpty();
        for (Command command : registered) {
            command.unregister(map);
            map.getKnownCommands().values().removeIf(known -> known == command);
        }
        registered.clear();

        if (shortcuts.enabled()) {
            for (Map.Entry<String, String> word : shortcuts.subcommands().entrySet()) {
                if (teamCommand.isOwnWord(word.getKey())) {
                    module.getPlugin().getLogger().warning("teams.yml: shortcuts.subcommands." + word.getKey()
                            + " is already /team " + word.getKey() + "; that one is kept.");
                } else if (teamCommand.find(word.getValue()).isEmpty()) {
                    module.getPlugin().getLogger().warning("teams.yml: shortcuts.subcommands." + word.getKey()
                            + " names /team " + word.getValue() + ", which does not exist.");
                }
            }
        }

        for (Map.Entry<String, String> entry : shortcuts.enabled() ? shortcuts.commands().entrySet()
                : Map.<String, String>of().entrySet()) {
            String name = entry.getKey();
            String[] target = entry.getValue().trim().split("\\s+");
            if (teamCommand.find(target[0]).isEmpty()) {
                module.getPlugin().getLogger().warning("teams.yml: shortcuts.commands." + name + " names /team "
                        + target[0] + ", which does not exist; /" + name + " is not made.");
                continue;
            }
            if (map.getCommand(name) != null) {
                module.getPlugin().getLogger().warning("teams.yml: /" + name + " is already a command; the shortcut to /team "
                        + entry.getValue() + " is not made.");
                continue;
            }
            Command command = new Shortcut(name, target);
            map.register(PREFIX, command);
            registered.add(command);
            changed = true;
        }
        if (changed) {
            // The clients' command trees, for completion and the red "unknown" colour.
            Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        }
    }

    /** One shortcut: {@code /name args} is {@code /team target... args}. */
    private final class Shortcut extends Command {

        private final String[] target;

        private Shortcut(String name, String[] target) {
            super(name, "/team " + String.join(" ", target), "/" + name, List.of());
            this.target = target;
        }

        private String[] full(String[] args) {
            String[] full = Arrays.copyOf(target, target.length + args.length);
            System.arraycopy(args, 0, full, target.length, args.length);
            return full;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!module.getPlugin().isEnabled()) {
                return true;
            }
            return teamCommand.onCommand(sender, team, team.getName(), full(args));
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return teamCommand.onTabComplete(sender, team, team.getName(), full(args));
        }
    }

    /** For a reload's report: the shortcut commands made, lower-case. */
    public List<String> names() {
        return registered.stream().map(command -> command.getName().toLowerCase(Locale.ROOT)).toList();
    }
}
