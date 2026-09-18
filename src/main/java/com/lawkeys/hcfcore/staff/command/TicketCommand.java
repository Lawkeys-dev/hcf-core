package com.lawkeys.hcfcore.staff.command;

import com.lawkeys.hcfcore.command.VisiblePlayers;
import com.lawkeys.hcfcore.staff.StaffMessages;
import com.lawkeys.hcfcore.staff.StaffModule;
import com.lawkeys.hcfcore.staff.ticket.Ticket;
import com.lawkeys.hcfcore.staff.ticket.TicketType;
import com.lawkeys.hcfcore.util.ColorCodes;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Bukkit;
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
import java.util.OptionalLong;
import java.util.UUID;

/**
 * {@code /report}, {@code /request} and {@code /tickets}.
 *
 * <p>Reports and requests share a queue because they are the same act with a
 * different word on the front: somebody wants staff attention, and here is why.
 *
 * <p>{@code /tickets} on its own opens {@link TicketMenu}, the management menu
 * FEATURES.md section 8 asks for. The subcommands do the same things by id, for
 * the console and for staff who would rather type.
 */
public final class TicketCommand implements TabExecutor {

    private static final List<String> STAFF_SUBCOMMANDS = List.of("list", "view", "claim", "close");

    private final StaffModule module;

    public TicketCommand(StaffModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.getSettings().enabled() || !module.getSettings().tickets().enabled()) {
            module.getLang().send(sender, StaffMessages.DISABLED);
            return true;
        }
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "report" -> open(sender, args, label, TicketType.REPORT);
            case "request" -> open(sender, args, label, TicketType.REQUEST);
            case "tickets" -> staff(sender, args, label);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Players
    // ------------------------------------------------------------------

