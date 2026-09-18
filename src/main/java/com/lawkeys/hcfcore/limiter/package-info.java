/**
 * Enchantment and potion limits (FEATURES.md sections 11 and 12).
 *
 * <p>{@link com.lawkeys.hcfcore.limiter.LevelCaps} holds every rule - clamping, and
 * the anvil's combining with a cap in place of vanilla's maximum - and is pure Java.
 * {@link com.lawkeys.hcfcore.limiter.ItemLevels} reads and writes the levels of a
 * real item, books included, and the listener applies both where enchantments and
 * effects are made.
 *
 * <p><strong>Blocks per claim.</strong> {@link com.lawkeys.hcfcore.limiter.ClaimBlockLimits}
 * caps how many of a block a team's territory may hold, the project owner's choice of
 * 12/09/2026. {@link com.lawkeys.hcfcore.limiter.ClaimBlockCounts} keeps the count per
 * claimed chunk, stored and recounted off the main thread, and {@code /team limits}
 * shows it.
 *
 * <p><strong>Not covered:</strong> a limit on items per player. FEATURES.md does not
 * describe one.
 */
package com.lawkeys.hcfcore.limiter;
