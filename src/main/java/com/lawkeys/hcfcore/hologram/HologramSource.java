package com.lawkeys.hcfcore.hologram;

import java.util.List;
import java.util.Objects;

/**
 * Holograms drawn from code rather than stored: another module says where they
 * stand and what they read, and this module draws them - redrawn every second, taken
 * down when they are no longer listed.
 *
 * <p>A seam (ARCHITECTURE.md section 14): {@code hologram/} is enabled after the
 * modules that answer it, so it declares the question and they register an answer -
 * {@code events/} for its capture zones. Nothing here knows what a zone is.
 */
@FunctionalInterface
public interface HologramSource {

    /**
     * One hologram, as it stands now.
     *
     * @param id    unique among every source's holograms; never a stored hologram's id
     *              (those cannot contain {@code :}, so a source prefixes its own)
     * @param lines the text, top first, colour codes still as {@code &}
     */
    record Placed(String id, String world, double x, double y, double z, List<String> lines) {

        public Placed {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(world, "world");
            lines = List.copyOf(lines);
        }

        public boolean sameSpotAs(Placed other) {
            return other != null && world.equals(other.world) && x == other.x && y == other.y && z == other.z;
        }
    }

    /** @return every hologram this source wants drawn now; called on the main thread */
    List<Placed> current();
}
