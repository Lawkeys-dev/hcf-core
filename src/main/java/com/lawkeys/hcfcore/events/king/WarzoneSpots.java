package com.lawkeys.hcfcore.events.king;

import com.lawkeys.hcfcore.claim.ClaimSettings.WarzoneRules.Area;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Where in the warzone a new King may be sent: the geometry half, which needs no
 * server. Whether the ground there is safe to stand on is the server's question.
 */
public final class WarzoneSpots {

    private WarzoneSpots() {
    }

    /**
     * @return a block column {@code {x, z}} drawn uniformly inside the warzone's
     *         square, bounds included
     */
    public static int[] randomColumn(Area area, RandomGenerator random) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(random, "random");
        int x = area.centerX() - area.radius() + random.nextInt(2 * area.radius() + 1);
        int z = area.centerZ() - area.radius() + random.nextInt(2 * area.radius() + 1);
        return new int[] {x, z};
    }
}
