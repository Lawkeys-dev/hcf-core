/**
 * Resource-regeneration events - family B of ARCHITECTURE.md section 9: Mountain,
 * Glowstone Mountain, Ore Mountain.
 *
 * <p>Deliberately not part of {@code events/}. There is no winner and no capture
 * condition here, only periodic block regeneration in a protected region, plus
 * the broadcast that turns it into a PvP hotspot (FEATURES.md section 6). Sharing
 * the capture abstraction would mean carrying a holding team, a countdown and a
 * reward through code that can never have any of the three.
 *
 * <p>The split is between rules and server, as everywhere else in this plugin:
 * {@link com.lawkeys.hcfcore.resourcenode.ResourceNodeManager} and everything it
 * touches are plain Java, and
 * {@link com.lawkeys.hcfcore.resourcenode.ResourceNodeModule} is the only part
 * that knows there is a world.
 */
package com.lawkeys.hcfcore.resourcenode;
