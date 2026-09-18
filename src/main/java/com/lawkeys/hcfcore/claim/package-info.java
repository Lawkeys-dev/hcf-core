/**
 * Territory system: chunk ownership, protection, HQ and secondary base, and the
 * {@code /team} territory subcommands. See FEATURES.md sections 2 and 3.
 *
 * <p><strong>The rule that shapes this package</strong> (FEATURES.md section 3):
 * who owns a chunk is persistent and is never changed by raiding, while whether
 * that owner is currently protected is a live state derived from their DTR. So
 * {@code ClaimManager} refuses to claim any chunk that already has an owner -
 * including a raidable one - and derives build permission from
 * {@link com.lawkeys.hcfcore.claim.RaidabilityPolicy} rather than from any notion
 * of "free land". A team regains its protection the moment its DTR rises, with
 * nothing re-claimed.
 *
 * <p>The {@code dtr/} module supplies the real raid policy; until it exists,
 * {@code RaidabilityPolicy.NEVER} keeps everything protected.
 *
 * <p>{@code /team lockclaim} closes a team's land to every non-member, during SOTW
 * only - the project owner's rule of 12/09/2026. {@link com.lawkeys.hcfcore.claim.LockWindow}
 * is the question "may claims be locked now", answered by {@code phase/}, and
 * {@code ClaimLockListener} keeps non-members out.
 */
package com.lawkeys.hcfcore.claim;
