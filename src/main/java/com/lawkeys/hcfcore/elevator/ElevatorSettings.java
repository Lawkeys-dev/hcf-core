package com.lawkeys.hcfcore.elevator;

import java.util.Objects;

/**
 * {@code elevators.yml}.
 *
 * @param header            what a sign's first line says, brackets included: {@code [Elevator]}
 * @param upWord            the second line of a sign going up
 * @param downWord          the second line of a sign going down
 * @param maxDistance       how far a sign looks for the next floor; {@code 0} for the whole column
 * @param cooldownSeconds   wait between two rides
 * @param ownTerritoryOnly  whether a sign works only on its user's team's land
 * @param blockedInCombat   whether a combat tag refuses a ride
 */
public record ElevatorSettings(boolean enabled, String header, String upWord, String downWord, int maxDistance,
                               long cooldownSeconds, boolean ownTerritoryOnly, boolean blockedInCombat) {

    public ElevatorSettings {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(upWord, "upWord");
        Objects.requireNonNull(downWord, "downWord");
        maxDistance = Math.max(0, maxDistance);
        cooldownSeconds = Math.max(0L, cooldownSeconds);
    }

    /** As shipped. */
    public static ElevatorSettings defaults() {
        return new ElevatorSettings(true, "[Elevator]", "Up", "Down", 0, 1L, false, false);
    }
}
