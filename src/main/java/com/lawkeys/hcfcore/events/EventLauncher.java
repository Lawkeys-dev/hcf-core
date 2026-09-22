package com.lawkeys.hcfcore.events;

import com.lawkeys.hcfcore.events.conquest.ConquestController;
import com.lawkeys.hcfcore.events.conquest.ConquestDefinition;
import com.lawkeys.hcfcore.events.conquest.ConquestMessages;
import com.lawkeys.hcfcore.events.conquest.ConquestRun;
import com.lawkeys.hcfcore.events.conquest.ConquestUpdate;
import com.lawkeys.hcfcore.events.core.CoreEventController;
import com.lawkeys.hcfcore.events.core.CoreEventDefinition;
import com.lawkeys.hcfcore.events.core.CoreMessages;
import com.lawkeys.hcfcore.events.core.CoreRun;
import com.lawkeys.hcfcore.events.core.CoreUpdate;
import com.lawkeys.hcfcore.events.king.KingEventController;
import com.lawkeys.hcfcore.events.king.KingEventDefinition;
import com.lawkeys.hcfcore.events.king.KingMessages;
import com.lawkeys.hcfcore.events.king.KingRun;
import com.lawkeys.hcfcore.events.slide.SlideController;
import com.lawkeys.hcfcore.events.slide.SlideDefinition;
import com.lawkeys.hcfcore.events.slide.SlideMessages;
import com.lawkeys.hcfcore.events.slide.SlideRun;
import com.lawkeys.hcfcore.events.slide.SlideUpdate;
import com.lawkeys.hcfcore.events.totem.TotemController;
import com.lawkeys.hcfcore.events.totem.TotemDefinition;
import com.lawkeys.hcfcore.events.totem.TotemMessages;
import com.lawkeys.hcfcore.events.totem.TotemRun;
import com.lawkeys.hcfcore.events.totem.TotemUpdate;

import java.util.Objects;
import java.util.Optional;

/**
 * Starts and stops any event by its id, whatever its engine: {@code /events start|stop}
 * and the weekly schedule go through here, so a scheduled start is exactly a
 * staff one.
 *
 * <p>The start or the end itself is announced to everybody by the engine, as ever;
 * what comes back is only what to tell whoever asked.
 */
public final class EventLauncher {

    /**
     * @param done       whether the event started (or stopped)
     * @param messageKey what to tell whoever asked, or {@code null} for nothing -
     *                   a Kill the King called off at once has already said why
     * @param event      the event's display name, for {@code %event%}
     */
    public record Result(boolean done, String messageKey, String event) {
    }

    private final EventModule module;

