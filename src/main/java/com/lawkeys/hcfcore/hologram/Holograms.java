package com.lawkeys.hcfcore.hologram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Every hologram, by id.
 *
 * <p>Pure Java; the cache is the truth and {@link #flush} writes what changed, with
 * the dirty-flag order settled on this branch - cleared before a row is read, put
 * back if the save fails. A deleted hologram is remembered until its row is gone.
 */
public final class Holograms {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,32}");

    /** More lines than this is a wall, not a hologram, and a text display draws them all at once. */
    public static final int MAX_LINES = 20;

    private final HologramStore store;
    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();

    public Holograms(HologramStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** @return the id as holograms are keyed - lower case - or {@code null} if it is not a valid one */
    public static String normalize(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        return VALID_ID.matcher(key).matches() ? key : null;
    }

    public Optional<Hologram> get(String id) {
        String key = normalize(id);
        return key == null ? Optional.empty() : Optional.ofNullable(holograms.get(key));
    }

    public List<Hologram> list() {
        List<Hologram> all = new ArrayList<>(holograms.values());
        all.sort(Comparator.comparing(Hologram::id));
        return all;
    }

    /** @return {@code false} if the id is not valid or already used */
    public boolean create(String id, String world, double x, double y, double z, List<String> lines) {
        String key = normalize(id);
        if (key == null || lines.size() > MAX_LINES
                || holograms.putIfAbsent(key, new Hologram(key, world, x, y, z, lines)) != null) {
            return false;
        }
        deleted.remove(key);
        dirty.add(key);
        return true;
    }

    /** @return {@code false} if there is no such hologram, or the lines would be too many */
    public boolean setLines(String id, List<String> lines) {
        if (lines.size() > MAX_LINES) {
            return false;
        }
        return update(id, hologram -> hologram.withLines(lines));
    }

    public boolean move(String id, String world, double x, double y, double z) {
        return update(id, hologram -> hologram.at(world, x, y, z));
    }

    public boolean delete(String id) {
        String key = normalize(id);
        if (key == null || holograms.remove(key) == null) {
            return false;
        }
        dirty.remove(key);
        deleted.add(key);
        return true;
    }

    private boolean update(String id, java.util.function.UnaryOperator<Hologram> change) {
        String key = normalize(id);
        if (key == null) {
            return false;
        }
        Hologram changed = holograms.computeIfPresent(key, (k, hologram) -> change.apply(hologram));
        if (changed == null) {
            return false;
        }
        dirty.add(key);
        return true;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void loadAll() throws Exception {
        store.initSchema();
        holograms.clear();
        dirty.clear();
        deleted.clear();
        for (Hologram hologram : store.loadAll()) {
            holograms.put(hologram.id(), hologram);
        }
    }

    public int flush() throws Exception {
        int written = 0;
        for (String id : Set.copyOf(deleted)) {
            deleted.remove(id);
            try {
                store.delete(id);
            } catch (Exception e) {
                deleted.add(id);
                throw e;
            }
            written++;
        }
        for (String id : Set.copyOf(dirty)) {
            dirty.remove(id);
            Hologram hologram = holograms.get(id);
            if (hologram == null) {
                continue;
            }
            try {
                store.save(hologram);
            } catch (Exception e) {
                dirty.add(id);
                throw e;
            }
            written++;
        }
        return written;
    }
}
