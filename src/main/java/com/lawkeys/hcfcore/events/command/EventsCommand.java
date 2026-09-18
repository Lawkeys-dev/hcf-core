package com.lawkeys.hcfcore.events.command;

import com.lawkeys.hcfcore.events.AgendaEntry;
import com.lawkeys.hcfcore.events.CaptureEventDefinition;
import com.lawkeys.hcfcore.events.EventManager;
import com.lawkeys.hcfcore.events.EventMessages;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.events.EventUpdate;
import com.lawkeys.hcfcore.events.RunningEvent;
import com.lawkeys.hcfcore.events.conquest.ConquestController;
import com.lawkeys.hcfcore.events.conquest.ConquestDefinition;
import com.lawkeys.hcfcore.events.conquest.ConquestMessages;
import com.lawkeys.hcfcore.events.conquest.ConquestRun;
import com.lawkeys.hcfcore.events.conquest.ConquestUpdate;
import com.lawkeys.hcfcore.events.king.KingEventController;
import com.lawkeys.hcfcore.events.king.KingEventDefinition;
import com.lawkeys.hcfcore.events.king.KingEventManager;
import com.lawkeys.hcfcore.events.king.KingMessages;
import com.lawkeys.hcfcore.events.king.KingRun;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code /events} - what is running, what is next, and the staff overrides.
 *
 * <p>Parses arguments and renders; every rule lives in {@link EventManager}.
 */
public final class EventsCommand implements TabExecutor {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EventModule module;

