/**
 * Staff and moderation tools (FEATURES.md section 8).
 *
 * <p><strong>What is here.</strong> Staff mode - a toolbar of items bound to
 * commands, with the staff member's own inventory held safely in the database
 * meanwhile - plus vanish, the staff channel, the staff build toggle, the staff
 * teleport family, broadcast, clear chat, and the dimension rosters.
 *
 * <p>Then freeze - with the moderation ban that follows running from one - invsee,
 * and the archive of inventories captured at death behind {@code /lastinv}.
 *
 * <p>Then reports and requests, one queue of tickets ({@code ticket/}) with a
 * management menu, and strikes ({@code strike/}). FEATURES.md forbade building the
 * strike system before its scale was specified, so it is a mechanism with the scale
 * left blank: the validity period is configuration, the sanctions are a ladder of
 * console commands in {@code staff.yml}, and that ladder ships empty - a strike
 * records itself and nothing more until an operator writes one.
 *
 * <p>The shipped toolbar binds only commands this module provides, since a slot
 * naming a command that does not exist would look like a tool and answer "unknown
 * command" - the rule that kept Inspect and Freeze out of the first version and let
 * them in once they existed.
 *
 * <p><strong>The one thing worth losing sleep over is the inventory.</strong>
 * Entering staff mode takes a player's whole inventory away. Everything in
 * {@link com.lawkeys.hcfcore.staff.StaffStashes} and
 * {@link com.lawkeys.hcfcore.staff.StaffModule} is arranged so it is only ever in
 * one of two places - their hands, or the database - and never in neither. It is
 * written before the inventory is cleared, restored before the mode flag is
 * dropped, given back at the next login if a crash got in the way, and never
 * overwritten by a second stash.
 *
 * <p>Session state (who is in the mode, who is hidden, who has the channel on, who
 * is frozen) is memory-only on purpose, like a combat tag: a restart ends it. What
 * outlives a restart is what would be unfair or useless to lose - a held inventory,
 * a moderation ban, the death archive, the ticket queue and the strike record.
 *
 * <p>That archive is the one store in the project that is <strong>not</strong>
 * cached: it is a record read on demand, not live state, and holding every
 * player's last few inventories in heap to serve a command used a few times a day
 * would be the wrong trade. See {@link com.lawkeys.hcfcore.staff.LastInventoryStore}.
 */
package com.lawkeys.hcfcore.staff;
