package com.lawkeys.hcfcore.claim.subclaim;

import com.lawkeys.hcfcore.team.TeamRole;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A subclaim: a container inside a team's land that only some of its members may
 * open - the classic HCF {@code [Subclaim]} sign, placed on a chest with the names
 * allowed on its other lines (the project owner's request, 23/09/2026). Without it,
 * any member empties the chest another member filled.
 *
 * <p>It restricts the owning team's own members only: land is protected from
 * everybody else by the claim, and a raid opens it as it opens the rest.
 *
 * <p>Pure Java: the sign's lines and the rule, unit-tested.
 *
 * @param names the players allowed, as written on the sign, lower-cased
 */
public record Subclaim(Set<String> names) {

    public Subclaim {
        names = Set.copyOf(Objects.requireNonNull(names, "names"));
    }

    /** {@code claims.yml}, {@code subclaims}. */
    public record Rules(boolean enabled, String header, TeamRole openAny) {

        public Rules {
            Objects.requireNonNull(header, "header");
        }

        public static Rules defaults() {
            return new Rules(true, "[Subclaim]", TeamRole.CO_LEADER);
        }
    }

    /**
     * @param lines the sign's four lines, colour codes already stripped
     * @return the subclaim the sign declares, or empty when it is not one
     */
    public static Optional<Subclaim> read(List<String> lines, String header) {
        if (lines.isEmpty() || !header.equalsIgnoreCase(lines.get(0).trim())) {
            return Optional.empty();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String name = line == null ? "" : line.trim();
            if (!name.isEmpty()) {
                names.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return Optional.of(new Subclaim(names));
    }

    /**
     * @param role    the player's role in the team that owns the land
     * @param openAny the lowest role that opens every subclaim of its team - the
     *                co-leaders and the leader, as shipped; {@code null} for nobody
     * @return whether this member may open or break it
     */
    public boolean allows(String playerName, TeamRole role, TeamRole openAny) {
        if (openAny != null && role != null && role.isAtLeast(openAny)) {
            return true;
        }
        return playerName != null && names.contains(playerName.toLowerCase(Locale.ROOT));
    }

    /** @return whether any of several signs on one container lets this member in */
    public static boolean anyAllows(List<Subclaim> signs, String playerName, TeamRole role, TeamRole openAny) {
        if (signs.isEmpty()) {
            return true;
        }
        for (Subclaim sign : signs) {
            if (sign.allows(playerName, role, openAny)) {
                return true;
            }
        }
        return false;
    }
}
