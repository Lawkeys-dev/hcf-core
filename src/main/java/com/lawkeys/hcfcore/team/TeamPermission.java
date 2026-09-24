package com.lawkeys.hcfcore.team;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One permission a team may give to a role of its choice in {@code /team settings}:
 * the team's own actions ({@link TeamAction}), and those other modules add -
 * {@code claim/}'s claiming, HQ and subclaims.
 *
 * @param key        the permission's name: the key of {@code required-roles} it
 *                   stands for, the key it is stored and locked under, and its name
 *                   in {@code team.settings.permissions.<key>} of the language file
 * @param icon       the item it shows as, unless {@code team-settings.icons} says otherwise
 * @param serverRole the server's role for it, read at each use so a reload counts
 */
public record TeamPermission(String key, String icon, Supplier<TeamRole> serverRole) {

    public TeamPermission {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(serverRole, "serverRole");
    }
}