    private void open(CommandSender sender, String[] args, String label, TicketType type) {
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, "general.player-only");
            return;
        }
        int messageFrom = type == TicketType.REPORT ? 1 : 0;
        if (args.length <= messageFrom) {
            module.getLang().send(sender, StaffMessages.USAGE, "usage", type == TicketType.REPORT
                    ? "/" + label + " <player> <reason>"
                    : "/" + label + " <message>");
            return;
        }
        Player target = null;
        if (type == TicketType.REPORT) {
            // Somebody the reporter cannot see is answered exactly like somebody who
            // is offline: anything else would let a player find vanished staff by
            // trying to report them.
            target = VisiblePlayers.find(player, args[0]).orElse(null);
            if (target == null) {
                module.getLang().send(sender, StaffMessages.PLAYER_NOT_FOUND, "player", args[0]);
                return;
            }
            if (target.equals(player)) {
                module.getLang().send(sender, StaffMessages.TICKET_SELF);
                return;
            }
        }
        String message = String.join(" ", Arrays.copyOfRange(args, messageFrom, args.length));
        Optional<Ticket> opened = module.getTickets().open(type, player.getUniqueId(), player.getName(),
                target == null ? null : target.getUniqueId(),
                target == null ? null : target.getName(), message);
        if (opened.isEmpty()) {
            module.getLang().send(player, StaffMessages.TICKET_TOO_SOON, "time",
                    Durations.formatWithSeconds(module.getTickets().remainingCooldown(player.getUniqueId())));
            return;
        }
        Ticket ticket = opened.get();
        module.flushSoon();
        module.getLang().send(player, StaffMessages.TICKET_OPENED, "id", String.valueOf(ticket.id()));
        // A request is about nobody: its line has no "about", rather than "about -".
        module.sendToStaffChannel(module.getLang().get(ticket.type() == TicketType.REPORT
                        ? StaffMessages.TICKET_ANNOUNCE : StaffMessages.TICKET_ANNOUNCE_REQUEST,
                "id", String.valueOf(ticket.id()),
                "type", typeName(ticket),
                "player", ticket.openedName(),
                "target", targetName(ticket),
                // Last, and escaped: what a player typed is shown as they typed it.
                "message", ColorCodes.escape(ticket.message())));
    }

    // ------------------------------------------------------------------
    // Staff
    // ------------------------------------------------------------------

    private void staff(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                TicketMenu.open(module, this, player);
            } else {
                list(sender);
            }
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "view" -> parseId(sender, args, label, "view").ifPresent(id -> view(sender, id));
            case "claim" -> parseId(sender, args, label, "claim").ifPresent(id -> claim(sender, id));
            case "close" -> parseId(sender, args, label, "close").ifPresent(id -> close(sender, id));
            default -> module.getLang().send(sender, StaffMessages.USAGE,
                    "usage", "/" + label + " [list|view <id>|claim <id>|close <id>]");
        }
    }

    private void list(CommandSender sender) {
        List<Ticket> queue = module.getTickets().queue();
        if (queue.isEmpty()) {
            module.getLang().send(sender, StaffMessages.TICKET_LIST_EMPTY);
            return;
        }
        module.getLang().send(sender, StaffMessages.TICKET_LIST_HEADER,
                "count", String.valueOf(queue.size()));
        long now = System.currentTimeMillis();
        for (Ticket ticket : queue) {
            sendEntry(sender, ticket, now);
        }
    }

    private void view(CommandSender sender, long id) {
        module.getTickets().get(id).ifPresentOrElse(
                ticket -> sendEntry(sender, ticket, System.currentTimeMillis()),
                () -> module.getLang().send(sender, StaffMessages.TICKET_UNKNOWN, "id", String.valueOf(id)));
    }

    private void sendEntry(CommandSender sender, Ticket ticket, long now) {
        module.getLang().send(sender, ticket.type() == TicketType.REPORT
                        ? StaffMessages.TICKET_LIST_ENTRY : StaffMessages.TICKET_LIST_ENTRY_REQUEST,
                "id", String.valueOf(ticket.id()),
                "type", typeName(ticket),
                "status", statusName(ticket),
                "player", ticket.openedName(),
                "target", targetName(ticket),
                "age", age(ticket, now),
                "message", ColorCodes.escape(ticket.message()));
    }

    /** Takes a ticket, so nobody else works on it too. */
    public void claim(CommandSender sender, long id) {
        Optional<Ticket> claimed = module.getTickets().claim(id, sender.getName());
        if (claimed.isEmpty()) {
            module.getLang().send(sender, module.getTickets().get(id).isPresent()
                    ? StaffMessages.TICKET_ALREADY_CLAIMED : StaffMessages.TICKET_UNKNOWN,
                    "id", String.valueOf(id));
            return;
        }
        module.flushSoon();
        module.getLang().send(sender, StaffMessages.TICKET_CLAIMED, "id", String.valueOf(id));
        tellOpener(claimed.get(), StaffMessages.TICKET_NOTIFY_CLAIMED);
    }

    public void close(CommandSender sender, long id) {
        Optional<Ticket> closed = module.getTickets().close(id, sender.getName());
        if (closed.isEmpty()) {
            module.getLang().send(sender, StaffMessages.TICKET_UNKNOWN, "id", String.valueOf(id));
            return;
        }
        module.flushSoon();
        module.getLang().send(sender, StaffMessages.TICKET_CLOSED, "id", String.valueOf(id));
        tellOpener(closed.get(), StaffMessages.TICKET_NOTIFY_CLOSED);
    }

    /**
     * Takes the ticket if nobody has it yet, and goes to see.
     *
     * <p>To the reported player where there is one - that is who a report asks staff
     * to watch - and otherwise to whoever raised it. A ticket somebody else has
     * already taken can still be visited: two staff watching one fight is not the
     * duplicated work that claiming exists to prevent.
     */
    public void visit(Player staff, long id) {
        Ticket ticket = module.getTickets().get(id).orElse(null);
        if (ticket == null) {
            module.getLang().send(staff, StaffMessages.TICKET_UNKNOWN, "id", String.valueOf(id));
            return;
        }
        if (ticket.isOpen()) {
            claim(staff, id);
        }
        Player subject = online(ticket.target());
        if (subject == null) {
            subject = online(ticket.openedBy());
        }
        if (subject == null || subject.equals(staff)) {
            module.getLang().send(staff, StaffMessages.TICKET_NOBODY_TO_VISIT, "id", String.valueOf(id));
            return;
        }
        staff.teleportAsync(subject.getLocation());
        module.getLang().send(staff, StaffMessages.TELEPORTED_TO, "player", subject.getName());
    }

    private void tellOpener(Ticket ticket, String key) {
        Player opener = online(ticket.openedBy());
        if (opener != null) {
            module.getLang().send(opener, key, "id", String.valueOf(ticket.id()));
        }
    }

    private static Player online(UUID playerId) {
        return playerId == null ? null : Bukkit.getPlayer(playerId);
    }

    static String typeName(Ticket ticket) {
        return ticket.type().name().toLowerCase(Locale.ROOT);
    }

    static String statusName(Ticket ticket) {
        String status = ticket.status().name().toLowerCase(Locale.ROOT);
        return ticket.handledBy() == null ? status : status + " by " + ticket.handledBy();
    }

    static String targetName(Ticket ticket) {
        return ticket.targetName() == null ? "-" : ticket.targetName();
    }

    static String age(Ticket ticket, long now) {
        return Durations.format(Math.max(0L, (now - ticket.openedAt()) / 1000L));
    }

    private OptionalLong parseId(CommandSender sender, String[] args, String label, String sub) {
        if (args.length < 2) {
            module.getLang().send(sender, StaffMessages.USAGE, "usage", "/" + label + " " + sub + " <id>");
            return OptionalLong.empty();
        }
        String raw = args[1].startsWith("#") ? args[1].substring(1) : args[1];
        try {
            return OptionalLong.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            module.getLang().send(sender, StaffMessages.INVALID_NUMBER, "input", args[1]);
            return OptionalLong.empty();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("report") && args.length == 1) {
            return VisiblePlayers.names(sender, args[0]);
        }
        if (!command.getName().equalsIgnoreCase("tickets")
                || !sender.hasPermission(StaffModule.STAFF_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return STAFF_SUBCOMMANDS.stream().filter(sub -> sub.startsWith(prefix)).toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            List<String> ids = new ArrayList<>();
            for (Ticket ticket : module.getTickets().queue()) {
                String id = String.valueOf(ticket.id());
                if (id.startsWith(args[1])) {
                    ids.add(id);
                }
            }
            return ids;
        }
        return List.of();
    }
}
