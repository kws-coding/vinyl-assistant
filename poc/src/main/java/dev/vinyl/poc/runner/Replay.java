package dev.vinyl.poc.runner;

import dev.vinyl.poc.discogs.DiscogsClient;
import dev.vinyl.poc.discogs.GradeMapper;
import dev.vinyl.poc.discogs.PriceDto;
import dev.vinyl.poc.domain.CandidateScorer;
import dev.vinyl.poc.domain.Decider;
import dev.vinyl.poc.domain.DecisionResult;
import dev.vinyl.poc.domain.EvidenceBuilder;
import dev.vinyl.poc.domain.ExtractedFacts;
import dev.vinyl.poc.domain.PricedCandidate;
import dev.vinyl.poc.domain.ReleaseInfo;
import dev.vinyl.poc.domain.ScoredCandidate;
import dev.vinyl.poc.domain.ScoringWeights;
import dev.vinyl.poc.domain.Thresholds;
import dev.vinyl.poc.vision.FactsMapper;
import dev.vinyl.poc.vision.Observation;
import dev.vinyl.poc.vision.VisionCache;
import dev.vinyl.poc.vision.VisionResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Throwaway. Replays records through search, scoring and the decider with parts of the cached vision result
 * removed or damaged, to see whether the tool flags the record or stays confidently wrong. Costs nothing: it
 * reads out/vision-cache and makes only Discogs calls (throttled). It never calls the vision model, so a
 * record with no cached result is skipped. Usage: Replay [--root DIR] [--only ID]
 * Needs DISCOGS_TOKEN. Prices come from Discogs suggestions at the record's media grade and are kept in memory only.
 */
public class Replay {

    private record Variant(String name, UnaryOperator<List<Observation>> change) {
    }

    private static UnaryOperator<List<Observation>> drop(String... fields) {
        Set<String> f = Set.of(fields);
        return obs -> obs.stream().filter(o -> !f.contains(o.field())).toList();
    }

    private static UnaryOperator<List<Observation>> keepOnly(String... fields) {
        Set<String> f = Set.of(fields);
        return obs -> obs.stream().filter(o -> f.contains(o.field())).toList();
    }

    private static UnaryOperator<List<Observation>> change(String field, UnaryOperator<String> fn) {
        return obs -> obs.stream().map(o -> o.field().equals(field)
                ? new Observation(o.field(), fn.apply(o.value()), o.rawText(), o.photo()) : o).toList();
    }

    private static final List<Variant> VARIANTS = List.of(
            new Variant("baseline (as read)", o -> o),
            new Variant("no barcode", drop("barcode_digits")),
            new Variant("no catalog number", drop("catalog_number")),
            new Variant("no barcode + no catalog no.", drop("barcode_digits", "catalog_number")),
            new Variant("no runouts", drop("matrix_text")),
            new Variant("no barcode + no runouts", drop("barcode_digits", "matrix_text")),
            new Variant("barcode only", keepOnly("barcode_digits")),
            new Variant("barcode misread (last digit)", change("barcode_digits", v -> v.substring(0, v.length() - 1) + "0")),
            new Variant("barcode truncated to 7 digits", change("barcode_digits", v -> {
                String d = v.replaceAll("[^0-9]", "").replaceFirst("^0+", "");
                return d.length() > 7 ? d.substring(0, 7) : d;
            })),
            new Variant("nothing but artist/title", keepOnly("artist", "title")));

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".");
        String only = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--root" -> root = Path.of(args[++i]);
                case "--only" -> only = args[++i];
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        VisionCache cache = new VisionCache(root.resolve("out/vision-cache"));
        DiscogsClient discogs = DiscogsClient.fromEnvironment(c -> { });
        Map<String, Double> prices = new HashMap<>();
        Thresholds thresholds = Thresholds.defaults();
        CandidateScorer scorer = new CandidateScorer(ScoringWeights.defaults());

        for (RecordInput rec : RecordsCsv.read(root.resolve("records.csv"))) {
            if (only != null && !only.equals(rec.id())) {
                continue;
            }
            VisionResult base = cache.get(rec.id()).orElse(null);
            if (base == null) {
                System.out.println("Record " + rec.id() + ": no cached vision result, skipped (run Main first)");
                continue;
            }
            System.out.printf("%nRecord %s  truth=%s  grade=%s%n", rec.id(), rec.truthReleaseId(), rec.mediaGrade());
            System.out.printf("%-32s %-6s %-20s %-9s %-6s %-8s %-8s %s%n",
                    "variant", "cands", "decision", "top", "score", "risk", "truth?", "outcome [search stage]");
            for (Variant v : VARIANTS) {
                replay(rec, base, v, discogs, prices, thresholds, scorer);
            }
        }
        System.out.println("# Data provided by Discogs (https://www.discogs.com). Synthetic damage on cached reads; not an accuracy measure.");
    }

    private static void replay(RecordInput rec, VisionResult base, Variant v, DiscogsClient discogs,
                               Map<String, Double> prices, Thresholds thresholds, CandidateScorer scorer) {
        VisionResult read = new VisionResult(base.photos(), v.change().apply(base.observations()), base.call());
        ExtractedFacts facts = FactsMapper.toFacts(read);
        CandidateFinder.Found found = new CandidateFinder(discogs, 15).find(facts, read);

        EvidenceBuilder evidence = new EvidenceBuilder();
        List<ScoredCandidate> scored = new ArrayList<>();
        boolean truthInSet = false;
        for (ReleaseInfo r : found.releases()) {
            truthInSet |= r.releaseId().equals(rec.truthReleaseId());
            scored.add(scorer.score(r.releaseId(), evidence.build(facts, r)));
        }
        ScoredCandidate best = scored.stream().max(Comparator.comparingDouble(ScoredCandidate::score)).orElse(null);
        List<PricedCandidate> priced = new ArrayList<>();
        for (ScoredCandidate s : scored) {
            boolean needsPrice = s == best || s.score() >= thresholds.plausibleScore();
            priced.add(new PricedCandidate(s, needsPrice ? price(discogs, prices, s.releaseId(), rec.mediaGrade()) : null));
        }

        DecisionResult d = new Decider(thresholds).decide(priced);
        System.out.printf("%-32s %-6d %-20s %-9s %-6.2f %-8.2f %-8s %s [%s]%n", v.name(), found.releases().size(),
                d.decision(), d.topReleaseId(), best == null ? 0 : best.score(), d.risk().amount(),
                truthInSet ? "in set" : "ABSENT", RecordPipeline.outcome(d, rec.truthReleaseId()), found.stage());
        System.out.println("     reason: " + d.reason());
    }

    private static Double price(DiscogsClient discogs, Map<String, Double> cache, String releaseId, String grade) {
        Double p = cache.computeIfAbsent(releaseId, id -> {
            try {
                Map<String, PriceDto> m = discogs.getPriceSuggestions(id);
                String key = GradeMapper.suggestionKey(grade, m.keySet()).orElse(null);
                PriceDto dto = key == null ? null : m.get(key);
                return dto == null || !"USD".equals(dto.currency()) ? -1.0 : dto.value();
            } catch (RuntimeException e) {
                return -1.0;
            }
        });
        return p < 0 ? null : p;
    }
}
