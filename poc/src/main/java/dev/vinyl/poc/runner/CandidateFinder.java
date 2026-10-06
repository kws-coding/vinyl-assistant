package dev.vinyl.poc.runner;

import dev.vinyl.poc.discogs.DiscogsClient;
import dev.vinyl.poc.discogs.DiscogsException;
import dev.vinyl.poc.discogs.ReleaseMapper;
import dev.vinyl.poc.discogs.SearchResponse;
import dev.vinyl.poc.domain.ExtractedFacts;
import dev.vinyl.poc.domain.Fact;
import dev.vinyl.poc.domain.ReleaseInfo;
import dev.vinyl.poc.vision.VisionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Searches Discogs by barcode, then catalog number (plus label), then artist and title, and stops at the
 * first search that returns anything. Fetches the top candidates as plain release info (not stored).
 */
public class CandidateFinder {

    public record Found(String stage, List<ReleaseInfo> releases, int skipped) {
    }

    private final DiscogsClient discogs;
    private final int maxCandidates;

    public CandidateFinder(DiscogsClient discogs, int maxCandidates) {
        this.discogs = discogs;
        this.maxCandidates = maxCandidates;
    }

    public Found find(ExtractedFacts facts, VisionResult vision) {
        for (Fact b : facts.barcodes()) {
            String digits = b.value().replaceAll("[^0-9]", "");
            if (digits.length() >= 8) {
                List<SearchResponse.Result> r = results(() -> discogs.searchByBarcode(digits));
                if (!r.isEmpty()) {
                    return fetch("barcode", r);
                }
            }
        }
        String label = facts.labels().isEmpty() ? null : facts.labels().get(0).value();
        for (Fact c : facts.catalogNumbers()) {
            List<SearchResponse.Result> r = results(() -> label == null
                    ? discogs.searchByCatno(c.value()) : discogs.searchByCatnoAndLabel(c.value(), label));
            if (!r.isEmpty()) {
                return fetch("catalog_number", r);
            }
        }
        List<dev.vinyl.poc.vision.Observation> artists = vision.observationsFor("artist");
        List<dev.vinyl.poc.vision.Observation> titles = vision.observationsFor("title");
        if (!artists.isEmpty() && !titles.isEmpty()) {
            List<SearchResponse.Result> r =
                    results(() -> discogs.searchByArtistAndTitle(artists.get(0).value(), titles.get(0).value()));
            if (!r.isEmpty()) {
                return fetch("artist_title", r);
            }
        }
        return new Found("none", List.of(), 0);
    }

    private List<SearchResponse.Result> results(Supplier<SearchResponse> search) {
        SearchResponse r = search.get();
        return r == null || r.results() == null ? List.of() : r.results();
    }

    private Found fetch(String stage, List<SearchResponse.Result> results) {
        List<ReleaseInfo> out = new ArrayList<>();
        int skipped = 0;
        for (SearchResponse.Result r : results.stream().limit(maxCandidates).toList()) {
            try {
                out.add(ReleaseMapper.toInfo(discogs.getRelease(String.valueOf(r.id()))));
            } catch (DiscogsException e) {
                skipped++;
            }
        }
        return new Found(stage, out, skipped);
    }
}
