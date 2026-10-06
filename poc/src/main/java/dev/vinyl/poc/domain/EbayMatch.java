package dev.vinyl.poc.domain;

import java.util.Locale;

/** Whether the user could tell an eBay sale was the same pressing, usually from a runout, barcode or catalog number in the listing. */
public enum EbayMatch {
    SAME, UNSURE, NO;

    /** Blank means the user did not say, which is treated as unsure. Anything else must be same, unsure or no. */
    public static EbayMatch parse(String text) {
        if (text == null || text.isBlank()) {
            return UNSURE;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "same" -> SAME;
            case "unsure" -> UNSURE;
            case "no" -> NO;
            default -> throw new IllegalArgumentException("ebay_match must be same, unsure or no: " + text);
        };
    }
}
