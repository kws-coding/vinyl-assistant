package dev.vinyl.poc.discogs;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Discogs /releases/{id} response. Only the fields we use. */
public record ReleaseDto(long id, String title, String country, Integer year, List<Artist> artists,
                         List<Label> labels, List<Format> formats, List<Identifier> identifiers,
                         @JsonProperty("num_for_sale") Integer numForSale,
                         @JsonProperty("lowest_price") Double lowestPrice) {

    public record Artist(String name) {
    }

    public record Label(String name, String catno) {
    }

    public record Format(String name, List<String> descriptions, String text) {
    }

    public record Identifier(String type, String value) {
    }
}
