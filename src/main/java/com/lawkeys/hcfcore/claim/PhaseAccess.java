package com.lawkeys.hcfcore.claim;

import com.lawkeys.hcfcore.team.Team;

/**
 * What a map phase opens on somebody else's land, whatever their DTR (the owner's
 * rules of 28/09/2026): during EOTW, building and breaking everywhere - server land
 * too, spawn aside; during the Purge, only using the blocks of enemy claims - doors,
 * chests, buttons - never building or breaking.
 *
 * <p>A seam like {@link RaidabilityPolicy}, filled by {@code phase/}; until then
 * {@link #NONE} opens nothing. A team raidable by its DTR is open to everything as
 * ever - that is the raidability policy's, not this.
 */
public interface PhaseAccess {

    /** @return whether anybody may build and break on this team's land now */
    boolean openToBuilding(Team owner);

    /** @return whether anybody may use this team's blocks now, without building */
    boolean openToUse(Team owner);

    PhaseAccess NONE = new PhaseAccess() {
        @Override
        public boolean openToBuilding(Team owner) {
            return false;
        }

        @Override
        public boolean openToUse(Team owner) {
            return false;
        }
    };
}
