/**
 * The general utility commands of FEATURES.md section 10.
 *
 * <p>Nothing here is HCF-specific, which is why it is its own module and why it can
 * be switched off whole: most servers already run an essentials plugin, and two
 * plugins fighting over {@code /spawn} helps nobody.
 *
 * <p>The one idea worth knowing is shared by {@code /spawn} and {@code /logout}:
 * both are countdowns that any damage or movement cancels. Neither may be a way out
 * of a fight - and {@code /logout} exists precisely because closing the client
 * <em>is</em> one, which the combat tag answers by killing the player. A visible,
 * interruptible way to leave has to exist, or standing still and hoping is the only
 * safe option. The countdowns belong to the {@code warmup/} module, which
 * {@code claim/} uses too: the rules live in {@link com.lawkeys.hcfcore.warmup.Warmups},
 * pure and tested; moving means leaving the block, not turning on the spot.
 */
package com.lawkeys.hcfcore.general;
