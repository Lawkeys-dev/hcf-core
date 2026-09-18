package com.lawkeys.hcfcore.kit;

import java.util.Objects;

/**
 * One kit: a saved inventory, and the rules for handing it out.
 *
 * <p><strong>The contents are opaque bytes.</strong> A kit is created by saving a
 * staff member's own inventory - {@code /kit create} - rather than by writing item
 * definitions into YAML, because a full HCF loadout is armour with enchantments,
 * potions with effects and durations, and named items, and describing that in
 * configuration by hand is a job nobody finishes correctly. Keeping the bytes out
 * of this record is also what lets the rules below be tested without a server.
 *
 * @param id          lower-case identifier, what {@code /kit <id>} takes
 * @param displayName shown to players, colour codes allowed
 * @param permission  node required, or {@code null} when anybody may take it
 * @param cooldownSeconds  wait between uses; {@code 0} means no wait at all, which
 *                         is what a kitmap wants
 * @param contents    the saved inventory, serialized by the server layer
 */
public record Kit(String id, String displayName, String permission,
                  long cooldownSeconds, byte[] contents) {

    public Kit {
        Objects.requireNonNull(id, "id");
        id = id.toLowerCase(java.util.Locale.ROOT);
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        cooldownSeconds = Math.max(0L, cooldownSeconds);
        contents = Objects.requireNonNull(contents, "contents").clone();
    }

    /** @return a copy, so a caller cannot change what the kit holds */
    @Override
    public byte[] contents() {
        return contents.clone();
    }

    /** @return whether this player's permissions let them take it */
    public boolean isAllowed(java.util.function.Predicate<String> hasPermission) {
        return permission == null || permission.isBlank() || hasPermission.test(permission);
    }
}
