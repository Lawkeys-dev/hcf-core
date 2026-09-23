package com.lawkeys.hcfcore.pvp;

import java.util.UUID;

/**
 * A player everybody may fight, teammates and allies included, and who may fight
 * everybody back - the King of a solo Kill the King (the project owner's rule,
 * 23/09/2026). A seam with a neutral default: nobody, until {@code events/}
 * installs one.
 */
@FunctionalInterface
public interface OpenTarget {

    OpenTarget NOBODY = player -> false;

    boolean isOpen(UUID player);
}
