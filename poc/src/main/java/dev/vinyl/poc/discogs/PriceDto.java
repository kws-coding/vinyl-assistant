package dev.vinyl.poc.discogs;

/** One entry of /marketplace/price_suggestions/{id}, keyed by Discogs grade name in the response. */
public record PriceDto(String currency, double value) {
}
