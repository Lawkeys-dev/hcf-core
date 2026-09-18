/**
 * Kits and refill signs (FEATURES.md section 7). Partner items are the ability
 * module's ({@code ability/}, {@code abilities.yml}).
 *
 * <p><strong>Kits are made by saving an inventory, not by writing YAML.</strong>
 * {@code /kit create <id>} stores the staff member's own inventory - enchanted
 * armour, brewed potions, named items and all - as opaque bytes. Describing a full
 * HCF loadout by hand in configuration is a job nobody finishes correctly, so there
 * is no kit list in {@code kits.yml} to get wrong.
 *
 * <p>A kit cooldown is stored as the instant it ends, so it keeps running while the
 * server is off; a daily kit would otherwise be worth nothing on a server that
 * restarts nightly.
 */
package com.lawkeys.hcfcore.kit;
