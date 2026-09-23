package com.lawkeys.hcfcore.economy.reward;

import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamManager;
import com.lawkeys.hcfcore.team.TeamRelation;

import java.util.UUID;

/** Whether two players fight on the same side: one team, or allied teams. */
public final class Sides {

    private Sides() {
    }

    /**
     * @return {@code true} for teammates and allies - whose kills of each other pay
     *         nothing, since paying them would pay them to farm each other
     */
    public static boolean same(TeamManager teams, UUID a, UUID b) {
        if (teams == null) {
            return false;
        }
        Team first = teams.getTeamOf(a).orElse(null);
        Team second = teams.getTeamOf(b).orElse(null);
        TeamRelation relation = teams.getRelation(first, second);
        return relation == TeamRelation.SELF || relation == TeamRelation.ALLY;
    }
}
