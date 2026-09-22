package com.lawkeys.hcfcore.events.command;

import com.lawkeys.hcfcore.events.AgendaEntry;
import com.lawkeys.hcfcore.events.CaptureEventDefinition;
import com.lawkeys.hcfcore.events.EventManager;
import com.lawkeys.hcfcore.events.EventMessages;
import com.lawkeys.hcfcore.events.EventModule;
import com.lawkeys.hcfcore.events.EventIds;
import com.lawkeys.hcfcore.events.EventUpdate;
import com.lawkeys.hcfcore.events.RunningEvent;
import com.lawkeys.hcfcore.events.Standing;
import com.lawkeys.hcfcore.events.conquest.ConquestController;
import com.lawkeys.hcfcore.events.conquest.ConquestDefinition;
import com.lawkeys.hcfcore.events.conquest.ConquestMessages;
import com.lawkeys.hcfcore.events.conquest.ConquestRun;
import com.lawkeys.hcfcore.events.conquest.ConquestUpdate;
import com.lawkeys.hcfcore.events.core.CoreEventController;
import com.lawkeys.hcfcore.events.core.CoreEventDefinition;
import com.lawkeys.hcfcore.events.core.CoreEventKind;
import com.lawkeys.hcfcore.events.core.CoreMessages;
import com.lawkeys.hcfcore.events.core.CoreRun;
import com.lawkeys.hcfcore.events.core.CoreUpdate;
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
import com.lawkeys.hcfcore.events.slide.SlideUpdate;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.util.Cuboid;
import com.lawkeys.hcfcore.util.Durations;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

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
        // The setup commands only touch DTC, Last Break and Slide, and write
        // events.yml themselves - they do not need the capture engine to be enabled.
        if (List.of("create", "setzone", "setcore", "delete").contains(action)) {
            return setupAction(sender, action, args);
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
        List<AgendaEntry> contributed = module.collectAgenda();

        if (definitions.isEmpty() && kingDefinitions.isEmpty() && conquests.isEmpty() && coreEvents.isEmpty()
                && slides.isEmpty() && contributed.isEmpty()) {
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
            Optional<CoreEventDefinition> coreEvent = module.getCore() == null
                    ? Optional.empty() : module.getCore().getSettings().find(id);
            if (coreEvent.isPresent()) {
                return coreAction(sender, coreEvent.get(), starting);
            }
            Optional<SlideDefinition> slideEvent = module.getSlide() == null
                    ? Optional.empty() : module.getSlide().getSettings().find(id);
            if (slideEvent.isPresent()) {
                return slideAction(sender, slideEvent.get(), starting);
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

    /** Start or stop a DTC or Last Break: one runs at a time, like a Conquest. */
    private boolean coreAction(CommandSender sender, CoreEventDefinition definition, boolean starting) {
        CoreEventController core = module.getCore();
        Optional<CoreRun> current = core.getManager().getCurrent();
        if (starting) {
            Optional<CoreUpdate> started = core.getManager().start(definition);
            if (started.isEmpty()) {
                // DTC and Last Break share one run slot: whichever of the two is
                // actually running - not necessarily this definition's own kind,
                // which is why the message names the RUNNING one, never assuming
                // "one DTC at a time" for a Last Break that happens to be running.
                boolean same = current.isPresent() && current.get().getDefinition().id().equalsIgnoreCase(definition.id());
                module.getLang().send(sender, same ? EventMessages.ALREADY_ACTIVE : CoreMessages.CORE_ALREADY_RUNNING,
                        "event", current.map(run -> run.getDefinition().displayName()).orElse(definition.displayName()));
                return true;
            }
            core.announce(started.get());
            module.getLang().send(sender, EventMessages.ADMIN_STARTED, "event", definition.displayName());
            return true;
        }
        if (current.isEmpty() || !current.get().getDefinition().id().equalsIgnoreCase(definition.id())) {
            module.getLang().send(sender, EventMessages.NOT_ACTIVE, "event", definition.displayName());
            return true;
        }
        core.getManager().stop().ifPresent(core::announce);
        module.getLang().send(sender, EventMessages.ADMIN_STOPPED, "event", definition.displayName());
        return true;
    }

    /** Start or stop a Slide: one runs at a time, like a Conquest. */
    private boolean slideAction(CommandSender sender, SlideDefinition definition, boolean starting) {
        SlideController slide = module.getSlide();
        Optional<SlideRun> current = slide.getManager().getCurrent();
        if (starting) {
            Optional<SlideUpdate> started = slide.getManager().start(definition);
            if (started.isEmpty()) {
                boolean same = current.isPresent() && current.get().getDefinition().id().equalsIgnoreCase(definition.id());
                module.getLang().send(sender, same ? EventMessages.ALREADY_ACTIVE : SlideMessages.ALREADY_RUNNING,
                        "event", current.map(run -> run.getDefinition().displayName()).orElse(definition.displayName()));
                return true;
            }
            slide.announce(started.get());
            module.getLang().send(sender, EventMessages.ADMIN_STARTED, "event", definition.displayName());
            return true;
        }
        if (current.isEmpty() || !current.get().getDefinition().id().equalsIgnoreCase(definition.id())) {
            module.getLang().send(sender, EventMessages.NOT_ACTIVE, "event", definition.displayName());
            return true;
        }
        slide.getManager().stop().ifPresent(slide::announce);
        module.getLang().send(sender, EventMessages.ADMIN_STOPPED, "event", definition.displayName());
        return true;
    }

    // ------------------------------------------------------------------
    // Setup commands: /events create|setzone|setcore|delete
    //
    // For DTC, Last Break and Slide only. These are the only commands in the
    // plugin that rewrite a configuration file - see EventYamlStore's javadoc.
    // ------------------------------------------------------------------

    private boolean setupAction(CommandSender sender, String action, String[] args) {
        if (!sender.hasPermission(EventModule.ADMIN_PERMISSION)) {
            module.getLang().send(sender, "general.no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            module.getLang().send(sender, CoreMessages.SETUP_USAGE, "usage", "/events " + action + " ... (in game only)");
            return true;
        }
        return switch (action) {
            case "create" -> setupCreate(player, args);
            case "setzone" -> setupSetZone(player, args);
            case "setcore" -> setupSetCore(player, args);
            case "delete" -> setupDelete(player, args);
            default -> false;
        };
    }

    private boolean setupCreate(Player player, String[] args) {
        if (args.length < 3) {
            return false;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        String id = EventIds.normalize(args[2]);
        String section = switch (type) {
            case "dtc" -> "dtc";
            case "lastbreak" -> "last-break";
            case "slide" -> "slide";
            default -> null;
        };
        if (section == null) {
            module.getLang().send(player, CoreMessages.SETUP_UNKNOWN_TYPE, "type", type);
            return true;
        }
        if (!EventIds.isValid(args[2])) {
            module.getLang().send(player, CoreMessages.SETUP_INVALID_ID, "id", args[2],
                    "min", String.valueOf(EventIds.MIN_LENGTH), "max", String.valueOf(EventIds.MAX_LENGTH));
            return true;
        }
        if (idAlreadyTaken(id)) {
            module.getLang().send(player, CoreMessages.SETUP_ALREADY_EXISTS, "event", id);
            return true;
        }
        Location at = player.getLocation();
        int radius = module.getSetupZoneRadius();
        int cx = at.getBlockX();
        int cy = at.getBlockY();
        int cz = at.getBlockZ();
        String world = at.getWorld().getName();

        boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection parent = root.getConfigurationSection(section);
            if (parent == null) {
                parent = root.createSection(section);
            }
            if (parent.contains(id)) {
                return false;
            }
            ConfigurationSection entry = parent.createSection(id);
            entry.set("display-name", "{primary}" + id);
            entry.set("world", world);
            set(entry, "corner-1", cx - radius, cy - radius, cz - radius);
            set(entry, "corner-2", cx + radius, cy + radius, cz + radius);
            if (!section.equals("slide")) {
                ConfigurationSection core = entry.createSection("core");
                core.set("x", cx);
                core.set("y", cy);
                core.set("z", cz);
                core.set("material", "OBSIDIAN");
                if (section.equals("dtc")) {
                    entry.set("counter", "SHARED");
                }
                entry.set("breaks", 150);
                entry.set("break-cooldown-seconds", 1);
            } else {
                entry.set("points-per-player", 1);
                entry.set("interval-seconds", 1);
                entry.set("death-penalty", 10);
                entry.set("announce-deaths", true);
                entry.set("points-to-win", 500);
            }
            entry.set("announce-at", List.of());
            entry.set("max-duration-seconds", 0);
            entry.set("schedule", List.of());
            entry.set("reward-commands", List.of());
            return true;
        });
        if (!written) {
            module.getLang().send(player, CoreMessages.SETUP_WRITE_FAILED, "event", id);
            return true;
        }
        module.reloadSettings();
        module.getLang().send(player, CoreMessages.SETUP_CREATED, "event", id, "radius", String.valueOf(radius));
        return true;
    }

    private static void set(ConfigurationSection entry, String key, int x, int y, int z) {
        ConfigurationSection corner = entry.createSection(key);
        corner.set("x", x);
        corner.set("y", y);
        corner.set("z", z);
    }

    private boolean setupSetZone(Player player, String[] args) {
        if (args.length == 2) {
            return setupZoneWand(player, args[1]);
        }
        if (args.length < 3) {
            return false;
        }
        String id = args[1];
        Integer corner = switch (args[2]) {
            case "1" -> 1;
            case "2" -> 2;
            default -> null;
        };
        if (corner == null) {
            return false;
        }
        if (!EventIds.isValid(id)) {
            module.getLang().send(player, CoreMessages.SETUP_INVALID_ID, "id", id,
                    "min", String.valueOf(EventIds.MIN_LENGTH), "max", String.valueOf(EventIds.MAX_LENGTH));
            return true;
        }
        Optional<String> section = sectionOf(id);
        if (section.isEmpty()) {
            module.getLang().send(player, CoreMessages.SETUP_UNKNOWN_ID, "event", id);
            return true;
        }
        if (isRunning(id)) {
            module.getLang().send(player, CoreMessages.SETUP_RUNNING, "event", id);
            return true;
        }
        Location at = player.getLocation();
        boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection entry = entryOf(root, section.get(), id);
            if (entry == null) {
                return false;
            }
            set(entry, "corner-" + corner, at.getBlockX(), at.getBlockY(), at.getBlockZ());
            return true;
        });
        if (!written) {
            module.getLang().send(player, CoreMessages.SETUP_WRITE_FAILED, "event", id);
            return true;
        }
        module.reloadSettings();
        module.getLang().send(player, CoreMessages.SETUP_ZONE_SET, "event", id, "corner", String.valueOf(corner));
        return true;
    }

    /**
     * {@code /events setzone <id>} - the claiming wand, drawing the event's zone: left
     * and right click its corners, sneak and left-click to write it.
     */
    private boolean setupZoneWand(Player player, String id) {
        if (!EventIds.isValid(id)) {
            module.getLang().send(player, CoreMessages.SETUP_INVALID_ID, "id", id,
                    "min", String.valueOf(EventIds.MIN_LENGTH), "max", String.valueOf(EventIds.MAX_LENGTH));
            return true;
        }
        if (sectionOf(id).isEmpty()) {
            module.getLang().send(player, CoreMessages.SETUP_UNKNOWN_ID, "event", id);
            return true;
        }
        if (module.getClaims() == null) {
            module.getLang().send(player, CoreMessages.SETUP_NO_WAND);
            return true;
        }
        module.getClaims().getWandSessions().give(player, new ZoneTask(id));
        return true;
    }

    /** The wand drawing an event's zone. */
    private final class ZoneTask implements com.lawkeys.hcfcore.claim.wand.WandTask {

        private final String id;

        ZoneTask(String id) {
            this.id = id;
        }

        @Override
        public String label() {
            return id;
        }

        @Override
        public java.util.List<String> preview(Player player, com.lawkeys.hcfcore.claim.wand.Selection selection) {
            return java.util.List.of(module.getLang().get(CoreMessages.SETUP_ZONE_PREVIEW, "event", id,
                    "size", selection.width() + "x" + selection.length(),
                    "from", String.valueOf(selection.minY()),
                    "to", String.valueOf(selection.maxY() + module.getSetupZoneHeight())));
        }

        @Override
        public boolean confirm(Player player, com.lawkeys.hcfcore.claim.wand.Selection selection) {
            Optional<String> section = sectionOf(id);
            if (section.isEmpty()) {
                module.getLang().send(player, CoreMessages.SETUP_UNKNOWN_ID, "event", id);
                return true;
            }
            if (isRunning(id)) {
                module.getLang().send(player, CoreMessages.SETUP_RUNNING, "event", id);
                return false;
            }
            int top = selection.maxY() + module.getSetupZoneHeight();
            boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
                ConfigurationSection entry = entryOf(root, section.get(), id);
                if (entry == null) {
                    return false;
                }
                entry.set("world", selection.world());
                set(entry, "corner-1", selection.minX(), selection.minY(), selection.minZ());
                set(entry, "corner-2", selection.maxX(), top, selection.maxZ());
                return true;
            });
            if (!written) {
                module.getLang().send(player, CoreMessages.SETUP_WRITE_FAILED, "event", id);
                return false;
            }
            module.reloadSettings();
            module.getLang().send(player, CoreMessages.SETUP_ZONE_DRAWN, "event", id,
                    "size", selection.width() + "x" + selection.length());
            return true;
        }
    }

    private boolean setupSetCore(Player player, String[] args) {
        if (args.length < 2) {
            return false;
        }
        String id = args[1];
        if (!EventIds.isValid(id)) {
            module.getLang().send(player, CoreMessages.SETUP_INVALID_ID, "id", id,
                    "min", String.valueOf(EventIds.MIN_LENGTH), "max", String.valueOf(EventIds.MAX_LENGTH));
            return true;
        }
        Optional<CoreEventDefinition> definition = module.getCore() == null
                ? Optional.empty() : module.getCore().getSettings().find(id);
        if (definition.isEmpty()) {
            boolean isSlide = module.getSlide() != null && module.getSlide().getSettings().find(id).isPresent();
            module.getLang().send(player, isSlide ? CoreMessages.SETUP_CORE_NOT_APPLICABLE : CoreMessages.SETUP_UNKNOWN_ID,
                    "event", id);
            return true;
        }
        if (isRunning(id)) {
            module.getLang().send(player, CoreMessages.SETUP_RUNNING, "event", id);
            return true;
        }
        Block target = player.getTargetBlockExact(module.getSetupCoreDistance());
        if (target == null) {
            module.getLang().send(player, CoreMessages.SETUP_NO_TARGET_BLOCK);
            return true;
        }
        Cuboid zone = definition.get().zone();
        if (!zone.containsBlock(target.getWorld().getName(), target.getX(), target.getY(), target.getZ())) {
            module.getLang().send(player, CoreMessages.SETUP_CORE_OUTSIDE_ZONE, "event", id);
            return true;
        }
        String section = definition.get().kind() == CoreEventKind.DTC ? "dtc" : "last-break";
        boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection entry = entryOf(root, section, id);
            if (entry == null) {
                return false;
            }
            ConfigurationSection core = entry.getConfigurationSection("core");
            if (core == null) {
                core = entry.createSection("core");
            }
            core.set("x", target.getX());
            core.set("y", target.getY());
            core.set("z", target.getZ());
            if (!core.contains("material")) {
                core.set("material", "OBSIDIAN");
            }
            return true;
        });
        if (!written) {
            module.getLang().send(player, CoreMessages.SETUP_WRITE_FAILED, "event", id);
            return true;
        }
        module.reloadSettings();
        module.getLang().send(player, CoreMessages.SETUP_CORE_SET, "event", id);
        module.getCore().getSettings().find(id).ifPresent(now -> {
            if (!module.getCore().isOnSystemClaim(now)) {
                module.getLang().send(player, CoreMessages.SETUP_NOT_IN_CLAIM, "event", id);
            }
        });
        return true;
    }

    private boolean setupDelete(Player player, String[] args) {
        if (args.length < 2) {
            return false;
        }
        String id = args[1];
        if (!EventIds.isValid(id)) {
            module.getLang().send(player, CoreMessages.SETUP_INVALID_ID, "id", id,
                    "min", String.valueOf(EventIds.MIN_LENGTH), "max", String.valueOf(EventIds.MAX_LENGTH));
            return true;
        }
        Optional<String> section = sectionOf(id);
        if (section.isEmpty()) {
            module.getLang().send(player, CoreMessages.SETUP_UNKNOWN_ID, "event", id);
            return true;
        }
        if (isRunning(id)) {
            module.getLang().send(player, CoreMessages.SETUP_RUNNING, "event", id);
            return true;
        }
        boolean written = EventYamlStore.edit(module.getPlugin(), root -> {
            ConfigurationSection parent = root.getConfigurationSection(section.get());
            if (parent == null || !parent.contains(id)) {
                return false;
            }
            parent.set(id, null);
            return true;
        });
        if (!written) {
            module.getLang().send(player, CoreMessages.SETUP_WRITE_FAILED, "event", id);
            return true;
        }
        module.reloadSettings();
        module.getLang().send(player, CoreMessages.SETUP_DELETED, "event", id);
        return true;
    }

    /** @return "dtc", "last-break" or "slide" - whichever of the three this id belongs to */
    private Optional<String> sectionOf(String id) {
        if (module.getCore() != null) {
            Optional<CoreEventDefinition> definition = module.getCore().getSettings().find(id);
            if (definition.isPresent()) {
                return Optional.of(definition.get().kind() == CoreEventKind.DTC ? "dtc" : "last-break");
            }
        }
        if (module.getSlide() != null && module.getSlide().getSettings().find(id).isPresent()) {
            return Optional.of("slide");
        }
        return Optional.empty();
    }

    private boolean isRunning(String id) {
        if (module.getCore() != null && module.getCore().getManager().getCurrent()
                .filter(run -> run.getDefinition().id().equalsIgnoreCase(id)).isPresent()) {
            return true;
        }
        return module.getSlide() != null && module.getSlide().getManager().getCurrent()
                .filter(run -> run.getDefinition().id().equalsIgnoreCase(id)).isPresent();
    }

    private boolean idAlreadyTaken(String id) {
        if (module.getSettings().find(id).isPresent()) {
            return true;
        }
        if (module.getKing() != null && module.getKing().getSettings().find(id).isPresent()) {
            return true;
        }
        if (module.getConquest() != null && module.getConquest().getSettings().find(id).isPresent()) {
            return true;
        }
        if (module.getCore() != null && module.getCore().getSettings().find(id).isPresent()) {
            return true;
        }
        return module.getSlide() != null && module.getSlide().getSettings().find(id).isPresent();
    }

    /** @return the {@code id}'s own section under {@code sectionName}, or {@code null} if it vanished */
    private static ConfigurationSection entryOf(ConfigurationSection root, String sectionName, String id) {
        ConfigurationSection parent = root.getConfigurationSection(sectionName);
        if (parent == null) {
            return null;
        }
        for (String key : parent.getKeys(false)) {
            if (key.equalsIgnoreCase(id)) {
                return parent.getConfigurationSection(key);
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(EventModule.ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return prefixed(List.of("start", "stop", "create", "setzone", "setcore", "delete"), args[0]);
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (action.equals("create")) {
                return prefixed(List.of("dtc", "lastbreak", "slide"), args[1]);
            }
            List<String> ids = new ArrayList<>();
            if (action.equals("setzone") || action.equals("setcore") || action.equals("delete")) {
                if (module.getCore() != null) {
                    module.getCore().getSettings().definitions().forEach(d -> ids.add(d.id()));
                }
                if (module.getSlide() != null && !action.equals("setcore")) {
                    module.getSlide().getSettings().definitions().forEach(d -> ids.add(d.id()));
                }
                return prefixed(ids, args[1]);
            }
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
            if (module.getCore() != null) {
                for (CoreEventDefinition definition : module.getCore().getSettings().definitions()) {
                    ids.add(definition.id());
                }
            }
            if (module.getSlide() != null) {
                for (SlideDefinition definition : module.getSlide().getSettings().definitions()) {
                    ids.add(definition.id());
                }
            }
            return prefixed(ids, args[1]);
        }
        if (args.length == 3 && action.equals("create")) {
            return List.of();
        }
        if (args.length == 3 && action.equals("setzone")) {
            return prefixed(List.of("1", "2"), args[2]);
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
