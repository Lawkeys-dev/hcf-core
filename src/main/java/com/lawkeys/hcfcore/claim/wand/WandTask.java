package com.lawkeys.hcfcore.claim.wand;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * What a claiming wand is drawing: a team's claim, server land for staff, an event's
 * zone. The wand itself only picks corners and shows them; the task says what the
 * rectangle would mean, and makes it so.
 */
public interface WandTask {

    /** @return what the wand is drawing, for its messages - a team's name, an event's id */
    String label();

    /**
     * @return lines to tell the holder once both corners are picked - a price, or why
     *         it would be refused - already translated and coloured; empty for none
     */
    List<String> preview(Player player, Selection selection);

    /**
     * Makes the selection so. The task tells the player how it went.
     *
     * @return {@code true} when done, which takes the wand away; {@code false} keeps
     *         it, so a refused claim can be redrawn
     */
    boolean confirm(Player player, Selection selection);
}
