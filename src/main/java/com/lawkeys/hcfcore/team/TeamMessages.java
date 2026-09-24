package com.lawkeys.hcfcore.team;

/**
 * Every language key the team module can produce.
 *
 * <p>No player-facing text is ever built in Java (ARCHITECTURE.md section 10):
 * managers return a key plus placeholders, and the command layer resolves it
 * through the language file. Keeping the keys here as constants means the
 * mapping between code and {@code lang/en.yml} is auditable - and it is
 * actually audited, by {@code TeamMessagesTest}, which fails the build if a key
 * used in code is missing from the shipped language file.
 *
 * <p>Key convention: {@code <module>.<context>.<message>}.
 */
public final class TeamMessages {

    private TeamMessages() {
    }

    // Name validation
    public static final String NAME_TOO_SHORT = "team.name.too-short";
    public static final String NAME_TOO_LONG = "team.name.too-long";
    public static final String NAME_INVALID_CHARACTERS = "team.name.invalid-characters";
    public static final String NAME_BLACKLISTED = "team.name.blacklisted";
    public static final String NAME_TAKEN = "team.name.taken";

    // Generic
    public static final String NOT_IN_TEAM = "team.error.not-in-team";
    public static final String TARGET_NOT_IN_TEAM = "team.error.target-not-in-team";
    public static final String NOT_A_MEMBER = "team.error.not-a-member";
    public static final String INSUFFICIENT_ROLE = "team.error.insufficient-role";
    public static final String CANNOT_TARGET_SELF = "team.error.cannot-target-self";
    public static final String SYSTEM_TEAM_IMMUTABLE = "team.error.system-team-immutable";
    public static final String NOT_SYSTEM_TEAM = "team.error.not-system-team";
    public static final String TEAM_FULL = "team.error.team-full";

    // Create / disband / rename
    public static final String CREATE_SUCCESS = "team.create.success";

    // System teams: safe zones and combat zones
    // /lff (teams.yml, lff)
    public static final String LFF_BROADCAST = "team.lff.broadcast";
    public static final String LFF_BROADCAST_NOTE = "team.lff.broadcast-note";
    public static final String LFF_IN_TEAM = "team.lff.in-team";
    public static final String LFF_COOLDOWN = "team.lff.cooldown";
    public static final String LFF_DISABLED = "team.lff.disabled";
    public static final String LFF_PLAYERS_ONLY = "team.lff.players-only";

    public static final String SYSTEM_CREATED_SAFE = "team.system.created-safe";
    public static final String SYSTEM_CREATED_COMBAT = "team.system.created-combat";
    public static final String SYSTEM_ZONE_SAFE = "team.system.zone-safe";
    public static final String SYSTEM_ZONE_COMBAT = "team.system.zone-combat";
    public static final String SYSTEM_INVALID_ZONE = "team.system.invalid-zone";
    public static final String INFO_ZONE_SAFE = "team.info.zone-safe";
    public static final String INFO_ZONE_COMBAT = "team.info.zone-combat";
    public static final String CREATE_ALREADY_IN_TEAM = "team.create.already-in-team";
    public static final String CREATE_CANCELLED = "team.create.cancelled";
    public static final String DISBAND_SUCCESS = "team.disband.success";
    public static final String DISBAND_CANCELLED = "team.disband.cancelled";
    public static final String RENAME_SUCCESS = "team.rename.success";
    public static final String RENAME_SAME_NAME = "team.rename.same-name";
    public static final String RENAME_CANCELLED = "team.rename.cancelled";

    // Invites
    public static final String INVITE_SENT = "team.invite.sent";
    public static final String INVITE_ALREADY_SENT = "team.invite.already-sent";
    public static final String INVITE_TARGET_IN_TEAM = "team.invite.target-in-team";
    public static final String INVITE_REVOKED = "team.invite.revoked";
    public static final String INVITE_NOT_FOUND = "team.invite.not-found";
    public static final String JOIN_SUCCESS = "team.join.success";
    public static final String JOIN_NO_INVITE = "team.join.no-invite";
    public static final String JOIN_ALREADY_IN_TEAM = "team.join.already-in-team";
    public static final String JOIN_FORCED = "team.join.forced";
    public static final String JOIN_FORCED_ALREADY_IN_TEAM = "team.join.forced-already-in-team";

