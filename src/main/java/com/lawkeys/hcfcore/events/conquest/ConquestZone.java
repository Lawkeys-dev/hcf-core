package com.lawkeys.hcfcore.events.conquest;

import com.lawkeys.hcfcore.util.Cuboid;

import java.util.Objects;

/**
 * One of a Conquest's zones - the classic four are Red, Blue, Green and Yellow.
 *
 * @param displayName what players see, colour codes included
 */
public record ConquestZone(String id, String displayName, Cuboid area) {

    public ConquestZone {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(area, "area");
    }
}
