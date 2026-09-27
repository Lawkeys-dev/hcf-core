package com.lawkeys.hcfcore.claim.command;

import com.lawkeys.hcfcore.claim.ClaimMessages;
import com.lawkeys.hcfcore.claim.ClaimModule;
import com.lawkeys.hcfcore.team.Team;
import com.lawkeys.hcfcore.team.TeamMenuButton;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * {@code claim/}'s buttons in {@code /team settings}. Each runs its command as the
 * player - {@code /team lockclaim}, {@code /team sethq} - so
 * the menu does exactly what the command does, checks and messages included.
 */
public final class ClaimMenuButtons {

    private ClaimMenuButtons() {
    }

    /** Locks or unlocks the claim; its state shows on it. During SOTW only. */
    public static final class Lock implements TeamMenuButton {

        private final ClaimModule claims;

        public Lock(ClaimModule claims) {
            this.claims = claims;
        }

        @Override
        public String key() {
            return "lock-claim";
        }

        @Override
        public String defaultIcon() {
            return "IRON_DOOR";
        }

        @Override
        public String name(Team team, Player viewer) {
            return claims.getLang().get(claims.getManager().isLocked(team.getId())
                    ? ClaimMessages.MENU_LOCK_ON : ClaimMessages.MENU_LOCK_OFF);
        }

        @Override
        public List<String> lore(Team team, Player viewer) {
            return List.of(claims.getLang().get(claims.getManager().canLock()
                    ? ClaimMessages.MENU_LOCK_LORE : ClaimMessages.MENU_LOCK_NOT_NOW));
        }

        @Override
        public void click(Team team, Player viewer, ClickType click) {
            viewer.performCommand("hcfcore:team lockclaim");
        }
    }

    /** Puts the HQ where the player stands. */
    public static final class SetHq implements TeamMenuButton {

        private final ClaimModule claims;

        public SetHq(ClaimModule claims) {
            this.claims = claims;
        }

        @Override
        public String key() {
            return "sethq";
        }

        @Override
        public String defaultIcon() {
            return "RED_BED";
        }

        @Override
        public String name(Team team, Player viewer) {
            return claims.getLang().get(ClaimMessages.MENU_SETHQ);
        }

        @Override
        public List<String> lore(Team team, Player viewer) {
            return List.of(claims.getLang().get(ClaimMessages.MENU_SETHQ_LORE));
        }

        @Override
        public void click(Team team, Player viewer, ClickType click) {
            viewer.performCommand("hcfcore:team sethq");
        }
    }
}
