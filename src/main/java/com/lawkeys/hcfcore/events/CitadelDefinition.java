package com.lawkeys.hcfcore.events;

import java.util.Objects;

/**
 * A Citadel: a KOTH with a much longer hold, whose capture zone stands inside a large
 * claimed area - the Citadel itself - where {@link CitadelRules} refuse pearls,
 * partner items and the other ways out. The fight there is decided by teams and
 * classes, and nobody leaves it by throwing an item.
 *
 * <p>Two zones, then. The zone to hold is the capture event's cuboid, run by the
 * ordinary capture engine like any KOTH ({@link CaptureEventDefinition}, found in
 * {@link EventSettings#definitions()} under the same id). The Citadel is server land:
 * the chunks of a server team, claimed with {@code /team createsystem} and
 * {@code /team forceclaim}, which this names - so it is protected from building,
 * shown on {@code /team map} and announced at its border like any server land.
 *
 * @param eventId the capture event's id
 * @param claim   the name of the server team whose land is the Citadel
 */
public record CitadelDefinition(String eventId, String claim, CitadelRules rules) {

    public CitadelDefinition {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(rules, "rules");
    }

    /** @return whether a team of this name owns the Citadel's land - names are matched without case */
    public boolean isClaimedBy(String teamName) {
        return teamName != null && claim.equalsIgnoreCase(teamName.trim());
    }
}
