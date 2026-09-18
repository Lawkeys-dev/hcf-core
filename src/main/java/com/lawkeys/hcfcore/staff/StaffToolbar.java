package com.lawkeys.hcfcore.staff;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The set of items a staff member is handed on entering staff mode, by slot.
 *
 * <p>Built once from {@code staff.yml} and then immutable, so a {@code /hcf reload}
 * swaps a whole toolbar in rather than mutating the one staff are holding.
 *
 * <p>Two slots cannot share a number: the second would silently overwrite the
 * first, and an operator editing a long list is exactly who would not notice.
 * {@link #of} reports the clash and keeps the first, which is the one nearer the
 * top of the file.
 */
public final class StaffToolbar {

    private static final StaffToolbar EMPTY = new StaffToolbar(Map.of());

    private final Map<Integer, ToolbarItem> bySlot;

    private StaffToolbar(Map<Integer, ToolbarItem> bySlot) {
        this.bySlot = Map.copyOf(bySlot);
    }

    /** A toolbar with nothing in it - staff mode still works, it just hands out no items. */
    public static StaffToolbar empty() {
        return EMPTY;
    }

    /**
     * @param items the configured items, in file order
     * @param warn  told about every item dropped, and why
     */
    public static StaffToolbar of(List<ToolbarItem> items, Consumer<String> warn) {
        Objects.requireNonNull(warn, "warn");
        Map<Integer, ToolbarItem> bySlot = new LinkedHashMap<>();
        for (ToolbarItem item : Objects.requireNonNullElseGet(items, List::<ToolbarItem>of)) {
            if (item == null) {
                continue;
            }
            if (!item.hasValidSlot()) {
                warn.accept("slot " + item.slot() + " is outside 0-" + ToolbarItem.MAX_SLOT
                        + "; that item is ignored.");
                continue;
            }
            if (item.command().isBlank()) {
                warn.accept("the item in slot " + item.slot()
                        + " has no command, so using it would do nothing; it is ignored.");
                continue;
            }
            ToolbarItem clash = bySlot.putIfAbsent(item.slot(), item);
            if (clash != null) {
                warn.accept("two items both ask for slot " + item.slot()
                        + "; keeping '" + clash.command() + "' and ignoring '" + item.command() + "'.");
            }
        }
        return bySlot.isEmpty() ? EMPTY : new StaffToolbar(bySlot);
    }

    public Optional<ToolbarItem> at(int slot) {
        return Optional.ofNullable(bySlot.get(slot));
    }

    /** @return every item, keyed by the slot it goes in */
    public Map<Integer, ToolbarItem> items() {
        return bySlot;
    }

    public boolean isEmpty() {
        return bySlot.isEmpty();
    }

    public int size() {
        return bySlot.size();
    }
}
