/**
 * The configurable chat format, and the routing of team and ally chat
 * (FEATURES.md sections 1 and 9).
 *
 * <p>One listener owns {@code AsyncChatEvent} for the whole plugin. The team
 * channel router that {@code team/listener} was written to hold lives here too,
 * because two listeners acting on one event - one cancelling it, the other
 * rendering it - would behave according to their relative priorities, which is the
 * kind of ordering nobody notices until chat goes missing.
 *
 * <p>The format is a template, not a layout: order, colours and separators are the
 * operator's. The kill count is dropped from the line entirely when it is zero,
 * since a fresh player reading {@code [0]} beside their name is noise.
 */
package com.lawkeys.hcfcore.chat;
