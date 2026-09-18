/**
 * Custom Bukkit events published by HCFCore so other modules - and third-party
 * plugins - can react to core state changes without modifying the core
 * (ARCHITECTURE.md section 7).
 *
 * <p>These types are part of the plugin's public API: renaming or removing one
 * breaks downstream plugins, so treat them as a contract.
 */
package com.lawkeys.hcfcore.api.event;
