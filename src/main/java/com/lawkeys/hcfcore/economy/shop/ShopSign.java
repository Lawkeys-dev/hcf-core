package com.lawkeys.hcfcore.economy.shop;

import java.util.List;
import java.util.Optional;

/**
 * A shop sign as staff write it - pure, unit-tested:
 * <pre>
 * [Buy]      or [Sell]
 * 16         how many per trade
 * Diamond    the item
 * 400        the price of those 16
 * </pre>
 *
 * @param material the item as written; the listener matches it against the server
 */
public record ShopSign(boolean buy, int amount, String material, double price) {

    /**
     * @param lines the four lines, colour codes stripped
     * @return the sign, or empty when it is not a shop sign or cannot be read
     */
    public static Optional<ShopSign> read(List<String> lines, String buyHeader, String sellHeader) {
        if (lines.size() < 4) {
            return Optional.empty();
        }
        String header = lines.get(0).trim();
        boolean buy = header.equalsIgnoreCase(buyHeader);
        if (!buy && !header.equalsIgnoreCase(sellHeader)) {
            return Optional.empty();
        }
        try {
            int amount = Integer.parseInt(lines.get(1).trim());
            String material = lines.get(2).trim();
            // The price as staff wrote it, or as the plugin rewrote it: "$1,200.00".
            double price = Double.parseDouble(lines.get(3).replaceAll("[^0-9.]", ""));
            if (amount < 1 || amount > 64 || material.isEmpty() || !(price > 0) || !Double.isFinite(price)) {
                return Optional.empty();
            }
            return Optional.of(new ShopSign(buy, amount, material, price));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** @return whether the first line is one of the two headers, however the rest reads */
    public static boolean isShopHeader(String firstLine, String buyHeader, String sellHeader) {
        String header = firstLine == null ? "" : firstLine.trim();
        return header.equalsIgnoreCase(buyHeader) || header.equalsIgnoreCase(sellHeader);
    }
}
