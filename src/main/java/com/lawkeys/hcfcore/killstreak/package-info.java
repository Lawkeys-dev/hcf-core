/**
 * Rewards for consecutive kills (FEATURES.md section 7).
 *
 * <p>Almost nothing of its own. The streak is counted by {@code stats/}, which had
 * to store it anyway, and this module only answers whether anything happens at that
 * number - installed through that module's {@code KillstreakObserver} seam, so the
 * two share nothing but an integer.
 *
 * <p>A reward is a broadcast and a list of console commands, because that is the
 * only shape that can express "give them a kit", "drop a crate" or "run something
 * from another plugin" without this module knowing what any of those are. The table
 * ships <strong>empty</strong>: FEATURES.md leaves it to the operator, and inventing
 * one would be deciding gameplay on their behalf.
 */
package com.lawkeys.hcfcore.killstreak;
