/**
 * Kill the King (FEATURES.md section 6): a random player is King, sent into the
 * warzone with a kit; the King wins by surviving, and whoever outside the King's
 * team kills them wins instead.
 *
 * <p>Family A of ARCHITECTURE.md section 9, but a second engine rather than a
 * capture event: {@link com.lawkeys.hcfcore.events.king.KingEventManager} is pure
 * Java and shares nothing with the capture engine but the daily schedule, the
 * {@code /events} command and {@code events.yml}.
 * {@link com.lawkeys.hcfcore.events.king.KingEventController} and
 * {@code KingListener} are the server half.
 *
 * <p>The one piece of persisted state is the King's own inventory,
 * {@link com.lawkeys.hcfcore.events.king.KingStashes}: kept in the database for the
 * length of the reign and handed back afterwards, crash or not.
 */
package com.lawkeys.hcfcore.events.king;
