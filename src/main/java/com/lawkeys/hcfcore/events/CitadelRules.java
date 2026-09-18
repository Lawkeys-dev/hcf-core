package com.lawkeys.hcfcore.events;

/**
 * What a Citadel's claim refuses, at all times: the ways to escape a fight or to win
 * it with an item rather than with a team. Class abilities - a Bard's buffs, an
 * Archer's tag, a Rogue's backstab - are not partner items and stay allowed.
 *
 * @param enderPearls  throwing an ender pearl from inside the claim
 * @param partnerItems using a partner item (a kit ability) inside the claim
 * @param chorusFruit  eating a chorus fruit, or any other food that teleports
 * @param elytra       taking off with elytra - and gliding on into the claim
 * @param riptide      launching with a Riptide trident
 */
public record CitadelRules(boolean enderPearls, boolean partnerItems, boolean chorusFruit,
                           boolean elytra, boolean riptide) {

    /** Everything refused: the Citadel as designed. */
    public static final CitadelRules ALL = new CitadelRules(true, true, true, true, true);
}
