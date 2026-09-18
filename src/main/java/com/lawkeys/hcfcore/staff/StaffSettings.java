package com.lawkeys.hcfcore.staff;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of {@code staff.yml}.
 *
 * <p>Held through a {@code Supplier} so {@code /hcf reload} applies at once
 * (ARCHITECTURE.md section 2).
 */
public record StaffSettings(
        boolean enabled,
        StaffModeRules staffMode,
        VanishRules vanish,
        StaffChatRules staffChat,
        BroadcastRules broadcast,
        FreezeRules freeze,
        InvseeRules invsee,
        LastInventoryRules lastInventory,
        TicketRules tickets,
        StrikeRules strikes) {

    public StaffSettings {
        Objects.requireNonNull(staffMode, "staffMode");
        Objects.requireNonNull(vanish, "vanish");
        Objects.requireNonNull(staffChat, "staffChat");
        Objects.requireNonNull(broadcast, "broadcast");
        Objects.requireNonNull(freeze, "freeze");
        Objects.requireNonNull(invsee, "invsee");
        Objects.requireNonNull(lastInventory, "lastInventory");
        Objects.requireNonNull(tickets, "tickets");
        Objects.requireNonNull(strikes, "strikes");
    }

    /**
     * What entering staff mode does, beyond handing over the toolbar.
     *
     * <p>The three flags are the bundle the project owner chose on 12/09/2026.
     * Building through protection is deliberately <strong>not</strong> among them:
     * it stayed a separate toggle, the staff-build command, so that watching a raid
     * and editing the world are two different decisions.
     *
     * @param vanish       hide them while in staff mode
     * @param flight       let them fly while in staff mode
     * @param invulnerable no damage taken, none dealt, no mob aggro, no item pickup -
     *                     so a staff member cannot disturb the fight they are watching
     * @param announce     tell other staff when somebody enters or leaves the mode
     * @param toolbar      the items handed over, by slot
     */
    public record StaffModeRules(boolean vanish, boolean flight, boolean invulnerable,
                                 boolean announce, StaffToolbar toolbar) {

        public StaffModeRules {
            Objects.requireNonNull(toolbar, "toolbar");
        }
    }

    /**
     * @param hideFromTab   also remove them from the player list, not only from sight
     * @param seePermission the node that lets somebody still see vanished staff, so
     *                      that senior staff are not invisible to each other
     */
    public record VanishRules(boolean hideFromTab, String seePermission) {

        public VanishRules {
            Objects.requireNonNull(seePermission, "seePermission");
        }
    }

    /** @param format the line other staff see; {@code %player%} and {@code %message%} */
    public record StaffChatRules(boolean enabled, String format) {

        public StaffChatRules {
            Objects.requireNonNull(format, "format");
        }
    }

    /** @param format the line everybody sees; {@code %message%} */
    public record BroadcastRules(String format, int clearChatLines) {

        public BroadcastRules {
            Objects.requireNonNull(format, "format");
        }
    }

    /**
     * Holding a player for a check.
     *
     * @param allowedCommands commands a frozen player may still run, without the
     *                        slash, lower-case. They have to be able to answer, and a
     *                        player who cannot talk to the staff member holding them
     *                        can only sit there or disconnect - which is the one thing
     *                        the hold exists to discourage
     * @param banOnLogout     record a moderation ban if they disconnect while held.
     *                        The standard answer, and the only one that makes a freeze
     *                        mean anything - but it is a ban, so it is a switch
     * @param reminderSeconds how often to remind them they are held; {@code 0} tells
     *                        them once and then leaves them alone
     */
    public record FreezeRules(boolean enabled, List<String> allowedCommands,
                              boolean banOnLogout, long reminderSeconds) {

        public FreezeRules {
            allowedCommands = List.copyOf(Objects.requireNonNull(allowedCommands, "allowedCommands"));
        }

        /** @return whether a frozen player may run this command, given without the slash */
        public boolean allows(String command) {
            String name = command.toLowerCase(java.util.Locale.ROOT);
            int space = name.indexOf(' ');
            if (space >= 0) {
                name = name.substring(0, space);
            }
            // A namespaced command (minecraft:tp) must not slip past a check on the
            // bare name; take the part after the colon, as the server does.
            int colon = name.indexOf(':');
            if (colon >= 0) {
                name = name.substring(colon + 1);
            }
            for (String allowed : allowedCommands) {
                if (allowed.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** @param enabled whether {@code /invsee} works at all */
    public record InvseeRules(boolean enabled) {
    }

    /** @param keep how many deaths to archive per player, at least 1 */
    public record LastInventoryRules(boolean enabled, int keep) {
    }

    /**
     * Reports and requests.
     *
     * @param cooldownSeconds how long one player waits between raising tickets -
     *                        without it, one person can fill the queue faster than
     *                        staff can read it
     */
    public record TicketRules(boolean enabled, long cooldownSeconds) {
    }

    /**
     * Strikes.
     *
     * @param validSeconds how long a strike counts towards the ladder; {@code 0}
     *                     means for ever. FEATURES.md lists this as a decision to be
     *                     taken, so it is configuration rather than a rule in code
     */
    public record StrikeRules(boolean enabled, long validSeconds) {
    }

    /** Built-in fallback, mirroring {@code resources/staff.yml}. */
    public static StaffSettings defaults() {
        return new StaffSettings(
                true,
                new StaffModeRules(true, true, true, true, defaultToolbar()),
                new VanishRules(true, "hcfcore.staff.vanish.see"),
                new StaffChatRules(true, "&9[SC] &b%player%&7: &f%message%"),
                new BroadcastRules("&8[&cBroadcast&8] &f%message%", 100),
                new FreezeRules(true, List.of("msg", "r", "reply", "tell", "w", "whisper"), true, 10L),
                new InvseeRules(true),
                new LastInventoryRules(true, 3),
                new TicketRules(true, 60L),
                // Zero: a strike counts for ever until an operator decides otherwise.
                // FEATURES.md forbids inventing the scale, and a validity period is
                // part of it.
                new StrikeRules(true, 0L));
    }

    /**
     * The toolbar shipped out of the box.
     *
     * <p>Every command bound here is one this module actually provides - the rule
     * that kept Inspect and Freeze out of the first version, when {@code /invsee}
     * and {@code /freeze} did not yet exist and a slot answering "unknown command"
     * would have been the ghost behaviour ARCHITECTURE.md section 2 forbids. They
     * exist now, so they are here, and they are the two targeted items that give
     * {@code needs-target} a reason to be in the file.
     */
    private static StaffToolbar defaultToolbar() {
        return StaffToolbar.of(List.of(
                new ToolbarItem(0, "COMPASS", "&b&lPlayer Tracker",
                        List.of("&7Teleports you to the nearest player."),
                        "staff tpnearest", false),
                new ToolbarItem(1, "ENDER_PEARL", "&d&lRandom Teleport",
                        List.of("&7Teleports you to a random player."),
                        "staff randomtp", false),
                new ToolbarItem(2, "BOOK", "&e&lInspect",
                        List.of("&7Right-click a player to see", "&7what they are carrying."),
                        "invsee %player%", true),
                new ToolbarItem(3, "PACKED_ICE", "&b&lFreeze",
                        List.of("&7Right-click a player to hold", "&7them for a check."),
                        "freeze %player%", true),
                new ToolbarItem(7, "LIME_DYE", "&a&lVanish",
                        List.of("&7Toggles whether players can see you."),
                        "vanish", false),
                new ToolbarItem(8, "BARRIER", "&c&lLeave Staff Mode",
                        List.of("&7Gives your own items back."),
                        "staff", false)),
                warning -> {
                    // Unreachable: this list is a constant, and any clash in it would
                    // be a bug in this method rather than in an operator's file.
                });
    }
}
