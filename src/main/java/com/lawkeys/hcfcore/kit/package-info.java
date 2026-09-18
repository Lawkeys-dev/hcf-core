/**
 * Kits, partner items and refill signs (FEATURES.md section 7).
 *
 * <p><strong>Kits are made by saving an inventory, not by writing YAML.</strong>
 * {@code /kit create <id>} stores the staff member's own inventory - enchanted
 * armour, brewed potions, named items and all - as opaque bytes. Describing a full
 * HCF loadout by hand in configuration is a job nobody finishes correctly, so there
 * is no kit list in {@code kits.yml} to get wrong.
 *
 * <p><strong>The ability list ships empty, on purpose.</strong> FEATURES.md calls
 * partner items a core feature and says their effects, triggers and cooldowns are
 * to be specified when they are built. The mechanism is here - an ability is an
 * item, a cooldown and a list of console commands, which can express any of them -
 * and the list is the operator's. Inventing one would be deciding gameplay on their
 * behalf, the same reason {@code points-per-capture} defaults to zero.
 *
 * <p>A kit cooldown is stored as the instant it ends, so it keeps running while the
 * server is off; a daily kit would otherwise be worth nothing on a server that
 * restarts nightly. An ability cooldown is memory-only, because it exists to stop a
 * partner item being spammed inside one fight, and a fight does not survive a
 * restart any more than a combat tag does.
 */
package com.lawkeys.hcfcore.kit;
