package com.lawkeys.hcfcore.team.command;

import com.lawkeys.hcfcore.team.TeamMessages;
import com.lawkeys.hcfcore.team.TeamModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /lff [note]} - "looking for faction": a player with no team tells the
 * server they want one, with a few words about themselves if they like (the
 * project owner's request, 23/09/2026). Once every {@code lff.cooldown-seconds},
 * so it stays an announcement and not a spam channel.
 */
public final class LffCommand implements TabExecutor {

    /** {@code teams.yml}, {@code lff}. */
    public record Rules(boolean enabled, long cooldownSeconds, int maxNoteLength) {

        public static Rules defaults() {
            return new Rules(true, 300L, 64);
        }

        public static Rules load(ConfigurationSection section) {
            Rules d = defaults();
            if (section == null) {
                return d;
            }
            return new Rules(section.getBoolean("enabled", d.enabled()),
                    Math.max(0L, section.getLong("cooldown-seconds", d.cooldownSeconds())),
                    Math.max(0, Math.min(200, section.getInt("max-note-length", d.maxNoteLength()))));
        }
    }

    private final TeamModule module;
    private volatile Rules rules = Rules.defaults();
    private final Map<UUID, Long> lastUsed = new ConcurrentHashMap<>();

    public LffCommand(TeamModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    public void apply(Rules replacement) {
        this.rules = Objects.requireNonNull(replacement, "replacement");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Rules current = rules;
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, TeamMessages.LFF_PLAYERS_ONLY);
            return true;
        }
        if (!current.enabled()) {
            module.getLang().send(player, TeamMessages.LFF_DISABLED);
            return true;
        }
        if (module.getManager().getTeamOf(player.getUniqueId()).isPresent()) {
            module.getLang().send(player, TeamMessages.LFF_IN_TEAM);
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = lastUsed.get(player.getUniqueId());
        long wait = last == null ? 0 : current.cooldownSeconds() - (now - last) / 1000L;
        if (wait > 0) {
            module.getLang().send(player, TeamMessages.LFF_COOLDOWN, "time", Durations.format(wait));
            return true;
        }
        String note = String.join(" ", args).trim();
        if (note.length() > current.maxNoteLength()) {
            note = note.substring(0, current.maxNoteLength());
        }
        // The note is the player's words: shown as they typed them, never read as
        // colour codes or theme tokens.
        note = note.replace("&", "").replace("{", "").replace("}", "").replace("§", "");
        lastUsed.put(player.getUniqueId(), now);
        String message = note.isEmpty()
                ? module.getLang().get(TeamMessages.LFF_BROADCAST, "player", player.getName())
                : module.getLang().get(TeamMessages.LFF_BROADCAST_NOTE, "player", player.getName(), "note", note);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
