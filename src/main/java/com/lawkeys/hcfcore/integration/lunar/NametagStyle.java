package com.lawkeys.hcfcore.integration.lunar;

import com.lawkeys.hcfcore.team.TeamRelation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The nametag a Lunar Client player sees above another: a team line and a name line,
 * coloured by how the two relate.
 *
 * <p>Pure Java, as {@code apollo.yml} describes it: {@code team-line} and
 * {@code name-line} are templates with {@code %color%}, {@code %team%},
 * {@code %dtr%} and {@code %player%}; the team line is left out for a player with no
 * team. A focused player - or a member of a focused team - takes the focus colour,
 * whatever else they are, except to their own team.
 *
 * @param teamLine template of the upper line; blank for none
 * @param nameLine template of the name line
 * @param colors   colour code per relation, {@code &} codes
 */
public record NametagStyle(String teamLine, String nameLine, Map<Relation, String> colors) {

    /** How the player under the nametag relates to the one looking at it. */
    public enum Relation {
        SELF, ALLY, ENEMY, FOCUS, NEUTRAL
    }

    public NametagStyle {
        Objects.requireNonNull(teamLine, "teamLine");
        Objects.requireNonNull(nameLine, "nameLine");
        Map<Relation, String> copy = new EnumMap<>(Relation.class);
        copy.putAll(Objects.requireNonNull(colors, "colors"));
        colors = Map.copyOf(copy);
    }

    /**
     * @param base    the teams' relation, from the viewer's side
     * @param focused whether the viewer's team focuses this player or their team
     */
    public static Relation relation(TeamRelation base, boolean focused) {
        if (base == TeamRelation.SELF) {
            return Relation.SELF;
        }
        if (focused) {
            return Relation.FOCUS;
        }
        return switch (base) {
            case ALLY -> Relation.ALLY;
            case ENEMY -> Relation.ENEMY;
            default -> Relation.NEUTRAL;
        };
    }

    /**
     * @param team the target's team name, or {@code null} for none
     * @param dtr  the team's DTR as it should read
     * @return the lines, top first, with {@code &} codes still untranslated
     */
    public List<String> lines(Relation relation, String team, String dtr, String player) {
        String color = colors.getOrDefault(relation, "");
        List<String> lines = new ArrayList<>(2);
        if (team != null && !teamLine.isBlank()) {
            lines.add(fill(teamLine, color, team, dtr, player));
        }
        lines.add(fill(nameLine, color, team == null ? "" : team, dtr, player));
        return lines;
    }

    private static String fill(String template, String color, String team, String dtr, String player) {
        return template.replace("%color%", color)
                .replace("%team%", team)
                .replace("%dtr%", dtr == null ? "" : dtr)
                .replace("%player%", player);
    }
}
