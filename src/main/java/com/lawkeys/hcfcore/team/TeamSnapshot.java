package com.lawkeys.hcfcore.team;

import com.lawkeys.hcfcore.util.WorldPosition;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A flat, immutable copy of everything about a team that is persisted.
 *
 * <p>This is the only shape a {@link TeamStore} ever sees. It exists so the
 * database layer can read and rebuild a team without being handed the model's
 * mutators - those stay package-private, which is what guarantees that every
 * rule goes through {@link TeamManager} (CONTRIBUTING.md section 3).
 *
 * <p>Runtime-only state is deliberately excluded: pending alliance offers, focus
 * markers, chat channels and invitations all reset on restart by design.
 *
 * @param systemZone     safe or combat for a system team, normalised on
 *                       construction: {@code null} for a player team, and
 *                       {@link SystemZone#SAFE} for a system team stored before the
 *                       choice existed
 * @param rallyExpiresAt epoch millis, or {@code 0} when the rally never expires
 */
public record TeamSnapshot(
        UUID id,
        String name,
        TeamType type,
        SystemZone systemZone,
        UUID leader,
        long createdAt,
        double balance,
        long points,
        int kothCaptures,
        WorldPosition rally,
        long rallyExpiresAt,
        Map<UUID, TeamRole> members,
        Set<UUID> allies,
        Map<String, TeamRole> permissions,
        JoinMode joinMode,
        String description,
        String discord) {

    public TeamSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (!type.isSystem()) {
            systemZone = null;
        } else if (systemZone == null) {
            systemZone = SystemZone.SAFE;
        }
        members = Map.copyOf(Objects.requireNonNull(members, "members"));
        allies = Set.copyOf(Objects.requireNonNull(allies, "allies"));
        permissions = Map.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    /** A team with no settings of its own: the server's roles and join mode, nothing said. */
    public TeamSnapshot(UUID id, String name, TeamType type, SystemZone systemZone, UUID leader, long createdAt,
                        double balance, long points, int kothCaptures, WorldPosition rally, long rallyExpiresAt,
                        Map<UUID, TeamRole> members, Set<UUID> allies) {
        this(id, name, type, systemZone, leader, createdAt, balance, points, kothCaptures, rally, rallyExpiresAt,
                members, allies, Map.of(), null, null, null);
    }
}
