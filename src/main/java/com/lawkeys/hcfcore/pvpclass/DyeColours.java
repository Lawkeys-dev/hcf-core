package com.lawkeys.hcfcore.pvpclass;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Which dye colour a set of leather armour reads as.
 *
 * <p>A piece dyed with one dye carries exactly that dye's colour: the game averages
 * the dyes applied, and one dye averages to itself (read in the 26.2 sources,
 * {@code DyedItemColor#applyDyes}, which uses the dye's texture colour - the colour
 * Paper's {@code DyeColor#getColor} gives). A piece dyed with several is some colour
 * in between, and reads as the dye it is closest to. A set has a colour only when
 * all four pieces are dyed and read as the same one.
 */
public final class DyeColours {

    private DyeColours() {
    }

    /** @return the name of the dye in {@code palette} whose colour is closest to {@code rgb} */
    public static String nearest(int rgb, Map<String, Integer> palette) {
        Objects.requireNonNull(palette, "palette");
        String best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Map.Entry<String, Integer> dye : palette.entrySet()) {
            long distance = distance(rgb, dye.getValue());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = dye.getKey();
            }
        }
        return best;
    }

    /**
     * @param pieces the colour of each piece, or {@code null} for a piece that is
     *               not dyed (or not leather)
     * @return the dye the whole set reads as, or empty
     */
    public static Optional<String> ofSet(List<Integer> pieces, Map<String, Integer> palette) {
        if (pieces.isEmpty() || palette.isEmpty()) {
            return Optional.empty();
        }
        String colour = null;
        for (Integer piece : pieces) {
            if (piece == null) {
                return Optional.empty();
            }
            String nearest = nearest(piece, palette);
            if (colour != null && !colour.equals(nearest)) {
                return Optional.empty();
            }
            colour = nearest;
        }
        return Optional.ofNullable(colour);
    }

    private static long distance(int a, int b) {
        long red = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        long green = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        long blue = (a & 0xFF) - (b & 0xFF);
        return red * red + green * green + blue * blue;
    }
}
