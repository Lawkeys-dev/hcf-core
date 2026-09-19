package com.lawkeys.hcfcore.staff;

import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.staff.strike.StrikeOffences;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Turns {@code staff.yml} into an immutable {@link StaffSettings}.
 *
 * <p>Invalid values are reported and replaced by the built-in default rather than
 * taking the server down (ARCHITECTURE.md section 6).
 */
public final class StaffSettingsLoader {

    private StaffSettingsLoader() {
    }

    public static StaffSettings load(ConfigurationSection section, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        StaffSettings defaults = StaffSettings.defaults();
        if (section == null) {
            warn.accept("staff.yml is missing or empty - using built-in defaults for the staff module.");
            return defaults;
        }

        return new StaffSettings(
                section.getBoolean("enabled", defaults.enabled()),
                loadStaffMode(section.getConfigurationSection("staff-mode"), defaults.staffMode(), warn),
                loadVanish(section.getConfigurationSection("vanish"), defaults.vanish()),
                loadStaffChat(section.getConfigurationSection("staff-chat"), defaults.staffChat()),
                loadBroadcast(section.getConfigurationSection("broadcast"), defaults.broadcast()),
                loadFreeze(section.getConfigurationSection("freeze"), defaults.freeze(), warn),
                loadInvsee(section.getConfigurationSection("invsee"), defaults.invsee()),
                loadLastInventory(section.getConfigurationSection("last-inventory"),
                        defaults.lastInventory(), warn),
                loadTickets(section.getConfigurationSection("tickets"), defaults.tickets(), warn),
                loadStrikes(section.getConfigurationSection("strikes"), defaults.strikes(), warn));
    }

    private static StaffSettings.StaffModeRules loadStaffMode(ConfigurationSection section,
                                                             StaffSettings.StaffModeRules defaults,
                                                             Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new StaffSettings.StaffModeRules(
                section.getBoolean("vanish", defaults.vanish()),
                section.getBoolean("flight", defaults.flight()),
                section.getBoolean("invulnerable", defaults.invulnerable()),
                section.getBoolean("announce", defaults.announce()),
                loadToolbar(section.getConfigurationSection("items"), defaults.toolbar(), warn));
    }

