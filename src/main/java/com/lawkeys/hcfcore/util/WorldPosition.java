package com.lawkeys.hcfcore.util;

import java.util.Objects;

/**
 * An immutable, Bukkit-free representation of a position in a world.
 *
 * <p>Deliberately does <em>not</em> use {@code org.bukkit.Location}: this type is
 * stored in the database, held by pure-logic managers and covered by unit tests
 * that run without a server (see CONTRIBUTING.md section 3, "Tests"). Conversion to and
 * from a Bukkit {@code Location} belongs to the command/listener layer, which is
 * the only part of the codebase allowed to depend on the server API for this.
 *
 * @param world world name, as returned by {@code World#getName()}
 * @param yaw   horizontal rotation, kept so the same type can later back the claim
 *              module's HQ/base points without a schema change
 */
public record WorldPosition(String world, double x, double y, double z, float yaw, float pitch) {

    public WorldPosition {
        Objects.requireNonNull(world, "world");
    }

    public static WorldPosition of(String world, double x, double y, double z) {
        return new WorldPosition(world, x, y, z, 0.0f, 0.0f);
    }
}
