package com.lawkeys.hcfcore.stats;

import java.util.List;

/**
 * The teams' leaderboards, for {@code /leaderboard}: the stats module keeps players'
 * numbers, the team module knows who is in which team. A seam filled at startup;
 * until then there are no team boards.
 */
public interface TeamBoards {

    /** One line of a board: a team and what it scored, written as it is shown. */
    record Row(String name, String value) {
    }

    /** @return the teams with the most points, best first */
    List<Row> byPoints(int limit);

    /** @return the teams whose members have killed the most, best first */
    List<Row> byKills(int limit);

    TeamBoards NONE = new TeamBoards() {
        @Override
        public List<Row> byPoints(int limit) {
            return List.of();
        }

        @Override
        public List<Row> byKills(int limit) {
            return List.of();
        }
    };
}
