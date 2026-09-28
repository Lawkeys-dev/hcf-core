package com.lawkeys.hcfcore.kit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A refill sign that would wipe a player's inventory asks for a second click: the
 * first says what will happen, the second - on the same sign, within the time -
 * hands the kit over (the owner's request of 28/09/2026). Pure.
 */
public final class KitConfirmations {

    private record Pending(String sign, long until) {
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /**
     * @param sign    what was clicked - the sign's place and its kit - so a click on
     *                another sign does not confirm this one
     * @param seconds how long the second click may wait; {@code 0} asks nothing
     * @return whether to go ahead: a confirming second click, or nothing to confirm.
     *         {@code false} means "asked, now wait for the second click"
     */
    public boolean confirm(UUID player, String sign, long now, long seconds) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(sign, "sign");
        if (seconds <= 0) {
            return true;
        }
        Pending waiting = pending.get(player);
        if (waiting != null && waiting.sign().equals(sign) && now <= waiting.until()) {
            pending.remove(player);
            return true;
        }
        pending.put(player, new Pending(sign, now + seconds * 1000L));
        return false;
    }

    public void forget(UUID player) {
        pending.remove(player);
    }
}