    // Leave / kick
    public static final String LEAVE_SUCCESS = "team.leave.success";
    public static final String LEAVE_LEADER_MUST_TRANSFER = "team.leave.leader-must-transfer";
    public static final String LEAVE_DISBANDED = "team.leave.disbanded";
    public static final String KICK_SUCCESS = "team.kick.success";

    // Roles
    public static final String PROMOTE_SUCCESS = "team.promote.success";
    public static final String PROMOTE_ALREADY_HIGHEST = "team.promote.already-highest";
    public static final String PROMOTE_CO_LEADER_LIMIT = "team.promote.co-leader-limit";
    public static final String DEMOTE_SUCCESS = "team.demote.success";
    public static final String DEMOTE_ALREADY_LOWEST = "team.demote.already-lowest";
    public static final String DEMOTE_CANNOT_DEMOTE_LEADER = "team.demote.cannot-demote-leader";
    public static final String TRANSFER_SUCCESS = "team.transfer.success";

    // Alliances
    public static final String ALLY_DISABLED = "team.ally.disabled";
    public static final String ALLY_SAME_TEAM = "team.ally.same-team";
    public static final String ALLY_ALREADY_ALLIED = "team.ally.already-allied";
    public static final String ALLY_REQUEST_SENT = "team.ally.request-sent";
    public static final String ALLY_REQUEST_ALREADY_SENT = "team.ally.request-already-sent";
    public static final String ALLY_NOW_ALLIED = "team.ally.now-allied";
    public static final String ALLY_LIMIT_REACHED = "team.ally.limit-reached";
    public static final String ALLY_TARGET_LIMIT_REACHED = "team.ally.target-limit-reached";
    public static final String UNALLY_SUCCESS = "team.unally.success";
    public static final String UNALLY_NOT_ALLIED = "team.unally.not-allied";

    // Focus
    public static final String FOCUS_DISABLED = "team.focus.disabled";
    public static final String FOCUS_TEAM_SUCCESS = "team.focus.team-success";
    public static final String FOCUS_PLAYER_SUCCESS = "team.focus.player-success";
    public static final String FOCUS_ALREADY_FOCUSED = "team.focus.already-focused";
    public static final String FOCUS_LIMIT_REACHED = "team.focus.limit-reached";
    public static final String FOCUS_CANNOT_FOCUS_OWN_TEAM = "team.focus.cannot-focus-own-team";
    public static final String UNFOCUS_SUCCESS = "team.unfocus.success";
    public static final String UNFOCUS_NOT_FOCUSED = "team.unfocus.not-focused";

    // Rally
    public static final String RALLY_DISABLED = "team.rally.disabled";
    public static final String RALLY_SET = "team.rally.set";
    public static final String RALLY_CLEARED = "team.rally.cleared";
    public static final String RALLY_NOT_SET = "team.rally.not-set";

    // Bank / points / KOTH
    public static final String BANK_DISABLED = "team.bank.disabled";
    public static final String BANK_INVALID_AMOUNT = "team.bank.invalid-amount";
    public static final String BANK_INSUFFICIENT_FUNDS = "team.bank.insufficient-funds";
    public static final String BANK_DEPOSIT_SUCCESS = "team.bank.deposit-success";
    public static final String BANK_WITHDRAW_SUCCESS = "team.bank.withdraw-success";
    public static final String POINTS_SET = "team.points.set";
    public static final String POINTS_ADDED = "team.points.added";
    public static final String KOTH_CAP_REACHED = "team.koth.cap-reached";
    public static final String KOTH_CAPTURE_COUNTED = "team.koth.capture-counted";
    public static final String KOTH_RESET = "team.koth.reset";

    // Command layer (usage, listings, team broadcasts)
    public static final String COMMAND_UNKNOWN_SUBCOMMAND = "team.command.unknown-subcommand";
    public static final String COMMAND_USAGE = "team.command.usage";
    public static final String COMMAND_HELP_HEADER = "team.command.help-header";
    public static final String COMMAND_HELP_ENTRY = "team.command.help-entry";
    public static final String COMMAND_INVALID_NUMBER = "team.command.invalid-number";

