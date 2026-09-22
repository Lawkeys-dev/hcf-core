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
     * @param material      the block it is drawn in
     * @param widthBlocks   how far along the border to draw, each way from the player
     * @param topY          the level it rises to from the ground
     * @param minimumHeight what it gets where the ground is already above {@code topY}
     */
    record Wall(String material, int widthBlocks, int topY, int minimumHeight) {

        public Wall {
            Objects.requireNonNull(material, "material");
            widthBlocks = Math.max(1, Math.min(128, widthBlocks));
            minimumHeight = Math.max(1, Math.min(64, minimumHeight));
        }
    }

    SafeZoneWallPolicy NONE = player -> Optional.empty();

    /** @return the wall to show this player around safe-zone land, or empty for none */
    Optional<Wall> wallFor(Player player);
}
