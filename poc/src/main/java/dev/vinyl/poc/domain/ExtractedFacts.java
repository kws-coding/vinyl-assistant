package dev.vinyl.poc.domain;

import java.util.List;

/** Everything read from one record's photos, grouped by identifier. Plain values, no model types. */
public record ExtractedFacts(List<Fact> barcodes, List<Fact> catalogNumbers, List<Fact> labels,
                             List<Fact> countries, List<Fact> formats, List<Fact> matrices) {

    public ExtractedFacts {
        barcodes = List.copyOf(barcodes);
        catalogNumbers = List.copyOf(catalogNumbers);
        labels = List.copyOf(labels);
        countries = List.copyOf(countries);
        formats = List.copyOf(formats);
        matrices = List.copyOf(matrices);
    }
}