    public static final String INFO_HEADER = "team.info.header";
    public static final String INFO_LEADER = "team.info.leader";
    public static final String INFO_CO_LEADERS = "team.info.co-leaders";
    public static final String INFO_OFFICERS = "team.info.officers";
    public static final String INFO_MEMBERS = "team.info.members";
    public static final String INFO_ONLINE = "team.info.online";
    public static final String INFO_BALANCE = "team.info.balance";
    public static final String INFO_POINTS = "team.info.points";
    public static final String INFO_KOTH_CAPTURES = "team.info.koth-captures";
    public static final String INFO_STRIKES = "team.info.strikes";
    public static final String INFO_ALLIES = "team.info.allies";
    public static final String INFO_RALLY = "team.info.rally";
    public static final String INFO_NONE = "team.info.none";

    public static final String LIST_HEADER = "team.list.header";
    public static final String LIST_ENTRY = "team.list.entry";
    public static final String LIST_EMPTY = "team.list.empty";

    public static final String CHAT_SWITCHED = "team.chat.switched";
    public static final String CHAT_FORMAT_TEAM = "team.chat.format-team";
    public static final String CHAT_FORMAT_ALLY = "team.chat.format-ally";
    public static final String CHAT_LOG = "team.chat.log";

    // Broadcasts sent to the rest of the team when one member acts
    public static final String DISBAND_BROADCAST = "team.disband.broadcast";
    public static final String RENAME_BROADCAST = "team.rename.broadcast";
    public static final String INVITE_RECEIVED = "team.invite.received";
    public static final String INVITE_BROADCAST = "team.invite.broadcast";
    public static final String JOIN_BROADCAST = "team.join.broadcast";
    public static final String LEAVE_BROADCAST = "team.leave.broadcast";
    public static final String KICK_KICKED = "team.kick.kicked";
    public static final String KICK_BROADCAST = "team.kick.broadcast";
    public static final String PROMOTE_BROADCAST = "team.promote.broadcast";
    public static final String DEMOTE_BROADCAST = "team.demote.broadcast";
    public static final String TRANSFER_BROADCAST = "team.transfer.broadcast";
    /** To the staff member whose /team forcekick removed a leader: who took over. */
    public static final String TRANSFER_FORCED = "team.transfer.forced";
    public static final String ALLY_RECEIVED = "team.ally.received";
    public static final String ALLY_BROADCAST = "team.ally.broadcast";
    public static final String UNALLY_BROADCAST = "team.unally.broadcast";
    public static final String FOCUS_BROADCAST = "team.focus.broadcast";
    public static final String RALLY_BROADCAST = "team.rally.broadcast";

    // Lookup
    public static final String NOT_FOUND = "team.error.not-found";
    public static final String PLAYER_NOT_FOUND = "team.error.player-not-found";

