/**
 * SOTW and EOTW: the start and the end of a map (FEATURES.md section 6).
 *
 * <p>Not an event in the capture sense - nothing is held and nobody wins - but a
 * phase that changes the rules of other modules for a while: combat, deathbans
 * and DTR in SOTW; raids, claims and deathbans in EOTW. Each of those modules
 * declared the question it needed answered as a seam, and
 * {@link com.lawkeys.hcfcore.phase.PhaseModule} plugs the answers of
 * {@link com.lawkeys.hcfcore.phase.PhaseManager} in at startup
 * (ARCHITECTURE.md section 14).
 *
 * <p>The phase is four instants in {@code hcf_map_phase}, plus the players who
 * enabled PvP during the current SOTW: it survives a restart, and a SOTW ends when
 * its time is up however often the server went down before.
 */
package com.lawkeys.hcfcore.phase;
