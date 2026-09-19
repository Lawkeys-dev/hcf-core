package com.lawkeys.hcfcore.staff.strike;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * What a team can be struck for, and what each strike costs it.
 *
 * <p>The project owner's rules (19/09/2026): a strike is given for an offence -
 * cheating, kill boosting, teaming, bug abuse... - each taking its own share of the
 * team's points; and whatever the offences, the team is disbanded when its active
 * strikes reach {@code disband-at}, 3 as shipped. Pure Java; the numbers are
 * {@code staff.yml}'s.
 */
public final class StrikeOffences {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,32}");

    /**
     * One offence.
     *
     * @param id                what staff type: {@code /strike add <team> boosting}
     * @param name              how it is written in messages
     * @param pointsLossPercent share of the team's points it takes, 0 to 100
     * @param commands          console commands run with it, {@code %team%}, {@code %strikes%}
     *                          and {@code %offence%} filled in - for anything else, from any plugin
     */
    public record Offence(String id, String name, int pointsLossPercent, List<String> commands) {
        public Offence {
            Objects.requireNonNull(id, "id");
            name = name == null || name.isBlank() ? id : name;
            pointsLossPercent = Math.max(0, Math.min(100, pointsLossPercent));
            commands = List.copyOf(Objects.requireNonNullElseGet(commands, List::<String>of));
        }
    }

    private final Map<String, Offence> offences;
    private final int disbandAt;

    private StrikeOffences(Map<String, Offence> offences, int disbandAt) {
        this.offences = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(offences));
        this.disbandAt = Math.max(0, disbandAt);
    }

    /** As shipped. */
    public static StrikeOffences defaults() {
        return of(List.of(
                new Offence("cheating", "Cheating", 50, List.of()),
                new Offence("bug-abuse", "Bug abuse", 50, List.of()),
                new Offence("ban-evasion", "Ban evasion", 50, List.of()),
                new Offence("boosting", "Kill boosting", 40, List.of()),
                new Offence("teaming", "Teaming", 35, List.of()),
                new Offence("alt-abuse", "Alt abuse", 35, List.of()),
                new Offence("claim-abuse", "Claim abuse", 25, List.of()),
                new Offence("other", "Other", 25, List.of())), 3, message -> { });
    }

    /**
     * @param disbandAt the number of active strikes that disbands a team, whatever they
     *                  were for; {@code 0} for never
     */
    public static StrikeOffences of(List<Offence> offences, int disbandAt, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        Map<String, Offence> checked = new LinkedHashMap<>();
        for (Offence offence : offences) {
            String id = offence.id().trim().toLowerCase(Locale.ROOT);
            if (!VALID_ID.matcher(id).matches()) {
                warn.accept("offences." + offence.id() + ": an id is 1 to 32 letters, digits, _ or -; ignored.");
                continue;
            }
            if (checked.containsKey(id)) {
                warn.accept("offences." + id + " is listed twice; the first is kept.");
                continue;
            }
            checked.put(id, new Offence(id, offence.name(), offence.pointsLossPercent(), offence.commands()));
        }
        if (disbandAt < 0) {
            warn.accept("disband-at must be 0 (never) or more, got " + disbandAt + "; 0 is used.");
        }
        return new StrikeOffences(checked, disbandAt);
    }

    public Optional<Offence> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(offences.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    /** @return every offence, in the file's order */
    public List<Offence> all() {
        return new ArrayList<>(offences.values());
    }

    public boolean isEmpty() {
        return offences.isEmpty();
    }

    /** @return the number of active strikes that disbands a team, or {@code 0} for never */
    public int disbandAt() {
        return disbandAt;
    }

    /** @return whether this many active strikes disbands a team */
    public boolean disbands(int activeStrikes) {
        return disbandAt > 0 && activeStrikes >= disbandAt;
    }

    /**
     * @return the points a strike takes: {@code percent} of what the team has,
     *         rounded down, and nothing from a team with none
     */
    public static long pointsLost(long points, int percent) {
        if (points <= 0 || percent <= 0) {
            return 0L;
        }
        int share = Math.min(100, percent);
        // Split so that points * percent cannot overflow, whatever the score.
        return points / 100 * share + points % 100 * share / 100;
    }

    /** @return the commands with the team's name, its count and the offence filled in */
    public static List<String> fill(List<String> commands, String teamName, int count, Offence offence) {
        return commands.stream()
                .map(command -> command
                        .replace("%team%", teamName == null ? "" : teamName)
                        .replace("%strikes%", String.valueOf(count))
                        .replace("%offence%", offence.id()))
                .toList();
    }
}
