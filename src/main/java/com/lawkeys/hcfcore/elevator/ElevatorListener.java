package com.lawkeys.hcfcore.elevator;

import com.lawkeys.hcfcore.lang.LangManager;
import com.lawkeys.hcfcore.util.LegacyText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.Optional;

/**
 * Where elevator signs are written and used. The rules are {@link ElevatorModule}'s.
 */
public final class ElevatorListener implements Listener {

    private final ElevatorModule module;

    public ElevatorListener(ElevatorModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    private static String plain(Component line) {
        return line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line).trim();
    }

    /** A sign written {@code [Elevator]} over a way becomes an elevator: coloured, and marked. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWrite(SignChangeEvent event) {
        ElevatorSettings settings = module.getSettings();
        if (!settings.enabled() || !plain(event.line(0)).equalsIgnoreCase(settings.header())) {
            return;
        }
        Player player = event.getPlayer();
        Optional<ElevatorRules.Direction> direction = module.direction(plain(event.line(1)));
        if (direction.isEmpty()) {
            module.getLang().send(player, ElevatorMessages.BAD_DIRECTION,
                    "up", settings.upWord(), "down", settings.downWord());
            return;
        }
        boolean up = direction.get() == ElevatorRules.Direction.UP;
        event.line(0, LegacyText.of(module.getLang().get(ElevatorMessages.SIGN_HEADER, "header", settings.header())));
        event.line(1, LegacyText.of(module.getLang().get(up ? ElevatorMessages.SIGN_UP : ElevatorMessages.SIGN_DOWN,
                "word", up ? settings.upWord() : settings.downWord())));
        Block block = event.getBlock();
        String way = direction.get().name();
        // The block entity takes the text after this event: marked a tick later, on the finished sign.
        module.getPlugin().getServer().getScheduler().runTask(module.getPlugin(), () -> {
            if (block.getState(false) instanceof Sign sign) {
                sign.getPersistentDataContainer().set(module.getKey(), PersistentDataType.STRING, way);
                sign.update(true, false);
            }
        });
        module.getLang().send(player, ElevatorMessages.CREATED, "way", up ? settings.upWord() : settings.downWord());
    }

    /**
     * A right-click on an elevator sign. At {@code HIGH}, after territory protection: a
     * sign on land its user may not touch takes nobody anywhere.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !module.getSettings().enabled()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (!Tag.ALL_SIGNS.isTagged(clicked.getType()) || !(clicked.getState(false) instanceof Sign sign)) {
            return;
        }
        String way = sign.getPersistentDataContainer().get(module.getKey(), PersistentDataType.STRING);
        if (way == null) {
            return;
        }
        // Never the sign editor, whatever happens next.
        event.setCancelled(true);
        if (event.useInteractedBlock() == Event.Result.DENY || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        ElevatorRules.Direction direction;
        try {
            direction = ElevatorRules.Direction.valueOf(way);
        } catch (IllegalArgumentException unknown) {
            return;
        }
        module.ride(event.getPlayer(), clicked, direction);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.forget(event.getPlayer());
    }
}
