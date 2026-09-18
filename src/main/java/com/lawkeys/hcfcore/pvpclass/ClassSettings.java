package com.lawkeys.hcfcore.pvpclass;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of {@code classes.yml}.
 *
 * @param warmupSeconds        how long a full set must be worn before its class turns on
 * @param abilitiesInSafeZones whether held and click effects work while their user
 *                             stands in a safe zone. Passive effects always do
 * @param classes              in file order: the first class whose set matches wins
 */
public record ClassSettings(boolean enabled, long warmupSeconds, boolean abilitiesInSafeZones,
                            List<PvpClass> classes) {

    public ClassSettings {
        warmupSeconds = Math.max(0L, warmupSeconds);
        classes = List.copyOf(classes);
    }

    public static ClassSettings defaults() {
        return new ClassSettings(true, 10, false, List.of());
    }

    /** @return the class whose armour set these four pieces are, helmet to boots */
    public Optional<PvpClass> matching(List<String> worn) {
        return classes.stream().filter(pvpClass -> pvpClass.isWornAs(worn)).findFirst();
    }

    public Optional<PvpClass> find(String id) {
        Objects.requireNonNull(id, "id");
        return classes.stream().filter(pvpClass -> pvpClass.id().equalsIgnoreCase(id.trim())).findFirst();
    }
}
