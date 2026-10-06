package dev.vinyl.poc.domain;

import java.util.Optional;

/**
 * Tells the user when an eBay sale is far above the Discogs price, so they look before listing. It never
 * changes a decision or a price. The eBay figure is typed in by the user and may be a different pressing, so
 * a sale marked as another pressing raises nothing, and one not confirmed as the same pressing says so.
 *
 * @param minRatio  the eBay sale must be at least this many times the Discogs price
 * @param minGapUsd and at least this many dollars above it
 */
public record EbayAlert(double minRatio, double minGapUsd) {

    /** A starting guess, not a tuned value. */
    public static EbayAlert defaults() {
        return new EbayAlert(2.0, 20.0);
    }

    public Optional<String> check(Double discogsPrice, Double ebayHigh, EbayMatch match) {
        if (discogsPrice == null || ebayHigh == null || discogsPrice <= 0 || match == EbayMatch.NO) {
            return Optional.empty();
        }
        if (ebayHigh < discogsPrice * minRatio || ebayHigh - discogsPrice < minGapUsd) {
            return Optional.empty();
        }
        String pressing = match == EbayMatch.SAME ? "same pressing confirmed" : "pressing not confirmed";
        return Optional.of(String.format("eBay high sale $%.2f is %.1fx the Discogs price $%.2f (%s)",
                ebayHigh, ebayHigh / discogsPrice, discogsPrice, pressing));
    }
}
