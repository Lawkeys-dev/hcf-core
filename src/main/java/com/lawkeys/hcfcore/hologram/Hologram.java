package com.lawkeys.hcfcore.hologram;

import com.lawkeys.hcfcore.util.ChunkPosition;

import java.util.List;
import java.util.Objects;

/**
 * One floating text: where it stands, and its lines.
 *
 * @param lines colour codes and placeholders included; see {@link HologramText}
 */
public record Hologram(String id, String world, double x, double y, double z, List<String> lines) {

    public Hologram {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(world, "world");
        lines = List.copyOf(lines);
    }

    public Hologram withLines(List<String> replacement) {
        return new Hologram(id, world, x, y, z, replacement);
    }

    public Hologram at(String newWorld, double newX, double newY, double newZ) {
        return new Hologram(id, newWorld, newX, newY, newZ, lines);
    }

    /** @return the chunk it stands in, to know when its world has loaded it */
    public int chunkX() {
        return ChunkPosition.toChunk((int) Math.floor(x));
    }

    public int chunkZ() {
        return ChunkPosition.toChunk((int) Math.floor(z));
    }
}
