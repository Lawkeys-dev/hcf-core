/**
 * Player statistics: kills, deaths, killstreaks and playtime (FEATURES.md section 9).
 *
 * <p>The foundation several other features stand on - the chat format shows a kill
 * count, the scoreboard shows a streak, the leaderboards sort these rows, and
 * {@code killstreak/} hangs rewards off the streak this module reports. It depends
 * on nothing itself, which is why it starts before all of them.
 *
 * <p><strong>Playtime is banked, not derived.</strong> A session's length is added
 * when the session ends; the session in progress is added on the way out of
 * {@link com.lawkeys.hcfcore.stats.PlayerStats#playtimeSeconds}. Deriving it from a
 * first-join date would count the years somebody was not playing, and writing the
 * live total mid-session would count that session again when it ended.
 */
package com.lawkeys.hcfcore.stats;
