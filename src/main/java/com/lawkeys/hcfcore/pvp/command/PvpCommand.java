package com.lawkeys.hcfcore.pvp.command;

import com.lawkeys.hcfcore.command.KnownPlayers;
import com.lawkeys.hcfcore.pvp.Deathban;
import com.lawkeys.hcfcore.pvp.PvpMessages;
import com.lawkeys.hcfcore.pvp.PvpModule;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /pvp} - combat tag and deathban status, plus the staff overrides.
 *
 * <p>Its own command rather than a {@code /team} subcommand: combat state belongs
 * to a player, not to their team, and a player without a team still needs it.
 */
public final class PvpCommand implements TabExecutor {

    /** Staff node for lifting and setting deathbans on other players. */
    public static final String ADMIN_PERMISSION = "hcfcore.pvp.admin";

    private static final List<String> ADMIN_SUBCOMMANDS = List.of("check", "lift", "ban");

    private final PvpModule module;

    public PvpCommand(PvpModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Deathbans are loaded data: /pvp lift|ban before they land would be undone.
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        if (module.getDeathbans() == null) {
            module.getLang().send(sender, PvpMessages.DISABLED);
            return true;
        }
        if (args.length == 0) {
            showOwnStatus(sender);
            return true;
        }

        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "check" -> check(sender, args);
            case "lift" -> lift(sender, args);
            case "ban" -> ban(sender, args);
            default -> module.getLang().send(sender, "general.unknown-command", "label", label);
        }
        return true;
    }

    private void showOwnStatus(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        module.getLang().send(sender, PvpMessages.STATUS_HEADER, "player", player.getName());
        renderCombatTag(sender, player.getUniqueId());
    }

    private void renderCombatTag(CommandSender sender, UUID playerId) {
        long remaining = module.getCombatTags().getRemainingSeconds(playerId);
        if (module.getCombatTags().isTagged(playerId)) {
            module.getLang().send(sender, PvpMessages.TAG_STATUS_ACTIVE,
                    "time", module.formatDuration(remaining));
        } else {
            module.getLang().send(sender, PvpMessages.TAG_STATUS_CLEAR);
        }
    }

    /** {@code /pvp check <player>} - combat tag and deathban of someone else. */
    private void check(CommandSender sender, String[] args) {
        if (args.length < 2) {
            module.getLang().send(sender, "general.unknown-command", "label", "pvp");
            return;
        }
        Optional<UUID> target = resolve(sender, args[1]);
        if (target.isEmpty()) {
            return;
        }

        module.getLang().send(sender, PvpMessages.STATUS_HEADER, "player", args[1]);
        renderCombatTag(sender, target.get());

        Optional<Deathban> ban = module.getDeathbans().getActiveBan(target.get());
        if (ban.isPresent() && ban.get().isUntilMapEnd()) {
            module.getLang().send(sender, PvpMessages.DEATHBAN_MAP_END_STATUS);
        } else if (ban.isPresent()) {
            module.getLang().send(sender, PvpMessages.DEATHBAN_STATUS,
                    "time", module.formatDuration(
                            ban.get().remainingSeconds(System.currentTimeMillis())));
        } else {
            module.getLang().send(sender, PvpMessages.DEATHBAN_NOT_BANNED, "player", args[1]);
        }
    }

    /** {@code /pvp lift <player>} - ends a deathban early. */
    private void lift(CommandSender sender, String[] args) {
        if (args.length < 2) {
            module.getLang().send(sender, "general.unknown-command", "label", "pvp");
            return;
        }
        Optional<UUID> target = resolve(sender, args[1]);
        if (target.isEmpty()) {
            return;
        }
        if (module.getDeathbans().lift(target.get())) {
            module.getLang().send(sender, PvpMessages.DEATHBAN_LIFTED, "player", args[1]);
        } else {
            module.getLang().send(sender, PvpMessages.DEATHBAN_NOT_BANNED, "player", args[1]);
        }
    }

    /** {@code /pvp ban <player> <seconds>} - deathbans somebody by hand. */
    private void ban(CommandSender sender, String[] args) {
        if (args.length < 3) {
            module.getLang().send(sender, "general.unknown-command", "label", "pvp");
            return;
        }
        Optional<UUID> target = resolve(sender, args[1]);
        if (target.isEmpty()) {
            return;
        }
        long seconds;
        try {
            seconds = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            module.getLang().send(sender, PvpMessages.INVALID_NUMBER, "input", args[2]);
            return;
        }
        // Zero or less answered "deathbans are disabled", and a length past a century
        // overflowed into the past and banned nobody (found in the command review).
        if (!Durations.isAcceptable(seconds)) {
            module.getLang().send(sender, PvpMessages.DEATHBAN_INVALID_LENGTH, "input", args[2]);
            return;
        }

        Optional<Deathban> ban = module.getDeathbans().apply(target.get(), seconds, "staff");
        if (ban.isEmpty()) {
            module.getLang().send(sender, PvpMessages.DISABLED);
            return;
        }
        module.getLang().send(sender, PvpMessages.DEATHBAN_SET,
                "player", args[1], "time", module.formatDuration(seconds));
        // On the server now, they would otherwise play on until they next logged in.
        module.kickDeathbanned(target.get(), false);
    }

    /**
     * @return the target's uuid, or empty after reporting that nobody by that name
     *         is known; offline players resolve too, since a deathban outlives the
     *         session that caused it - through the server's name cache, never by
     *         listing the player data folder on the main thread (see
     *         {@code EconomyModule#resolvePlayer})
     */
    private Optional<UUID> resolve(CommandSender sender, String name) {
        Optional<UUID> id = KnownPlayers.idOf(name);
        if (id.isEmpty()) {
            module.getLang().send(sender, PvpMessages.PLAYER_NOT_FOUND, "player", name);
        }
        return id;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            for (String sub : ADMIN_SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    options.add(sub);
                }
            }
            return options;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
