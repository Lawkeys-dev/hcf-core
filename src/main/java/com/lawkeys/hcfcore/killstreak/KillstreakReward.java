package com.lawkeys.hcfcore.killstreak;

import java.util.List;
import java.util.Objects;

/**
 * What a player gets for reaching a streak.
 *
 * <p><strong>Rewards are console commands, not built-in effects.</strong> FEATURES.md
 * leaves the reward table entirely to the operator, and a command list is the only
 * shape that can express "give them a kit", "drop a supply crate", "run something
 * from another plugin" without this module knowing what any of those are. The same
 * decision as the staff toolbar, and for the same reason.
 *
 * @param streak    the number of kills in a row this fires at
 * @param broadcast a line sent to everybody, or blank for a silent reward
 * @param commands  run from the console, with {@code %player%} and {@code %streak%}
 */
public record KillstreakReward(int streak, String broadcast, List<String> commands) {

    public KillstreakReward {
        commands = List.copyOf(Objects.requireNonNullElseGet(commands, List::<String>of));
        broadcast = broadcast == null ? "" : broadcast;
    }

    /** @return the commands with this player's name and streak filled in */
    public List<String> commandsFor(String playerName) {
        return commands.stream()
                .map(command -> command
                        .replace("%player%", playerName == null ? "" : playerName)
                        .replace("%streak%", String.valueOf(streak)))
                .toList();
    }

    public String broadcastFor(String playerName) {
        return broadcast
                .replace("%player%", playerName == null ? "" : playerName)
                .replace("%streak%", String.valueOf(streak));
    }

    public boolean hasBroadcast() {
        return !broadcast.isBlank();
    }
}
