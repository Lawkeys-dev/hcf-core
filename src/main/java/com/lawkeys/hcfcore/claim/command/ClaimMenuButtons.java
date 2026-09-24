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
 * player - {@code /team lockclaim}, {@code /team sethq}, {@code /team setbase} - so
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

    /** Puts the HQ, or the second base, where the player stands. */
    public static final class SetHome implements TeamMenuButton {

        private final ClaimModule claims;
        private final boolean hq;

        public SetHome(ClaimModule claims, boolean hq) {
            this.claims = claims;
            this.hq = hq;
        }

        @Override
        public String key() {
            return hq ? "sethq" : "setbase";
        }

        @Override
        public String defaultIcon() {
            return hq ? "RED_BED" : "WHITE_BED";
        }

        @Override
        public String name(Team team, Player viewer) {
            return claims.getLang().get(hq ? ClaimMessages.MENU_SETHQ : ClaimMessages.MENU_SETBASE);
        }

        @Override
        public List<String> lore(Team team, Player viewer) {
            return List.of(claims.getLang().get(hq ? ClaimMessages.MENU_SETHQ_LORE : ClaimMessages.MENU_SETBASE_LORE));
        }

        @Override
        public void click(Team team, Player viewer, ClickType click) {
            viewer.performCommand(hq ? "hcfcore:team sethq" : "hcfcore:team setbase");
        }
    }
}
