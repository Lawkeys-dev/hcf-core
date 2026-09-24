package com.lawkeys.hcfcore.events.king;

import com.lawkeys.hcfcore.util.Durations;
import com.lawkeys.hcfcore.events.CaptureEventDefinition;
import com.lawkeys.hcfcore.events.EventSettings;
import com.lawkeys.hcfcore.events.EventSettingsLoader;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the {@code kill-the-king} section of {@code events.yml}.
 *
 * <p>Same contract as {@code EventSettingsLoader}: a malformed entry is reported
 * and skipped, never fatal. A kit item, enchantment or effect the server does not
 * know is reported and left out - never guessed at - and the rest of the kit
 * stands.
 *
 * <p>It runs on the server, not in tests: checking a name means asking the
 * server's registries.
 */
public final class KingSettingsLoader {

    private static final Map<String, KingKit.Slot> ARMOUR_SLOTS = Map.of(
            "helmet", KingKit.Slot.HELMET,
            "chestplate", KingKit.Slot.CHESTPLATE,
            "leggings", KingKit.Slot.LEGGINGS,
            "boots", KingKit.Slot.BOOTS,
            "off-hand", KingKit.Slot.OFF_HAND);

    private KingSettingsLoader() {
    }

    /**
     * @param events the capture half, already loaded: KTK shares its switch and
     *               time zone, and must not reuse one of its ids
     */
    public static KingSettings load(ConfigurationSection root, EventSettings events, Consumer<String> warn) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(warn, "warn");
        List<KingEventDefinition> definitions = new ArrayList<>();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("kill-the-king");
        if (section != null) {
            // Ids are shared with the capture events: /events start <id> must mean
            // exactly one thing.
            Set<String> taken = new HashSet<>();
            for (CaptureEventDefinition capture : events.definitions()) {
                taken.add(capture.id().toLowerCase(Locale.ROOT));
            }
            for (String id : section.getKeys(false)) {
                if (!taken.add(id.toLowerCase(Locale.ROOT))) {
                    warn.accept("kill-the-king '" + id + "' has the id of another event (ids are "
                            + "case-insensitive and shared with the capture events); skipped.");
                    continue;
                }
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) {
                    warn.accept("kill-the-king '" + id + "' is not a section; skipped.");
                    continue;
                }
                KingEventDefinition definition = loadDefinition(id, entry, warn);
                if (definition != null) {
                    definitions.add(definition);
                }
            }
        }
        return new KingSettings(events.enabled(), events.timeZone(), definitions);
    }

    private static KingEventDefinition loadDefinition(String id, ConfigurationSection entry, Consumer<String> warn) {
        String world = entry.getString("world");
        if (world == null || world.isBlank()) {
            warn.accept("kill-the-king '" + id + "' has no world; skipped. Its warzone (claims.yml) "
                    + "is the event zone, so the world is required.");
            return null;
        }
        long duration = Durations.capSeconds(entry.getLong("duration-seconds", 0L), "duration-seconds", warn);
        if (duration <= 0) {
            warn.accept("kill-the-king '" + id + "' needs a positive duration-seconds; skipped.");
            return null;
        }
        int minimumPlayers = entry.getInt("minimum-players", 2);
        if (minimumPlayers < 1) {
            warn.accept("kill-the-king '" + id + "': minimum-players must be at least 1; using 2.");
            minimumPlayers = 2;
        }
        long announceSeconds = Durations.capSeconds(entry.getLong("announce-interval-seconds", 60L),
                "kill-the-king." + id + ".announce-interval-seconds", warn);
        if (announceSeconds < 0) {
            warn.accept("kill-the-king '" + id + "': announce-interval-seconds cannot be negative; using 60.");
            announceSeconds = 60L;
        }
        if (entry.contains("coordinates-interval-ticks")) {
            // Files written before the position moved to the scoreboard: a chat line every
            // second flooded it. Said once per load, so the operator knows it does nothing.
            warn.accept("kill-the-king '" + id + "': coordinates-interval-ticks is no longer used - the King's"
                    + " position is on the scoreboard (%king_location_line% in ui.yml), and in chat every"
                    + " announce-interval-seconds (60 by default). It can be deleted.");
        }
        String displayName = entry.getString("display-name", id);
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }
        return new KingEventDefinition(
                id,
                displayName,
                world.trim(),
                duration,
                minimumPlayers,
                EventSettingsLoader.loadMarks(entry.getLongList("announce-at-seconds"), id, warn),
                EventSettingsLoader.loadSchedule(entry.getStringList("schedule"), id, warn),
                announceSeconds,
                loadPenalty(entry.getConfigurationSection("outside-penalty"), id, warn),
                loadKit(entry.getConfigurationSection("kit"), id, warn),
                EventSettingsLoader.nonEmpty(entry.getStringList("reward-commands"), id, "reward-commands", warn),
                loadMode(entry.getString("mode", "team"), id, warn),
                loadReign(entry.getConfigurationSection("reign")));
    }

    private static ReignRules loadReign(ConfigurationSection section) {
        ReignRules defaults = ReignRules.defaults();
        if (section == null) {
            return defaults;
        }
        return new ReignRules(
                section.getBoolean("lock-armour", defaults.lockArmour()),
                section.getBoolean("drop-kit", defaults.dropKit()),
                section.getBoolean("death-costs-dtr", defaults.deathCostsDtr()),
                section.getBoolean("deathban", defaults.deathban()));
    }

    private static KingMode loadMode(String raw, String id, Consumer<String> warn) {
        return KingMode.of(raw).orElseGet(() -> {
            warn.accept("kill-the-king '" + id + "': mode '" + raw + "' is not team or solo; team is used.");
            return KingMode.TEAM;
        });
    }

    private static OutsidePenalty loadPenalty(ConfigurationSection section, String id, Consumer<String> warn) {
        OutsidePenalty defaults = OutsidePenalty.defaults();
        if (section == null) {
            return defaults;
        }
        try {
            return new OutsidePenalty(
                    Durations.capSeconds(section.getLong("grace-seconds", defaults.graceSeconds()), "grace-seconds", warn),
                    section.getDouble("damage-per-second", defaults.damagePerSecond()),
                    section.getInt("wither-start-level", defaults.witherStartLevel()),
                    Durations.capSeconds(section.getLong("wither-step-seconds", defaults.witherStepSeconds()), "wither-step-seconds", warn),
                    section.getInt("wither-max-level", defaults.witherMaxLevel()));
        } catch (IllegalArgumentException e) {
            warn.accept("kill-the-king '" + id + "': outside-penalty is unusable (" + e.getMessage()
                    + "); using the defaults.");
            return defaults;
        }
    }

    private static KingKit loadKit(ConfigurationSection section, String id, Consumer<String> warn) {
        if (section == null) {
            return KingKit.empty();
        }
        List<KingKit.Item> items = new ArrayList<>();
        for (Map.Entry<String, KingKit.Slot> slot : ARMOUR_SLOTS.entrySet()) {
            Object raw = section.get(slot.getKey());
            if (raw != null) {
                KingKit.Item item = loadItem(slot.getValue(), asMap(raw), id, warn);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        for (Map<?, ?> raw : section.getMapList("items")) {
            KingKit.Item item = loadItem(KingKit.Slot.INVENTORY, raw, id, warn);
            if (item != null) {
                items.add(item);
            }
        }

        Map<String, Integer> effects = new LinkedHashMap<>();
        ConfigurationSection effectSection = section.getConfigurationSection("effects");
        if (effectSection != null) {
            for (String name : effectSection.getKeys(false)) {
                int level = effectSection.getInt(name, 0);
                if (KingKitFactory.effect(name) == null) {
                    warn.accept("kill-the-king '" + id + "': effect '" + name + "' is not known to this "
                            + "server; left out of the kit.");
                } else if (level < 1) {
                    warn.accept("kill-the-king '" + id + "': effect '" + name + "' needs a level of at "
                            + "least 1; left out of the kit.");
                } else {
                    effects.put(name, level);
                }
            }
        }
        return new KingKit(items, effects);
    }

    private static KingKit.Item loadItem(KingKit.Slot slot, Map<?, ?> raw, String id, Consumer<String> warn) {
        Object materialName = raw.get("material");
        Material material = materialName == null ? null : KingKitFactory.material(materialName.toString());
        if (material == null) {
            warn.accept("kill-the-king '" + id + "': kit item '" + materialName + "' is not an item on "
                    + "this server; left out of the kit.");
            return null;
        }
        int amount = intValue(raw.get("amount"), 1);
        if (amount < 1) {
            warn.accept("kill-the-king '" + id + "': kit item " + material.name()
                    + " needs an amount of at least 1; using 1.");
            amount = 1;
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        Object rawEnchantments = raw.get("enchantments");
        if (rawEnchantments != null) {
            for (Map.Entry<?, ?> entry : asMap(rawEnchantments).entrySet()) {
                String name = String.valueOf(entry.getKey());
                int level = intValue(entry.getValue(), 0);
                if (KingKitFactory.enchantment(name) == null) {
                    warn.accept("kill-the-king '" + id + "': enchantment '" + name + "' is not known to "
                            + "this server; left off " + material.name() + ".");
                } else if (level < 1) {
                    warn.accept("kill-the-king '" + id + "': enchantment '" + name + "' needs a level of "
                            + "at least 1; left off " + material.name() + ".");
                } else {
                    enchantments.put(name, level);
                }
            }
        }
        Object name = raw.get("name");
        return new KingKit.Item(slot, material.name(), amount, enchantments,
                name == null || name.toString().isBlank() ? null : name.toString());
    }

    /**
     * @return a nested YAML block as a map, whether the configuration API handed
     *         it over as a section (under a named key) or as a plain map (inside a
     *         list)
     */
    private static Map<?, ?> asMap(Object raw) {
        if (raw instanceof ConfigurationSection section) {
            return section.getValues(false);
        }
        if (raw instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    private static int intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException ignored) {
                // falls through to the fallback
            }
        }
        return fallback;
    }
}