    public EventsCommand(EventModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EventManager manager = module.getManager();

        if (args.length == 0) {
            list(sender, manager);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (manager == null || !module.getSettings().enabled()) {
            // Only the staff verbs need the capture engine. The listing does not: a
            // server running a mountain and no KOTH still has an agenda to show.
            module.getLang().send(sender, EventMessages.DISABLED);
            return true;
        }
        return switch (action) {
            case "start" -> staffAction(sender, manager, args, true);
            case "stop" -> staffAction(sender, manager, args, false);
            default -> {
                list(sender, manager);
                yield true;
            }
        };
    }

    /**
     * Renders the agenda: the capture events, then whatever other modules
     * contributed (a mountain refill, typically - see {@code AgendaContributor}).
     */
    private void list(CommandSender sender, EventManager manager) {
        boolean captureEventsUsable = manager != null && module.getSettings().enabled();
        List<CaptureEventDefinition> definitions =
                captureEventsUsable ? module.getSettings().definitions() : List.of();
        KingEventController king = module.getKing();
        List<KingEventDefinition> kingDefinitions = captureEventsUsable && king != null
                ? king.getSettings().definitions() : List.of();
        ConquestController conquest = module.getConquest();
        List<ConquestDefinition> conquests = captureEventsUsable && conquest != null
                ? conquest.getSettings().definitions() : List.of();
        List<AgendaEntry> contributed = module.collectAgenda();

        if (definitions.isEmpty() && kingDefinitions.isEmpty() && conquests.isEmpty() && contributed.isEmpty()) {
            module.getLang().send(sender,
                    captureEventsUsable ? EventMessages.LIST_EMPTY : EventMessages.DISABLED);
            return;
        }

        module.getLang().send(sender, EventMessages.LIST_HEADER);
        for (CaptureEventDefinition definition : definitions) {
            Optional<RunningEvent> running = manager.getActiveEvent(definition.id());
            if (running.isPresent()) {
                RunningEvent event = running.get();
                // Two keys rather than one with an English word injected from here:
                // no user-visible text may live in code (ARCHITECTURE.md section 10).
                String key = event.isContested() ? EventMessages.LIST_CONTESTED
                        : event.getHolderTeamId() == null ? EventMessages.LIST_FREE : EventMessages.LIST_ACTIVE;
                module.getLang().send(sender, key,
                        "event", definition.displayName(),
                        "time", Durations.format(event.getRemainingSeconds()),
                        "team", holderName(event));
                continue;
            }
            Optional<ZonedDateTime> next = manager.getNextOccurrence(definition);
            if (next.isPresent()) {
                module.getLang().send(sender, EventMessages.LIST_SCHEDULED,
                        "event", definition.displayName(),
                        "time", TIME.format(next.get()));
            } else {
                module.getLang().send(sender, EventMessages.LIST_UNSCHEDULED,
                        "event", definition.displayName());
            }
        }

        for (KingEventDefinition definition : kingDefinitions) {
            listKing(sender, king, definition);
        }

        for (ConquestDefinition definition : conquests) {
            listConquest(sender, conquest, definition);
        }

        for (AgendaEntry entry : contributed) {
            module.getLang().send(sender, entry.messageKey(), entry.placeholders());
        }
    }

    private void listKing(CommandSender sender, KingEventController king, KingEventDefinition definition) {
        KingEventManager manager = king.getManager();
        Optional<KingRun> run = running(manager, definition);
        if (run.isPresent()) {
            if (run.get().isReigning()) {
                module.getLang().send(sender, KingMessages.LIST_ACTIVE,
                        "event", definition.displayName(),
                        "king", module.getTeams().nameOf(run.get().getKingId()),
                        "time", Durations.format(manager.getRemainingSeconds()));
            } else {
                module.getLang().send(sender, KingMessages.LIST_CROWNING, "event", definition.displayName());
            }
            return;
        }
        Optional<ZonedDateTime> next = manager.getNextOccurrence(definition);
        if (next.isPresent()) {
            module.getLang().send(sender, EventMessages.LIST_SCHEDULED,
                    "event", definition.displayName(), "time", TIME.format(next.get()));
        } else {
            module.getLang().send(sender, EventMessages.LIST_UNSCHEDULED, "event", definition.displayName());
        }
    }

    private void listConquest(CommandSender sender, ConquestController conquest, ConquestDefinition definition) {
        Optional<ConquestRun> run = conquest.getManager().getCurrent()
                .filter(current -> current.getDefinition().id().equalsIgnoreCase(definition.id()));
        if (run.isPresent()) {
            List<ConquestRun.Standing> standings = run.get().standings();
            if (standings.isEmpty()) {
                module.getLang().send(sender, ConquestMessages.LIST_ACTIVE_NOBODY, "event", definition.displayName(),
                        "target", String.valueOf(definition.pointsToWin()));
            } else {
                module.getLang().send(sender, ConquestMessages.LIST_ACTIVE, "event", definition.displayName(),
                        "team", module.getTeams().getManager().getTeam(standings.get(0).teamId())
                                .map(Team::getName).orElse("?"),
                        "points", String.valueOf(standings.get(0).points()),
                        "target", String.valueOf(definition.pointsToWin()));
            }
            return;
        }
        Optional<ZonedDateTime> next = conquest.getManager().getNextOccurrence(definition);
        if (next.isPresent()) {
            module.getLang().send(sender, EventMessages.LIST_SCHEDULED,
                    "event", definition.displayName(), "time", TIME.format(next.get()));
        } else {
            module.getLang().send(sender, EventMessages.LIST_UNSCHEDULED, "event", definition.displayName());
        }
    }

    /** @return the run of this definition, if it is the one running */
    private static Optional<KingRun> running(KingEventManager manager, KingEventDefinition definition) {
        return manager.getCurrent().filter(run -> run.getDefinition().id().equalsIgnoreCase(definition.id()));
    }

    /** @return the holding team's name, or an empty string when nobody holds it */
    private String holderName(RunningEvent event) {
        return Optional.ofNullable(event.getHolderTeamId())
                .flatMap(id -> module.getTeams().getManager().getTeam(id))
                .map(Team::getName)
                .orElse("");
    }

    private boolean staffAction(CommandSender sender, EventManager manager, String[] args,
                                boolean starting) {
        if (!sender.hasPermission(EventModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            return false; // Paper prints the usage from plugin.yml.
        }
        // Kill the King takes items into stashes that are loaded at startup: no
        // start, and no stop that hands them back, before they are in.
        if (module.getStartup().refuseCommand(sender)) {
            return true;
        }

        String id = args[1];
        Optional<CaptureEventDefinition> definition = module.getSettings().find(id);
        if (definition.isEmpty()) {
            Optional<KingEventDefinition> king = module.getKing() == null
                    ? Optional.empty() : module.getKing().getSettings().find(id);
            if (king.isPresent()) {
                return kingAction(sender, king.get(), starting);
            }
            Optional<ConquestDefinition> conquest = module.getConquest() == null
                    ? Optional.empty() : module.getConquest().getSettings().find(id);
            if (conquest.isPresent()) {
                return conquestAction(sender, conquest.get(), starting);
            }
            module.getLang().send(sender, EventMessages.UNKNOWN_EVENT, "event", id);
            return true;
        }

        Optional<EventUpdate> update = starting
                ? manager.start(definition.get())
                : manager.stop(definition.get().id());

        if (update.isEmpty()) {
            module.getLang().send(sender,
                    starting ? EventMessages.ALREADY_ACTIVE : EventMessages.NOT_ACTIVE,
                    "event", definition.get().displayName());
            return true;
        }

        module.broadcast(update.get().messageKey(), update.get().placeholders());
        module.getLang().send(sender,
                starting ? EventMessages.ADMIN_STARTED : EventMessages.ADMIN_STOPPED,
                "event", definition.get().displayName());
        return true;
    }

    /**
     * Start or stop a Kill the King. Starting only opens it: the King is drawn and
     * announced a few ticks later, once a spot in the warzone is found.
     */
    private boolean kingAction(CommandSender sender, KingEventDefinition definition, boolean starting) {
        KingEventController king = module.getKing();
        Optional<KingRun> current = king.getManager().getCurrent();
        if (starting) {
            if (current.isPresent()) {
                // Only one King at a time; say which event holds the slot.
                boolean same = current.get().getDefinition().id().equalsIgnoreCase(definition.id());
                module.getLang().send(sender, same ? EventMessages.ALREADY_ACTIVE : KingMessages.ALREADY_RUNNING,
                        "event", current.get().getDefinition().displayName());
                return true;
            }
            king.start(definition);
            // Called off at once - no warzone, a ceiling, too few players: the reason
            // has just been broadcast, and "Started" after it would contradict it.
            if (king.getManager().getCurrent().isPresent()) {
                module.getLang().send(sender, EventMessages.ADMIN_STARTED, "event", definition.displayName());
            }
            return true;
        }
        if (running(king.getManager(), definition).isEmpty() || !king.stop()) {
            module.getLang().send(sender, EventMessages.NOT_ACTIVE, "event", definition.displayName());
            return true;
        }
        module.getLang().send(sender, EventMessages.ADMIN_STOPPED, "event", definition.displayName());
        return true;
    }

    /** Start or stop a Conquest: one runs at a time. */
    private boolean conquestAction(CommandSender sender, ConquestDefinition definition, boolean starting) {
        ConquestController conquest = module.getConquest();
        Optional<ConquestRun> current = conquest.getManager().getCurrent();
        if (starting) {
            Optional<ConquestUpdate> started = conquest.getManager().start(definition);
            if (started.isEmpty()) {
                boolean same = current.isPresent() && current.get().getDefinition().id().equalsIgnoreCase(definition.id());
                module.getLang().send(sender, same ? EventMessages.ALREADY_ACTIVE : ConquestMessages.ALREADY_RUNNING,
                        "event", current.map(run -> run.getDefinition().displayName()).orElse(definition.displayName()));
                return true;
            }
            conquest.announce(started.get());
            module.getLang().send(sender, EventMessages.ADMIN_STARTED, "event", definition.displayName());
            return true;
        }
        if (current.isEmpty() || !current.get().getDefinition().id().equalsIgnoreCase(definition.id())) {
            module.getLang().send(sender, EventMessages.NOT_ACTIVE, "event", definition.displayName());
            return true;
        }
        conquest.getManager().stop().ifPresent(conquest::announce);
        module.getLang().send(sender, EventMessages.ADMIN_STOPPED, "event", definition.displayName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(EventModule.ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return prefixed(List.of("start", "stop"), args[0]);
        }
        if (args.length == 2) {
            List<String> ids = new ArrayList<>();
            for (CaptureEventDefinition definition : module.getSettings().definitions()) {
                ids.add(definition.id());
            }
            if (module.getKing() != null) {
                for (KingEventDefinition definition : module.getKing().getSettings().definitions()) {
                    ids.add(definition.id());
                }
            }
            if (module.getConquest() != null) {
                for (ConquestDefinition definition : module.getConquest().getSettings().definitions()) {
                    ids.add(definition.id());
                }
            }
            return prefixed(ids, args[1]);
        }
        return List.of();
    }

    private static List<String> prefixed(List<String> candidates, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