    // /team settings
    public static final String PROMOTE_OFFICER_LIMIT = "team.promote.officer-limit";
    public static final String SETTINGS_LOCKED = "team.settings.locked";
    public static final String SETTINGS_ABOVE_YOU = "team.settings.above-you";
    public static final String SETTINGS_PERMISSION_SET = "team.settings.permission-set";
    public static final String SETTINGS_JOIN_MODE_DISABLED = "team.settings.join-mode-disabled";
    public static final String SETTINGS_JOIN_MODE_SET = "team.settings.join-mode-set";
    public static final String SETTINGS_DESCRIPTION_SET = "team.settings.description-set";
    public static final String SETTINGS_DESCRIPTION_CLEARED = "team.settings.description-cleared";
    public static final String SETTINGS_DISCORD_SET = "team.settings.discord-set";
    public static final String SETTINGS_DISCORD_CLEARED = "team.settings.discord-cleared";
    public static final String SETTINGS_DISCORD_INVALID = "team.settings.discord-invalid";
    public static final String TEAM_CLOSED = "team.join.closed";
    public static final String INFO_DESCRIPTION = "team.info.description";
    public static final String INFO_DISCORD = "team.info.discord";
    public static final String INFO_JOIN_MODE = "team.info.join-mode";
    /** How a join mode reads: {@code team.join-mode.<mode>}. */
    public static String joinMode(JoinMode mode) {
        return "team.join-mode." + mode.configKey();
    }
    public static final String SETTINGS_BUTTON_PROFILE = "team.settings.button.profile";
    public static final String SETTINGS_BUTTON_JOIN_MODE = "team.settings.button.join-mode";
    public static final String SETTINGS_BUTTON_JOIN_MODE_LORE = "team.settings.button.join-mode-lore";
    public static final String SETTINGS_BUTTON_INVITE = "team.settings.button.invite";
    public static final String SETTINGS_BUTTON_INVITE_LORE = "team.settings.button.invite-lore";
    public static final String SETTINGS_BUTTON_NOT_ALLOWED = "team.settings.button.not-allowed";
    public static final String SETTINGS_PROFILE_DESCRIPTION = "team.settings.profile.description";
    public static final String SETTINGS_PROFILE_DISCORD = "team.settings.profile.discord";
    public static final String SETTINGS_PROFILE_NONE = "team.settings.profile.none";
    public static final String SETTINGS_PROFILE_HINT = "team.settings.profile.hint";
    public static final String DIALOG_PROFILE_TITLE = "team.settings.dialog.profile-title";
    public static final String DIALOG_NAME = "team.settings.dialog.name";
    public static final String DIALOG_DESCRIPTION = "team.settings.dialog.description";
    public static final String DIALOG_DISCORD = "team.settings.dialog.discord";
    public static final String DIALOG_INVITE_TITLE = "team.settings.dialog.invite-title";
    public static final String DIALOG_PLAYER = "team.settings.dialog.player";
    public static final String DIALOG_SAVE = "team.settings.dialog.save";
    public static final String DIALOG_SEND = "team.settings.dialog.send";
    public static final String DIALOG_CANCEL = "team.settings.dialog.cancel";
    public static final String SETTINGS_DISABLED = "team.settings.disabled";
    public static final String SETTINGS_TITLE = "team.settings.title";
    public static final String SETTINGS_PERMISSIONS_TITLE = "team.settings.permissions-title";
    public static final String SETTINGS_MEMBERS_TITLE = "team.settings.members-title";
    public static final String SETTINGS_INVITES_TITLE = "team.settings.invites-title";
    public static final String SETTINGS_BUTTON_PERMISSIONS = "team.settings.button.permissions";
    public static final String SETTINGS_BUTTON_PERMISSIONS_LORE = "team.settings.button.permissions-lore";
    public static final String SETTINGS_BUTTON_MEMBERS = "team.settings.button.members";
    public static final String SETTINGS_BUTTON_MEMBERS_LORE = "team.settings.button.members-lore";
    public static final String SETTINGS_BUTTON_INVITES = "team.settings.button.invites";
    public static final String SETTINGS_BUTTON_INVITES_LORE = "team.settings.button.invites-lore";
    public static final String SETTINGS_BUTTON_BACK = "team.settings.button.back";
    public static final String SETTINGS_PERMISSION_NAME = "team.settings.permission.name";
    public static final String SETTINGS_PERMISSION_ROLE = "team.settings.permission.role";
    public static final String SETTINGS_PERMISSION_SERVER = "team.settings.permission.server";
    public static final String SETTINGS_PERMISSION_OWN = "team.settings.permission.own";
    public static final String SETTINGS_PERMISSION_HINT = "team.settings.permission.hint";
    public static final String SETTINGS_PERMISSION_LOCKED = "team.settings.permission.locked";
    public static final String SETTINGS_MEMBER_NAME = "team.settings.member.name";
    public static final String SETTINGS_MEMBER_ROLE = "team.settings.member.role";
    public static final String SETTINGS_MEMBER_HINT = "team.settings.member.hint";
    public static final String SETTINGS_INVITE_NAME = "team.settings.invite.name";
    public static final String SETTINGS_INVITE_HINT = "team.settings.invite.hint";
}
