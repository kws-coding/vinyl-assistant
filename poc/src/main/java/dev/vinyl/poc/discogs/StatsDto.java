package dev.vinyl.poc.discogs;

import com.fasterxml.jackson.annotation.JsonProperty;

/** /marketplace/stats/{id}: only the number for sale and the lowest price (no sales history). */
public record StatsDto(@JsonProperty("num_for_sale") int numForSale,
                       @JsonProperty("lowest_price") PriceDto lowestPrice) {
}