    public EventLauncher(EventModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /** @return the display name of the event with this id, whatever its engine */
    public Optional<String> displayName(String id) {
        return module.getSettings().find(id).map(CaptureEventDefinition::displayName)
                .or(() -> king(id).map(KingEventDefinition::displayName))
                .or(() -> conquest(id).map(ConquestDefinition::displayName))
                .or(() -> core(id).map(CoreEventDefinition::displayName))
                .or(() -> slide(id).map(SlideDefinition::displayName))
                .or(() -> totem(id).map(TotemDefinition::displayName));
    }

    public Result start(String id) {
        return run(id, true);
    }

    public Result stop(String id) {
        return run(id, false);
    }

    private Result run(String id, boolean starting) {
        EventManager manager = module.getManager();
        if (manager == null || !module.getSettings().enabled()) {
            return new Result(false, EventMessages.DISABLED, id);
        }
        Optional<CaptureEventDefinition> capture = module.getSettings().find(id);
        if (capture.isPresent()) {
            return capture(manager, capture.get(), starting);
        }
        Optional<KingEventDefinition> king = king(id);
        if (king.isPresent()) {
            return king(king.get(), starting);
        }
        Optional<ConquestDefinition> conquest = conquest(id);
        if (conquest.isPresent()) {
            return conquest(conquest.get(), starting);
        }
        Optional<CoreEventDefinition> core = core(id);
        if (core.isPresent()) {
            return core(core.get(), starting);
        }
        Optional<SlideDefinition> slide = slide(id);
        if (slide.isPresent()) {
            return slide(slide.get(), starting);
        }
        Optional<TotemDefinition> totem = totem(id);
        if (totem.isPresent()) {
            return totem(totem.get(), starting);
        }
        return new Result(false, EventMessages.UNKNOWN_EVENT, id);
    }

    private Result capture(EventManager manager, CaptureEventDefinition definition, boolean starting) {
        Optional<EventUpdate> update = starting ? manager.start(definition) : manager.stop(definition.id());
        if (update.isEmpty()) {
            return new Result(false, starting ? EventMessages.ALREADY_ACTIVE : EventMessages.NOT_ACTIVE,
                    definition.displayName());
        }
        module.broadcast(update.get().messageKey(), update.get().placeholders());
        return done(starting, definition.displayName());
    }

    /** Starting only opens it: the King is drawn and announced a few ticks later. */
    private Result king(KingEventDefinition definition, boolean starting) {
        KingEventController king = module.getKing();
        Optional<KingRun> current = king.getManager().getCurrent();
        if (starting) {
            if (current.isPresent()) {
                // Only one King at a time; say which event holds the slot.
                boolean same = current.get().getDefinition().id().equalsIgnoreCase(definition.id());
                return new Result(false, same ? EventMessages.ALREADY_ACTIVE : KingMessages.ALREADY_RUNNING,
                        current.get().getDefinition().displayName());
            }
            king.start(definition);
            // Called off at once - no warzone, a ceiling, too few players: the reason
            // has just been broadcast, and "Started" after it would contradict it.
            return king.getManager().getCurrent().isPresent()
                    ? done(true, definition.displayName())
                    : new Result(false, null, definition.displayName());
        }
        if (current.filter(run -> run.getDefinition().id().equalsIgnoreCase(definition.id())).isEmpty()
                || !king.stop()) {
            return new Result(false, EventMessages.NOT_ACTIVE, definition.displayName());
        }
        return done(false, definition.displayName());
    }

    private Result conquest(ConquestDefinition definition, boolean starting) {
        ConquestController conquest = module.getConquest();
        Optional<ConquestRun> current = conquest.getManager().getCurrent();
        if (starting) {
            Optional<ConquestUpdate> started = conquest.getManager().start(definition);
            if (started.isEmpty()) {
                return busy(current.map(run -> run.getDefinition().id()), current.map(run -> run.getDefinition().displayName()),
                        definition.id(), definition.displayName(), ConquestMessages.ALREADY_RUNNING);
            }
            conquest.announce(started.get());
            return done(true, definition.displayName());
        }
        if (current.filter(run -> run.getDefinition().id().equalsIgnoreCase(definition.id())).isEmpty()) {
            return new Result(false, EventMessages.NOT_ACTIVE, definition.displayName());
        }
        conquest.getManager().stop().ifPresent(conquest::announce);
        return done(false, definition.displayName());
    }

    /** DTC and Last Break share one run slot: the refusal names whichever of the two runs. */
    private Result core(CoreEventDefinition definition, boolean starting) {
        CoreEventController core = module.getCore();
        Optional<CoreRun> current = core.getManager().getCurrent();
        if (starting) {
            Optional<CoreUpdate> started = core.getManager().start(definition);
            if (started.isEmpty()) {
                return busy(current.map(run -> run.getDefinition().id()), current.map(run -> run.getDefinition().displayName()),
                        definition.id(), definition.displayName(), CoreMessages.CORE_ALREADY_RUNNING);
            }
            core.announce(started.get());
            return done(true, definition.displayName());
        }
        if (current.filter(run -> run.getDefinition().id().equalsIgnoreCase(definition.id())).isEmpty()) {
            return new Result(false, EventMessages.NOT_ACTIVE, definition.displayName());
        }
        core.getManager().stop().ifPresent(core::announce);
        return done(false, definition.displayName());
    }

    private Result slide(SlideDefinition definition, boolean starting) {
        SlideController slide = module.getSlide();
        Optional<SlideRun> current = slide.getManager().getCurrent();
        if (starting) {
            Optional<SlideUpdate> started = slide.getManager().start(definition);
            if (started.isEmpty()) {
                return busy(current.map(run -> run.getDefinition().id()), current.map(run -> run.getDefinition().displayName()),
                        definition.id(), definition.displayName(), SlideMessages.ALREADY_RUNNING);
            }
            slide.announce(started.get());
            return done(true, definition.displayName());
        }
        if (current.filter(run -> run.getDefinition().id().equalsIgnoreCase(definition.id())).isEmpty()) {
            return new Result(false, EventMessages.NOT_ACTIVE, definition.displayName());
        }
        slide.getManager().stop().ifPresent(slide::announce);
        return done(false, definition.displayName());
    }

    private Result totem(TotemDefinition definition, boolean starting) {
        TotemController totem = module.getTotem();
        Optional<TotemRun> current = totem.getManager().getCurrent();
        if (starting) {
            Optional<TotemUpdate> started = totem.getManager().start(definition);
            if (started.isEmpty()) {
                return busy(current.map(run -> run.getDefinition().id()), current.map(run -> run.getDefinition().displayName()),
                        definition.id(), definition.displayName(), TotemMessages.ALREADY_RUNNING);
            }
            totem.announce(started.get());
            return done(true, definition.displayName());
        }
        if (current.filter(run -> run.getDefinition().id().equalsIgnoreCase(definition.id())).isEmpty()) {
            return new Result(false, EventMessages.NOT_ACTIVE, definition.displayName());
        }
        totem.getManager().stop().ifPresent(totem::announce);
        return done(false, definition.displayName());
    }

    /** A refused start: this very event runs already, or another holds its engine's one slot. */
    private static Result busy(Optional<String> runningId, Optional<String> runningName, String id, String name,
                               String otherKey) {
        boolean same = runningId.filter(id::equalsIgnoreCase).isPresent();
        return new Result(false, same ? EventMessages.ALREADY_ACTIVE : otherKey, runningName.orElse(name));
    }

    private static Result done(boolean starting, String name) {
        return new Result(true, starting ? EventMessages.ADMIN_STARTED : EventMessages.ADMIN_STOPPED, name);
    }

    private Optional<KingEventDefinition> king(String id) {
        return module.getKing() == null ? Optional.empty() : module.getKing().getSettings().find(id);
    }

    private Optional<ConquestDefinition> conquest(String id) {
        return module.getConquest() == null ? Optional.empty() : module.getConquest().getSettings().find(id);
    }

    private Optional<CoreEventDefinition> core(String id) {
        return module.getCore() == null ? Optional.empty() : module.getCore().getSettings().find(id);
    }

    private Optional<SlideDefinition> slide(String id) {
        return module.getSlide() == null ? Optional.empty() : module.getSlide().getSettings().find(id);
    }

    private Optional<TotemDefinition> totem(String id) {
        return module.getTotem() == null ? Optional.empty() : module.getTotem().getSettings().find(id);
    }
}
