/**
 * Apollo (Lunar Client) integration, FEATURES.md section 13: the Waypoint, Team,
 * Cooldown and Nametag modules, chosen by the project owner on 12/09/2026.
 *
 * <p>Soft dependency on Lunar's Apollo plugin ({@code Apollo-Bukkit}): with it absent,
 * {@link com.lawkeys.hcfcore.integration.lunar.LunarIntegration} stays inert and no
 * Apollo class is ever loaded; with it present, a player not on Lunar Client is never
 * sent anything. {@code ApolloBridge} is the only class naming an Apollo type. The
 * rules - what changed since the last update, what a nametag reads - are pure and
 * tested ({@link com.lawkeys.hcfcore.integration.lunar.SentState},
 * {@link com.lawkeys.hcfcore.integration.lunar.NametagStyle}).
 *
 * <p>Apollo's Hologram module is deliberately not used: it is visible to Lunar Client
 * players only, and the plugin's holograms are for everybody (see the
 * {@code hologram} package).
 */
package com.lawkeys.hcfcore.integration.lunar;
