package com.lawkeys.hcfcore.events.king;

/**
 * {@code kill-the-king.<id>.reign:} - what the crown asks of the King, and what
 * dying with it costs them.
 *
 * <p>By default the King's death is the event's, not a real one: the kit is gone
 * with the reign, nobody picks it up, and the King's team loses no DTR and the
 * King is not deathbanned. Their own items were put aside when they were crowned
 * and come back at the respawn, as ever.
 *
 * @param lockArmour    the King cannot take off the kit's armour - no click, drag,
 *                      key swap or right-click swap with another piece
 * @param dropKit       the kit falls at the King's death, as loot for the killer
 *                      (loot protection, {@code pvp.yml}); otherwise it vanishes. What
 *                      the King picked up during the reign falls either way
 * @param deathCostsDtr the King's death takes DTR from their team, as any death
 * @param deathban      the King's death deathbans them, as any death
 */
public record ReignRules(boolean lockArmour, boolean dropKit, boolean deathCostsDtr, boolean deathban) {

    public static ReignRules defaults() {
        return new ReignRules(true, false, false, false);
    }
}
