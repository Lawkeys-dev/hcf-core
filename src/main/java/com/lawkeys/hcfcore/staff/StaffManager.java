package com.lawkeys.hcfcore.staff;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is in staff mode, who is hidden, who is listening to staff chat, and who may
 * build through protection.
 *
 * <p><strong>Nothing here is persisted, on purpose.</strong> Every state is a
 * session: a staff member who was vanished when the server went down is not
 * vanished when it comes back, the same way a combat tag does not survive a
 * restart. What <em>is</em> persisted is the one thing losing would hurt - the
 * survival inventory taken off them on entering staff mode, which
 * {@link StaffStashes} keeps in the database.
 *
 * <p>Pure Java, no Bukkit: the rules below are decided here and merely applied by
 * the server layer, which is what lets them be tested (ARCHITECTURE.md section 13).
 */
public final class StaffManager {

    /**
     * Why a player is hidden, which decides whether leaving staff mode un-hides them.
     *
     * <p>The distinction is the point. A staff member who typed {@code /vanish} and
     * <em>then</em> entered staff mode expects to still be hidden when they leave
     * it; one who was only hidden because staff mode hides people expects to come
     * back into view. Collapsing the two into a single flag gets one of those
     * wrong, and it is the kind of wrong that is noticed by walking into a raid
     * visible when you thought you were not.
     */
    public enum VanishSource {
        /** The player asked for it with the vanish command. */
        MANUAL,
        /** Staff mode turned it on, and leaving staff mode turns it back off. */
        STAFF_MODE
    }

    private final Set<UUID> inStaffMode = ConcurrentHashMap.newKeySet();
    private final Map<UUID, VanishSource> vanished = new ConcurrentHashMap<>();
    private final Set<UUID> staffChat = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffBuild = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------------------------
    // Staff mode
    // ------------------------------------------------------------------

    public boolean isInStaffMode(UUID playerId) {
        return inStaffMode.contains(playerId);
    }

    /**
     * @return {@code false}, changing nothing, if they were already in staff mode -
     *         entering twice would stash a toolbar as though it were a survival
     *         inventory, and the real one would be gone
     */
    public boolean enterStaffMode(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return inStaffMode.add(playerId);
    }

    /**
     * @return {@code true} if they were in staff mode and now are not
     */
    public boolean leaveStaffMode(UUID playerId) {
        return inStaffMode.remove(playerId);
    }

    public Set<UUID> staffModePlayers() {
        return Set.copyOf(inStaffMode);
    }

    // ------------------------------------------------------------------
    // Vanish
    // ------------------------------------------------------------------

    public boolean isVanished(UUID playerId) {
        return vanished.containsKey(playerId);
    }

    /**
     * Hides a player.
     *
     * @return {@code false} if they were already hidden, in which case the source is
     *         <strong>not</strong> changed: staff mode must not quietly take
     *         ownership of a vanish the player asked for themselves, or leaving it
     *         would reveal them
     */
    public boolean vanish(UUID playerId, VanishSource source) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(source, "source");
        return vanished.putIfAbsent(playerId, source) == null;
    }

    /** @return {@code true} if they were hidden and now are not */
    public boolean reveal(UUID playerId) {
        return vanished.remove(playerId) != null;
    }

    /**
     * Reveals a player only if staff mode is what hid them.
     *
     * @return {@code true} if they were revealed
     * @see VanishSource
     */
    public boolean revealIfStaffMode(UUID playerId) {
        return vanished.remove(playerId, VanishSource.STAFF_MODE);
    }

    public VanishSource vanishSource(UUID playerId) {
        return vanished.get(playerId);
    }

    public Set<UUID> vanishedPlayers() {
        return Set.copyOf(vanished.keySet());
    }

    // ------------------------------------------------------------------
    // Staff chat
    // ------------------------------------------------------------------

    /** @return whether their chat now goes to the staff channel */
    public boolean toggleStaffChat(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (staffChat.remove(playerId)) {
            return false;
        }
        staffChat.add(playerId);
        return true;
    }

    public boolean isStaffChatOn(UUID playerId) {
        return staffChat.contains(playerId);
    }

    // ------------------------------------------------------------------
    // Staff build
    // ------------------------------------------------------------------

    /** @return whether they may now build through protection */
    public boolean toggleStaffBuild(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (staffBuild.remove(playerId)) {
            return false;
        }
        staffBuild.add(playerId);
        return true;
    }

    public boolean hasStaffBuild(UUID playerId) {
        return staffBuild.contains(playerId);
    }

    // ------------------------------------------------------------------
    // Session end
    // ------------------------------------------------------------------

    /**
     * Forgets everything about a player who has left.
     *
     * <p>Called on quit. Staff mode itself is <em>not</em> forgotten here: the
     * server layer takes them out of it first, so their survival inventory is given
     * back before they go. Dropping the flag without that would leave the stash
     * owed and the toolbar saved as their real inventory.
     */
    public void forgetSession(UUID playerId) {
        vanished.remove(playerId);
        staffChat.remove(playerId);
        staffBuild.remove(playerId);
    }

    /** Drops every session state. Used on shutdown, after staff have been taken out of the mode. */
    public void clearAll() {
        inStaffMode.clear();
        vanished.clear();
        staffChat.clear();
        staffBuild.clear();
    }
}
