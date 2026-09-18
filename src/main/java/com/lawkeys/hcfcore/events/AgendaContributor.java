package com.lawkeys.hcfcore.events;

import java.util.List;

/**
 * Lets a module outside {@code events/} put its own scheduled things in
 * {@code /events}.
 *
 * <p>FEATURES.md section 10 asks for <em>one</em> agenda command listing what is
 * coming - "KOTH, Citadel, Conquest, Mountain refill" - so players can plan.
 * Those do not all belong to the same family: a mountain refill is family B of
 * ARCHITECTURE.md section 9 and shares no engine with a capture event. This is
 * the seam that reconciles the two, in the same spirit as
 * {@code claim.RaidabilityPolicy}: the module that owns the command declares the
 * question, and the module written later answers it at startup.
 *
 * <p>Read-only on purpose. Staff verbs stay on each family's own command, because
 * "start a KOTH" and "refill a mountain" are not the same act and pretending
 * otherwise is how the two families start bleeding into each other.
 */
@FunctionalInterface
public interface AgendaContributor {

    /** @return the lines to show, already ordered; an empty list when there is nothing */
    List<AgendaEntry> agendaEntries();
}
