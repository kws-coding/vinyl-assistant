package dev.vinyl.poc.discogs;

import java.util.List;

/** Discogs /database/search response. Only the fields we use. */
public record SearchResponse(List<Result> results) {

    public record Result(long id, String title, String country, String year, List<String> format,
                         List<String> label, String catno, List<String> barcode) {
    }
}
