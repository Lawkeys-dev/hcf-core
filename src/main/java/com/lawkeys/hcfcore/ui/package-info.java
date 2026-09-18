/**
 * The scoreboard, the tab list and the leaderboards (FEATURES.md section 9).
 *
 * <p><strong>Written against the official Bukkit scoreboard API, not a packet
 * library.</strong> CONTRIBUTING.md's stack table suggests FastBoard "to avoid
 * reinventing the packet-based scoreboard"; nothing here reinvents packets. The two
 * problems such a library exists to solve are both solved by the documented API:
 * flicker, by writing text into team prefixes updated in place rather than clearing
 * the board each tick, and the red score numbers, by {@code Objective#numberFormat},
 * which Paper added for exactly this. One less shaded dependency.
 *
 * <p><strong>The board is a list of template lines, not a fixed layout.</strong>
 * Which numbers belong on a player's screen depends on the server - a kitmap has no
 * DTR, a server without events has no KOTH timer - so the lines live in
 * {@code ui.yml}. A line whose placeholders all resolve to nothing is dropped
 * rather than drawn blank, which is what makes a timer row appear only while its
 * timer runs, and what makes a missing module cost nothing.
 */
package com.lawkeys.hcfcore.ui;