    /**
     * Reads the toolbar.
     *
     * <p>An <em>absent</em> {@code items} section means "not configured", and the
     * shipped toolbar is used. An <em>empty</em> one means the operator deleted
     * every item on purpose, and staff mode then hands out nothing - the difference
     * matters, because the second is a choice and the first is silence.
     */
    private static StaffToolbar loadToolbar(ConfigurationSection section, StaffToolbar defaults,
                                            Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        List<ToolbarItem> items = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                warn.accept("staff-mode.items." + key + " is not a block of settings; ignored.");
                continue;
            }
            int slot;
            try {
                slot = Integer.parseInt(key.trim());
            } catch (NumberFormatException e) {
                warn.accept("staff-mode.items." + key + " is not a slot number; ignored.");
                continue;
            }
            String material = itemSection.getString("material");
            if (material == null || material.isBlank()) {
                warn.accept("staff-mode.items." + key + " has no material; ignored.");
                continue;
            }
            items.add(new ToolbarItem(
                    slot,
                    material,
                    itemSection.getString("name"),
                    itemSection.getStringList("lore"),
                    Objects.requireNonNullElse(itemSection.getString("command"), ""),
                    itemSection.getBoolean("needs-target", false)));
        }
        return StaffToolbar.of(items, message -> warn.accept("staff-mode.items: " + message));
    }

    private static StaffSettings.FreezeRules loadFreeze(ConfigurationSection section,
                                                       StaffSettings.FreezeRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        List<String> allowed = section.getStringList("allowed-commands");
        return new StaffSettings.FreezeRules(
                section.getBoolean("enabled", defaults.enabled()),
                // An empty list is a choice - a frozen player who may run nothing at
                // all - so it is only replaced when the key is absent entirely.
                section.isSet("allowed-commands") ? allowed : defaults.allowedCommands(),
                section.getBoolean("ban-on-logout", defaults.banOnLogout()),
                Math.max(0L, Durations.capSeconds(section.getLong("reminder-seconds", defaults.reminderSeconds()), "reminder-seconds", warn)));
    }

    private static StaffSettings.InvseeRules loadInvsee(ConfigurationSection section,
                                                       StaffSettings.InvseeRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new StaffSettings.InvseeRules(section.getBoolean("enabled", defaults.enabled()));
    }

    private static StaffSettings.LastInventoryRules loadLastInventory(ConfigurationSection section,
                                                                     StaffSettings.LastInventoryRules defaults,
                                                                     Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        int keep = section.getInt("keep", defaults.keep());
        if (keep < 1) {
            warn.accept("last-inventory.keep must be at least 1; using " + defaults.keep() + ".");
            keep = defaults.keep();
        }
        return new StaffSettings.LastInventoryRules(
                section.getBoolean("enabled", defaults.enabled()), keep);
    }

    /**
     * Reads what a team can be struck for ({@code strikes.offences}) and how many
     * strikes disband it ({@code strikes.disband-at}). Without offences in the file,
     * the shipped ones are used: a strike always has something to be for.
     */
    public static StrikeOffences loadStrikeOffences(ConfigurationSection section, Consumer<String> warn) {
        StrikeOffences defaults = StrikeOffences.defaults();
        ConfigurationSection strikes = section == null ? null : section.getConfigurationSection("strikes");
        if (strikes == null) {
            return defaults;
        }
        if (strikes.contains("ladder")) {
            warn.accept("strikes.ladder is no longer read: a strike is given for an offence (strikes.offences),"
                    + " each with its points-loss-percent, and disband-at disbands the team - see staff.yml.");
        }
        int disbandAt = strikes.getInt("disband-at", defaults.disbandAt());
        ConfigurationSection listed = strikes.getConfigurationSection("offences");
        if (listed == null || listed.getKeys(false).isEmpty()) {
            return StrikeOffences.of(defaults.all(), disbandAt, warn);
        }
        List<StrikeOffences.Offence> offences = new ArrayList<>();
        for (String id : listed.getKeys(false)) {
            ConfigurationSection offence = listed.getConfigurationSection(id);
            if (offence == null) {
                warn.accept("offences." + id + " must list name and points-loss-percent; ignored.");
                continue;
            }
            int percent = offence.getInt("points-loss-percent", 0);
            if (percent < 0 || percent > 100) {
                warn.accept("offences." + id + ".points-loss-percent must be 0 to 100, got " + percent
                        + "; brought within.");
            }
            offences.add(new StrikeOffences.Offence(id, offence.getString("name", id), percent,
                    offence.getStringList("commands")));
        }
        return StrikeOffences.of(offences, disbandAt, warn);
    }

    private static StaffSettings.TicketRules loadTickets(ConfigurationSection section,
                                                        StaffSettings.TicketRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new StaffSettings.TicketRules(
                section.getBoolean("enabled", defaults.enabled()),
                Math.max(0L, Durations.capSeconds(section.getLong("cooldown-seconds", defaults.cooldownSeconds()), "cooldown-seconds", warn)));
    }

    private static StaffSettings.StrikeRules loadStrikes(ConfigurationSection section,
                                                        StaffSettings.StrikeRules defaults, Consumer<String> warn) {
        if (section == null) {
            return defaults;
        }
        return new StaffSettings.StrikeRules(
                section.getBoolean("enabled", defaults.enabled()),
                Math.max(0L, Durations.capSeconds(section.getLong("valid-seconds", defaults.validSeconds()), "valid-seconds", warn)));
    }

    private static StaffSettings.VanishRules loadVanish(ConfigurationSection section,
                                                       StaffSettings.VanishRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new StaffSettings.VanishRules(
                section.getBoolean("hide-from-tab", defaults.hideFromTab()),
                Objects.requireNonNullElse(section.getString("see-permission"), defaults.seePermission()));
    }

    private static StaffSettings.StaffChatRules loadStaffChat(ConfigurationSection section,
                                                             StaffSettings.StaffChatRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new StaffSettings.StaffChatRules(
                section.getBoolean("enabled", defaults.enabled()),
                Objects.requireNonNullElse(section.getString("format"), defaults.format()));
    }

    private static StaffSettings.BroadcastRules loadBroadcast(ConfigurationSection section,
                                                             StaffSettings.BroadcastRules defaults) {
        if (section == null) {
            return defaults;
        }
        return new StaffSettings.BroadcastRules(
                Objects.requireNonNullElse(section.getString("format"), defaults.format()),
                Math.max(0, section.getInt("clear-chat-lines", defaults.clearChatLines())));
    }
}
