package com.lawkeys.hcfcore.effectcommand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The effect commands of {@code effect-commands.yml}, checked: a name or an alias
 * is taken by one command only - the first to claim it - and a command that cannot
 * be used is left out with a warning, never half-made.
 */
public final class EffectCommandSet {

    /** The highest level an effect can have: its amplifier is stored in a byte. */
    public static final int MAX_LEVEL = 255;
    /** The permission a command needs when the file names none: {@code hcfcore.effect.<name>}. */
    public static final String PERMISSION_PREFIX = "hcfcore.effect.";

    private static final Pattern LABEL = Pattern.compile("[a-z0-9_-]+");

    /** A command as written in the file, before any check. */
    public record Entry(String name, String effect, int level, List<String> aliases, String permission) {
    }

    private final Map<String, EffectCommand> byName;

    private EffectCommandSet(Map<String, EffectCommand> byName) {
        this.byName = byName;
    }

    public static EffectCommandSet empty() {
        return new EffectCommandSet(Map.of());
    }

    public static EffectCommandSet of(List<Entry> entries, Consumer<String> warn) {
        Map<String, EffectCommand> commands = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();
        for (Entry entry : entries) {
            String name = label(entry.name());
            if (name == null) {
                warn.accept("'" + entry.name() + "' is not a command name (letters, digits, _ and - only); left out.");
                continue;
            }
            if (!taken.add(name)) {
                warn.accept("/" + name + " is already taken by another effect command; left out.");
                continue;
            }
            String effect = effectKey(entry.effect());
            if (effect == null) {
                warn.accept("/" + name + " names no effect; left out.");
                continue;
            }
            if (entry.level() < 1 || entry.level() > MAX_LEVEL) {
                warn.accept("/" + name + ": level must be 1 to " + MAX_LEVEL + "; left out.");
                continue;
            }
            List<String> aliases = new ArrayList<>();
            for (String raw : entry.aliases()) {
                String alias = label(raw);
                if (alias == null) {
                    warn.accept("/" + name + ": '" + raw + "' is not an alias; ignored.");
                } else if (!taken.add(alias)) {
                    warn.accept("/" + name + ": the alias /" + alias + " is already taken; ignored.");
                } else {
                    aliases.add(alias);
                }
            }
            String permission = entry.permission() == null || entry.permission().isBlank()
                    ? PERMISSION_PREFIX + name : entry.permission().trim();
            commands.put(name, new EffectCommand(name, effect, entry.level(), aliases, permission));
        }
        return new EffectCommandSet(Map.copyOf(commands));
    }

    /** @return the command by its name - not an alias - if it is in the file */
    public Optional<EffectCommand> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Collection<EffectCommand> all() {
        return byName.values();
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }

    /** @return a level as the game writes it: II, not 2 - up to X, figures above */
    public static String roman(int level) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level <= numerals.length ? numerals[level - 1] : Integer.toString(level);
    }

    private static String label(String raw) {
        if (raw == null) {
            return null;
        }
        String label = raw.trim().toLowerCase(Locale.ROOT);
        if (label.startsWith("/")) {
            label = label.substring(1);
        }
        return LABEL.matcher(label).matches() ? label : null;
    }

    /** @return the effect's key with its namespace, {@code minecraft:speed}, or {@code null} if blank */
    static String effectKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return key.contains(":") ? key : "minecraft:" + key;
    }
}
