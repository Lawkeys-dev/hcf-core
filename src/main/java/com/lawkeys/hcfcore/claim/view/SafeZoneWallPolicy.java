package com.lawkeys.hcfcore.claim.view;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;

/**
 * Who sees a wall around a safe zone, and what it looks like - the seam the
 * {@code pvp/} module fills so that a player in combat sees spawn closed to them
 * (the project owner's choice, 22/09/2026). The claim module draws it; the rule of
 * who is refused belongs to combat, not to territory.
 *
 * <p>Without the PvP module, nobody sees one: {@link #NONE}.
 */
@FunctionalInterface
public interface SafeZoneWallPolicy {

    /**
     * @param material     the block it is drawn in
     * @param radiusBlocks the border's air blocks within this distance of the player
     *                     are drawn - a ball around them, not a sheet to the sky
     */
    record Wall(String material, int radiusBlocks) {

        public Wall {
            Objects.requireNonNull(material, "material");
            radiusBlocks = Math.max(1, Math.min(32, radiusBlocks));
        }
    }

    SafeZoneWallPolicy NONE = player -> Optional.empty();

    /** @return the wall to show this player around safe-zone land, or empty for none */
    Optional<Wall> wallFor(Player player);
}
