package dev.vinyl.poc.domain;

import java.util.List;

/** What we know about a candidate release, as plain values. Built from Discogs data and not stored. */
public record ReleaseInfo(String releaseId, String title, String country, Integer year,
                          List<String> catalogNumbers, List<String> barcodes, List<String> matrices,
                          List<String> labels, List<String> formatDescriptions) {

    public ReleaseInfo {
        catalogNumbers = List.copyOf(catalogNumbers);
        barcodes = List.copyOf(barcodes);
        matrices = List.copyOf(matrices);
        labels = List.copyOf(labels);
        formatDescriptions = List.copyOf(formatDescriptions);
    }
}
