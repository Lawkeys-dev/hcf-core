package com.lawkeys.hcfcore.events;

import com.lawkeys.hcfcore.api.event.EventWonEvent;
import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.util.RecordSigns;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A won event leaves a sign, as a kill does (the owner's request of 28/09/2026): the
 * event, who won it and when, written on the sign and never editable
 * ({@link RecordSigns}). To the King's winner, or to every online member of the
 * winning team ({@code capture-signs} in {@code events.yml}).
 */
public final class CaptureSigns implements Listener {

    /**
     * @param dateFormat how {@code %date%} is written, in {@code DateTimeFormatter} letters
     */
    public record Rules(boolean enabled, String material, String dateFormat) {

        public Rules {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(dateFormat, "dateFormat");
        }

        public static Rules defaults() {
            return new Rules(true, "OAK_SIGN", "dd/MM/yyyy HH:mm");
        }

        public static Rules load(ConfigurationSection section, Consumer<String> warn) {
            Rules d = defaults();
            if (section == null) {
                return d;
            }
            String material = section.getString("material", d.material());
            Material sign = material == null ? null : Material.matchMaterial(material.trim());
            if (sign == null || !sign.isItem() || !sign.name().endsWith("_SIGN")) {
                warn.accept("capture-signs.material '" + material + "' is not a sign; using " + d.material() + ".");
                material = d.material();
            }
            String format = section.getString("date-format", d.dateFormat());
            try {
                DateTimeFormatter.ofPattern(format);
            } catch (IllegalArgumentException | NullPointerException e) {
                warn.accept("capture-signs.date-format '" + format + "' is not a date pattern; using "
                        + d.dateFormat() + ".");
                format = d.dateFormat();
            }
            return new Rules(section.getBoolean("enabled", d.enabled()), material.trim(), format);
        }
    }

    public static final String NAME = "events.capture-sign.name";
    public static final String LINE_1 = "events.capture-sign.line-1";
    public static final String LINE_2 = "events.capture-sign.line-2";
    public static final String LINE_3 = "events.capture-sign.line-3";
    public static final String LINE_4 = "events.capture-sign.line-4";

    private final LangManager lang;
    private final Supplier<Rules> rules;

    public CaptureSigns(LangManager lang, Supplier<Rules> rules) {
        this.lang = Objects.requireNonNull(lang, "lang");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWon(EventWonEvent event) {
        Rules current = rules.get();
        if (!current.enabled()) {
            return;
        }
        List<Player> receivers = new ArrayList<>();
        String winner;
        if (event.getPlayer().isPresent()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(event.getPlayer().get());
            winner = player.getName() == null ? "?" : player.getName();
            if (player.getPlayer() != null) {
                receivers.add(player.getPlayer());
            }
        } else if (event.getTeam().isPresent()) {
            Team team = event.getTeam().get();
            winner = team.getName();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (team.isMember(online.getUniqueId())) {
                    receivers.add(online);
                }
            }
        } else {
            return;
        }
        Map<String, String> placeholders = Map.of("event", event.getDisplayName(), "winner", winner,
                "date", DateTimeFormatter.ofPattern(current.dateFormat()).format(ZonedDateTime.now()));
        List<String> lines = List.of(lang.get(LINE_1, placeholders), lang.get(LINE_2, placeholders),
                lang.get(LINE_3, placeholders), lang.get(LINE_4, placeholders));
        for (Player receiver : receivers) {
            ItemStack sign = RecordSigns.item(Material.matchMaterial(current.material()), lines,
                    lang.get(NAME, placeholders), lines);
            if (sign == null) {
                return;
            }
            for (ItemStack left : receiver.getInventory().addItem(sign).values()) {
                receiver.getWorld().dropItemNaturally(receiver.getLocation(), left);
            }
        }
    }
}
