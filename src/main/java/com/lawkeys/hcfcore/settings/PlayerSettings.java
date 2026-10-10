package com.lawkeys.hcfcore.settings;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who has switched what off.
 *
 * <p>Pure Java. The cache is the truth at runtime (ARCHITECTURE.md section 3);
 * {@link #flush} writes what changed from an async task. The dirty mark is cleared
 * <em>before</em> a row is read for saving and put back if the save fails, so a
 * change made while a save is in flight is written by the next one rather than lost.
 */
public final class PlayerSettings {

    private final PlayerSettingsStore store;
    private final Map<UUID, Set<PlayerSetting>> disabled = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public PlayerSettings(PlayerSettingsStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** @return whether this setting is on for this player - everything is, until switched off */
    public boolean isOn(UUID playerId, PlayerSetting setting) {
        Set<PlayerSetting> off = disabled.get(playerId);
        return off == null || !off.contains(setting);
    }

    /** @return what this player has switched off */
    public Set<PlayerSetting> disabled(UUID playerId) {
        Set<PlayerSetting> off = disabled.get(playerId);
        return off == null ? Set.of() : Set.copyOf(off);
    }

    /** Switches a setting on or off; setting it to what it already is changes and writes nothing. */
    public void set(UUID playerId, PlayerSetting setting, boolean on) {
        Objects.requireNonNull(setting, "setting");
        boolean[] changed = {false};
        disabled.compute(playerId, (id, off) -> {
            Set<PlayerSetting> next = off == null ? EnumSet.noneOf(PlayerSetting.class) : EnumSet.copyOf(off);
            changed[0] = on ? next.remove(setting) : next.add(setting);
            return next.isEmpty() ? null : next;
        });
        if (changed[0]) {
            dirty.add(playerId);
        }
    }

    /** @return whether the setting is on now */
    public boolean toggle(UUID playerId, PlayerSetting setting) {
        boolean on = !isOn(playerId, setting);
        set(playerId, setting, on);
        return on;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void loadAll() throws Exception {
        store.initSchema();
        disabled.clear();
        dirty.clear();
        for (Map.Entry<UUID, Set<PlayerSetting>> entry : store.loadAll().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                disabled.put(entry.getKey(), EnumSet.copyOf(entry.getValue()));
            }
        }
    }

    public int flush() throws Exception {
        Set<UUID> toSave = Set.copyOf(dirty);
        if (toSave.isEmpty()) {
            return 0;
        }
        Map<UUID, Set<PlayerSetting>> batch = new HashMap<>();
        for (UUID playerId : toSave) {
            dirty.remove(playerId);
            batch.put(playerId, disabled(playerId));
        }
        try {
            store.saveAll(batch);
        } catch (Exception e) {
            dirty.addAll(toSave);
            throw e;
        }
        return toSave.size();
    }

    public int size() {
        return disabled.size();
    }
}
