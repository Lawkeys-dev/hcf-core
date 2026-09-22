package com.lawkeys.hcfcore.events.command;

import com.lawkeys.hcfcore.events.AgendaEntry;
import com.lawkeys.hcfcore.events.CaptureEventDefinition;
import com.lawkeys.hcfcore.events.EventLauncher;
import com.lawkeys.hcfcore.events.EventManager;
import com.lawkeys.hcfcore.events.EventMessages;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.events.RunningEvent;
import com.lawkeys.hcfcore.events.Standing;
import com.lawkeys.hcfcore.events.setup.EventSetup;
import com.lawkeys.hcfcore.events.conquest.ConquestController;
import com.lawkeys.hcfcore.events.conquest.ConquestDefinition;
import com.lawkeys.hcfcore.events.conquest.ConquestMessages;
import com.lawkeys.hcfcore.events.conquest.ConquestRun;
import com.lawkeys.hcfcore.events.core.CoreEventController;
import com.lawkeys.hcfcore.events.core.CoreEventDefinition;
import com.lawkeys.hcfcore.events.core.CoreEventKind;
import com.lawkeys.hcfcore.events.core.CoreMessages;
import com.lawkeys.hcfcore.events.core.CoreRun;
import com.lawkeys.hcfcore.events.core.CoreWinRule;
import com.lawkeys.hcfcore.events.king.KingEventController;
import com.lawkeys.hcfcore.events.king.KingEventDefinition;
import com.lawkeys.hcfcore.events.king.KingEventManager;
import com.lawkeys.hcfcore.events.king.KingMessages;
import com.lawkeys.hcfcore.events.king.KingRun;
import com.lawkeys.hcfcore.events.slide.SlideController;
import com.lawkeys.hcfcore.events.slide.SlideDefinition;
import com.lawkeys.hcfcore.events.slide.SlideMessages;
import com.lawkeys.hcfcore.events.slide.SlideRun;
import com.lawkeys.hcfcore.events.totem.TotemController;
import com.lawkeys.hcfcore.events.totem.TotemDefinition;
import com.lawkeys.hcfcore.events.totem.TotemMessages;
import com.lawkeys.hcfcore.events.totem.TotemRun;
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
    private final EventSetup setup;

    public EventsCommand(EventModule module) {
        this.module = Objects.requireNonNull(module, "module");
        this.setup = new EventSetup(module);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EventManager manager = module.getManager();

        if (args.length == 0) {
            list(sender, manager);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        // The setup verbs write events.yml themselves, for every kind of event: they
        // do not need the capture engine to be enabled.
        if (EventSetup.VERBS.contains(action)) {
            return setup.handle(sender, action, args);
        }
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
        CoreEventController core = module.getCore();
        List<CoreEventDefinition> coreEvents = captureEventsUsable && core != null
                ? core.getSettings().definitions() : List.of();
        SlideController slide = module.getSlide();
        List<SlideDefinition> slides = captureEventsUsable && slide != null
                ? slide.getSettings().definitions() : List.of();
        TotemController totem = module.getTotem();
        List<TotemDefinition> totems = captureEventsUsable && totem != null
                ? totem.getSettings().definitions() : List.of();
        List<AgendaEntry> contributed = module.collectAgenda();

        if (definitions.isEmpty() && kingDefinitions.isEmpty() && conquests.isEmpty() && coreEvents.isEmpty()
                && slides.isEmpty() && totems.isEmpty() && contributed.isEmpty()) {
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

        for (CoreEventDefinition definition : coreEvents) {
            listCore(sender, core, definition);
        }

        for (SlideDefinition definition : slides) {
            listSlide(sender, slide, definition);
        }

        for (TotemDefinition definition : totems) {
            listTotem(sender, totem, definition);
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
            List<Standing> standings = run.get().standings();
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

    private void listCore(CommandSender sender, CoreEventController core, CoreEventDefinition definition) {
        Optional<CoreRun> run = core.getManager().getCurrent()
                .filter(current -> current.getDefinition().id().equalsIgnoreCase(definition.id()));
        boolean dtc = definition.kind() == CoreEventKind.DTC;
        if (run.isPresent()) {
            CoreRun current = run.get();
            if (definition.winRule() == CoreWinRule.FIRST_TO_TARGET) {
                List<Standing> standings = current.standings();
                if (standings.isEmpty()) {
                    module.getLang().send(sender, CoreMessages.DTC_LIST_ACTIVE_NOBODY, "event", definition.displayName(),
                            "target", String.valueOf(definition.breaks()));
                } else {
                    module.getLang().send(sender, CoreMessages.DTC_LIST_ACTIVE_PER_TEAM, "event", definition.displayName(),
                            "team", module.getTeams().getManager().getTeam(standings.get(0).teamId())
                                    .map(Team::getName).orElse("?"),
                            "breaks", String.valueOf(standings.get(0).points()),
                            "target", String.valueOf(definition.breaks()));
                }
            } else {
                module.getLang().send(sender, dtc ? CoreMessages.DTC_LIST_ACTIVE : CoreMessages.LAST_BREAK_LIST_ACTIVE,
                        "event", definition.displayName(),
                        "health", String.valueOf(current.health()), "max", String.valueOf(definition.breaks()));
            }
            return;
        }
        Optional<ZonedDateTime> next = core.getManager().getNextOccurrence(definition);
        if (next.isPresent()) {
            module.getLang().send(sender, EventMessages.LIST_SCHEDULED,
                    "event", definition.displayName(), "time", TIME.format(next.get()));
        } else {
            module.getLang().send(sender, EventMessages.LIST_UNSCHEDULED, "event", definition.displayName());
        }
    }

    private void listTotem(CommandSender sender, TotemController totem, TotemDefinition definition) {
        Optional<TotemRun> run = totem.getManager().getCurrent()
                .filter(current -> current.getDefinition().id().equalsIgnoreCase(definition.id()));
        if (run.isPresent()) {
            if (run.get().holder() == null) {
                module.getLang().send(sender, TotemMessages.LIST_ACTIVE_NOBODY, "event", definition.displayName(),
                        "height", String.valueOf(definition.height()));
            } else {
                module.getLang().send(sender, TotemMessages.LIST_ACTIVE, "event", definition.displayName(),
                        "team", module.getTeams().getManager().getTeam(run.get().holder()).map(Team::getName).orElse("?"),
                        "broken", String.valueOf(run.get().brokenCount()),
                        "height", String.valueOf(definition.height()));
            }
            return;
        }
        Optional<ZonedDateTime> next = totem.getManager().getNextOccurrence(definition);
        if (next.isPresent()) {
            module.getLang().send(sender, EventMessages.LIST_SCHEDULED,
                    "event", definition.displayName(), "time", TIME.format(next.get()));
        } else {
            module.getLang().send(sender, EventMessages.LIST_UNSCHEDULED, "event", definition.displayName());
        }
    }

    private void listSlide(CommandSender sender, SlideController slide, SlideDefinition definition) {
        Optional<SlideRun> run = slide.getManager().getCurrent()
                .filter(current -> current.getDefinition().id().equalsIgnoreCase(definition.id()));
        if (run.isPresent()) {
            List<Standing> standings = run.get().standings();
            if (standings.isEmpty()) {
                module.getLang().send(sender, SlideMessages.LIST_ACTIVE_NOBODY, "event", definition.displayName(),
                        "target", String.valueOf(definition.pointsToWin()));
            } else {
                module.getLang().send(sender, SlideMessages.LIST_ACTIVE, "event", definition.displayName(),
                        "team", module.getTeams().getManager().getTeam(standings.get(0).teamId())
                                .map(Team::getName).orElse("?"),
                        "points", String.valueOf(standings.get(0).points()),
                        "target", String.valueOf(definition.pointsToWin()));
            }
            return;
        }
        Optional<ZonedDateTime> next = slide.getManager().getNextOccurrence(definition);
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
        EventLauncher.Result result = starting
                ? module.getLauncher().start(args[1])
                : module.getLauncher().stop(args[1]);
        if (result.messageKey() != null) {
            module.getLang().send(sender, result.messageKey(), "event", result.event());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(EventModule.ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> verbs = new ArrayList<>(List.of("start", "stop"));
            verbs.addAll(EventSetup.VERBS);
            return prefixed(verbs, args[0]);
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (EventSetup.VERBS.contains(action)) {
            return prefixed(setup.complete(action, args), args[args.length - 1]);
        }
        if (args.length == 2 && (action.equals("start") || action.equals("stop"))) {
            return prefixed(setup.ids(), args[1]);
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
