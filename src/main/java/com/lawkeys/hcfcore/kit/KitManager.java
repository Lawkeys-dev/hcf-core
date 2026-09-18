package com.lawkeys.hcfcore.kit;

import com.lawkeys.hcfcore.util.Durations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The kits, and who may take which one when.
 *
 * <p>Pure Java. A cooldown is stored as the instant it ends rather than as time
 * remaining, for the same reason a deathban is: one that counted down only while
 * the server was up would be reset by every restart, and a twenty-four hour kit
 * would be worth nothing on a server that restarts nightly.
 */
public final class KitManager {

    private final KitStore store;
    private final LongSupplier clock;

    private final Map<String, Kit> kits = new ConcurrentHashMap<>();
    /** player to kit id to the instant their wait ends. */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Set<String> dirtyKits = ConcurrentHashMap.newKeySet();
    private final Set<String> deletedKits = ConcurrentHashMap.newKeySet();
    private final Set<UUID> dirtyCooldowns = ConcurrentHashMap.newKeySet();
    /** player to kit id to where they want its items. */
    private final Map<UUID, Map<String, KitLayout>> layouts = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyLayouts = ConcurrentHashMap.newKeySet();

    public KitManager(KitStore store) {
        this(store, System::currentTimeMillis);
    }

    public KitManager(KitStore store, LongSupplier clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<Kit> get(String id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(kits.get(id.toLowerCase(Locale.ROOT)));
    }

    /** @return every kit, ordered by id so a list is stable between calls */
    public List<Kit> all() {
        List<Kit> all = new ArrayList<>(kits.values());
        all.sort((a, b) -> a.id().compareToIgnoreCase(b.id()));
        return all;
    }

    public int size() {
        return kits.size();
    }

    /**
     * Creates or replaces a kit. Replacing one with other contents drops every layout
     * made for it: a layout moves slots, and the old slots no longer hold the items the
     * player arranged.
     */
    public void save(Kit kit) {
        Kit previous = kits.put(kit.id(), kit);
        deletedKits.remove(kit.id());
        dirtyKits.add(kit.id());
        if (previous != null && !Arrays.equals(previous.contents(), kit.contents())) {
            dropLayouts(kit.id());
        }
    }

    /** @return whether a kit of that id existed */
    public boolean delete(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (kits.remove(key) == null) {
            return false;
        }
        dirtyKits.remove(key);
        deletedKits.add(key);
        // Cooldowns for a kit that no longer exists are dead weight; drop them so a
        // recreated kit of the same name does not inherit somebody's old wait.
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            if (entry.getValue().remove(key) != null) {
                dirtyCooldowns.add(entry.getKey());
            }
        }
        dropLayouts(key);
        return true;
    }

    private void dropLayouts(String kitId) {
        for (Map.Entry<UUID, Map<String, KitLayout>> entry : layouts.entrySet()) {
            if (entry.getValue().remove(kitId) != null) {
                dirtyLayouts.add(entry.getKey());
            }
        }
    }

    // ------------------------------------------------------------------
    // Layouts
    // ------------------------------------------------------------------

    /** @return where this player wants this kit's items; {@link KitLayout#NONE} when they never chose */
    public KitLayout layout(UUID playerId, String kitId) {
        return layouts.getOrDefault(playerId, Map.of())
                .getOrDefault(kitId.toLowerCase(Locale.ROOT), KitLayout.NONE);
    }

    /** Records a layout; an empty one - every item where the kit has it - forgets it. */
    public void setLayout(UUID playerId, String kitId, KitLayout layout) {
        String key = kitId.toLowerCase(Locale.ROOT);
        if (layout.isEmpty()) {
            Map<String, KitLayout> theirs = layouts.get(playerId);
            if (theirs == null || theirs.remove(key) == null) {
                return;
            }
        } else {
            layouts.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(key, layout);
        }
        dirtyLayouts.add(playerId);
    }

    // ------------------------------------------------------------------
    // Cooldowns
    // ------------------------------------------------------------------

    /** @return seconds still to wait, or {@code 0} when the kit may be taken now */
    public long remainingCooldown(UUID playerId, String kitId) {
        Long expiry = cooldowns.getOrDefault(playerId, Map.of()).get(kitId.toLowerCase(Locale.ROOT));
        if (expiry == null) {
            return 0L;
        }
        long remaining = expiry - clock.getAsLong();
        return Durations.secondsLeft(remaining);
    }

    public boolean isOnCooldown(UUID playerId, String kitId) {
        return remainingCooldown(playerId, kitId) > 0;
    }

