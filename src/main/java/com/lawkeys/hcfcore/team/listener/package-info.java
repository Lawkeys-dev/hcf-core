/**
 * Listeners for the team module.
 *
 * <p>{@link com.lawkeys.hcfcore.team.listener.TeamPointsListener} reports what moves the
 * Team Points scale: deaths and kills, and a team made raidable.
 *
 * <p><strong>The team chat router is not here.</strong> Sending a player's message
 * to their team or allies when {@code /team chat} has switched their channel lives
 * in {@code chat/listener/ChatListener}, because the public chat format acts on the
 * same {@code AsyncChatEvent}. Two listeners on one event, one cancelling it and the
 * other rendering it, would behave according to their relative priorities - the
 * kind of ordering nobody notices until chat goes missing. One listener decides
 * once: a non-public channel is routed and the event cancelled, otherwise the line
 * is formatted.
 */
package com.lawkeys.hcfcore.team.listener;
