package com.lawkeys.hcfcore.pvp.deathsign;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * {@code pvp.yml}, {@code death-signs}: the sign a kill leaves behind - who died, to
 * whom, and when - the trophy of HCF servers (the project owner's request,
 * 23/09/2026). Its four lines are in the language file ({@code pvp.death-sign}).
 *
 * @param material   the sign, as the server spells it; any standing or hanging sign
 * @param toKiller   {@code true}: straight into the killer's inventory (dropped at
 *                   their feet when it is full); {@code false}: among the dead
 *                   player's drops, where loot protection covers it like the rest
 * @param dateFormat how {@code %date%} is written, in {@code DateTimeFormatter} letters
 */
public record DeathSignRules(boolean enabled, String material, boolean toKiller, String dateFormat) {

    public DeathSignRules {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(dateFormat, "dateFormat");
    }

    public static DeathSignRules defaults() {
        return new DeathSignRules(true, "OAK_SIGN", false, "dd/MM/yyyy HH:mm");
    }

    public static DeathSignRules load(ConfigurationSection section, Consumer<String> warn) {
        DeathSignRules d = defaults();
        if (section == null) {
            return d;
        }
        String material = section.getString("material", d.material());
        Material sign = material == null ? null : Material.matchMaterial(material.trim());
        if (sign == null || !sign.isItem() || !sign.name().endsWith("_SIGN")) {
            warn.accept("death-signs.material '" + material + "' is not a sign; using " + d.material() + ".");
            material = d.material();
        }
        String format = section.getString("date-format", d.dateFormat());
        try {
            DateTimeFormatter.ofPattern(format);
        } catch (IllegalArgumentException | NullPointerException e) {
            warn.accept("death-signs.date-format '" + format + "' is not a date pattern; using " + d.dateFormat() + ".");
            format = d.dateFormat();
        }
        return new DeathSignRules(section.getBoolean("enabled", d.enabled()), material.trim().toUpperCase(),
                section.getBoolean("to-killer", d.toKiller()), format);
    }
}