    /** Starts this player's wait for this kit. A kit with no cooldown records nothing. */
    public void markUsed(UUID playerId, Kit kit) {
        if (kit.cooldownSeconds() <= 0) {
            return;
        }
        cooldowns.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .put(kit.id(), clock.getAsLong() + kit.cooldownSeconds() * 1000L);
        dirtyCooldowns.add(playerId);
    }

    /** Clears a player's waits, all of them or one. Staff use this to let somebody re-take a kit. */
    public void clearCooldowns(UUID playerId, String kitId) {
        Map<String, Long> theirs = cooldowns.get(playerId);
        if (theirs == null) {
            return;
        }
        if (kitId == null) {
            theirs.clear();
        } else {
            theirs.remove(kitId.toLowerCase(Locale.ROOT));
        }
        dirtyCooldowns.add(playerId);
    }

    /**
     * Drops waits that have already ended.
     *
     * <p>Called periodically: without it a server would carry a row for every kit
     * every player has ever taken, none of which mean anything any more.
     *
     * @return how many were dropped
     */
    public int purgeExpired() {
        long now = clock.getAsLong();
        int dropped = 0;
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            Map<String, Long> theirs = entry.getValue();
            int before = theirs.size();
            theirs.entrySet().removeIf(cooldown -> cooldown.getValue() <= now);
            if (theirs.size() != before) {
                dropped += before - theirs.size();
                dirtyCooldowns.add(entry.getKey());
            }
        }
        cooldowns.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return dropped;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void loadAll() throws Exception {
        store.initSchema();
        kits.clear();
        cooldowns.clear();
        dirtyKits.clear();
        deletedKits.clear();
        dirtyCooldowns.clear();
        layouts.clear();
        dirtyLayouts.clear();
        for (Kit kit : store.loadKits()) {
            kits.put(kit.id(), kit);
        }
        for (Map.Entry<UUID, Map<String, Long>> entry : store.loadCooldowns().entrySet()) {
            cooldowns.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
        }
        for (Map.Entry<UUID, Map<String, String>> entry : store.loadLayouts().entrySet()) {
            Map<String, KitLayout> theirs = new ConcurrentHashMap<>();
            entry.getValue().forEach((kitId, stored) -> {
                KitLayout layout = KitLayout.parse(stored);
                if (!layout.isEmpty()) {
                    theirs.put(kitId, layout);
                }
            });
            if (!theirs.isEmpty()) {
                layouts.put(entry.getKey(), theirs);
            }
        }
    }

    /**
     * Writes what changed since the last flush.
     *
     * <p>Each mark is cleared <em>before</em> its row is read, and put back if the
     * write fails: a change made while the write is in flight marks the row again and
     * is written next time, instead of being cleared with the mark it arrived after.
     */
    public int flush() throws Exception {
        int written = 0;
        for (String id : Set.copyOf(deletedKits)) {
            deletedKits.remove(id);
            if (kits.containsKey(id)) {
                continue;
            }
            try {
                store.deleteKit(id);
                written++;
            } catch (Exception e) {
                deletedKits.add(id);
                throw e;
            }
        }
        for (String id : Set.copyOf(dirtyKits)) {
            dirtyKits.remove(id);
            Kit kit = kits.get(id);
            if (kit == null) {
                continue;
            }
            try {
                store.saveKit(kit);
                written++;
            } catch (Exception e) {
                dirtyKits.add(id);
                throw e;
            }
        }
        for (UUID playerId : Set.copyOf(dirtyCooldowns)) {
            dirtyCooldowns.remove(playerId);
            try {
                store.saveCooldowns(playerId, Map.copyOf(cooldowns.getOrDefault(playerId, Map.of())));
                written++;
            } catch (Exception e) {
                dirtyCooldowns.add(playerId);
                throw e;
            }
        }
        for (UUID playerId : Set.copyOf(dirtyLayouts)) {
            dirtyLayouts.remove(playerId);
            Map<String, String> stored = new java.util.LinkedHashMap<>();
            layouts.getOrDefault(playerId, Map.of()).forEach((kitId, layout) -> stored.put(kitId, layout.format()));
            try {
                store.saveLayouts(playerId, stored);
                written++;
            } catch (Exception e) {
                dirtyLayouts.add(playerId);
                throw e;
            }
        }
        return written;
    }

    /** @return every kit id, for tab completion */
    public Collection<String> ids() {
        return Set.copyOf(kits.keySet());
    }
}
