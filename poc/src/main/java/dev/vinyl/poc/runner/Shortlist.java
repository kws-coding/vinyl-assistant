package dev.vinyl.poc.runner;

import dev.vinyl.poc.discogs.DiscogsClient;
import dev.vinyl.poc.domain.CandidateScorer;
import dev.vinyl.poc.domain.EvidenceBuilder;
import dev.vinyl.poc.domain.ExtractedFacts;
import dev.vinyl.poc.domain.Fact;
import dev.vinyl.poc.domain.IdentifierEvidence;
import dev.vinyl.poc.domain.ReleaseInfo;
import dev.vinyl.poc.domain.ScoredCandidate;
import dev.vinyl.poc.domain.ScoringWeights;
import dev.vinyl.poc.domain.Thresholds;
import dev.vinyl.poc.vision.FactsMapper;
import dev.vinyl.poc.vision.VisionCache;
import dev.vinyl.poc.vision.VisionResult;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Throwaway. Narrows a record to its plausible Discogs releases so the user can confirm the answer key by
 * checking the physical record. Built to avoid anchoring on the tool's pick: candidates come in release id order,
 * with no scores and no top choice. Prints to the console only; Discogs content is not saved. It costs nothing:
 * it reads the vision cache and makes only Discogs calls. Usage: Shortlist [--root DIR] [--only ID] [--all]
 * --all shows every fetched candidate, not only the plausible ones, so a tool mistake that dropped the true
 * release can still be seen.
 */
public class Shortlist {

    /** One candidate with the evidence the photos gave for it. */
    record Entry(ReleaseInfo release, List<IdentifierEvidence> evidence) {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".");
        String only = null;
        boolean all = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--root" -> root = Path.of(args[++i]);
                case "--only" -> only = args[++i];
                case "--all" -> all = true;
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        VisionCache cache = new VisionCache(root.resolve("out/vision-cache"));
        DiscogsClient discogs = DiscogsClient.fromEnvironment(c -> { });
        double plausible = Thresholds.defaults().plausibleScore();
        CandidateScorer scorer = new CandidateScorer(ScoringWeights.defaults());
        EvidenceBuilder builder = new EvidenceBuilder();

        for (RecordInput rec : RecordsCsv.read(root.resolve("records.csv"))) {
            if (only != null && !only.equals(rec.id())) {
                continue;
            }
            VisionResult read = cache.get(rec.id()).orElse(null);
            if (read == null) {
                System.out.println("Record " + rec.id() + ": no cached vision result. Run Main first (that is a paid call).");
                continue;
            }
            ExtractedFacts facts = FactsMapper.toFacts(read);
            CandidateFinder.Found found = new CandidateFinder(discogs, 15).find(facts, read);
            List<Entry> entries = found.releases().stream().map(r -> new Entry(r, builder.build(facts, r))).toList();
            boolean showAll = all;
            List<Entry> shortlist = entries.stream()
                    .filter(e -> showAll || scorer.score(e.release().releaseId(), e.evidence()).score() >= plausible).toList();
            System.out.println(render(rec.id(), facts, found.stage(), entries.size(), shortlist));
        }
        System.out.println("# Data provided by Discogs (https://www.discogs.com).");
    }

    static String render(String recordId, ExtractedFacts facts, String stage, int fetched, List<Entry> shortlist) {
        StringBuilder out = new StringBuilder();
        out.append(String.format("%n=== Record %s ===%n", recordId));
        out.append("Read from the photos (raw text):\n");
        section(out, "barcode", facts.barcodes());
        section(out, "catalog number", facts.catalogNumbers());
        section(out, "label", facts.labels());
        section(out, "country", facts.countries());
        section(out, "format", facts.formats());
        section(out, "runout / matrix", facts.matrices());
        out.append(String.format("%nSearch stage: %s, %d fetched, %d plausible (release id order, no scores, no top pick)%n",
                stage, fetched, shortlist.size()));
        if (shortlist.isEmpty()) {
            out.append("No plausible release. Look this one up by hand, or add a barcode, catalog number or runout photo.\n");
        }
        shortlist.stream().sorted(Comparator.comparingLong(e -> Long.parseLong(e.release().releaseId()))).forEach(e -> {
            ReleaseInfo r = e.release();
            out.append(String.format("%n  %s  %s  [%s, %s]%n", r.releaseId(), r.title(), nz(r.country()),
                    r.year() == null ? "year ?" : r.year()));
            out.append("    https://www.discogs.com/release/").append(r.releaseId()).append('\n');
            out.append("    label / cat no: ").append(String.join("; ", r.labels())).append(" / ")
                    .append(String.join("; ", r.catalogNumbers())).append('\n');
            out.append("    format: ").append(String.join("; ", r.formatDescriptions())).append('\n');
            out.append("    barcodes: ").append(String.join("; ", r.barcodes())).append('\n');
            out.append("    runouts: ").append(r.matrices().isEmpty() ? "(none listed)" : String.join(" | ", r.matrices()))
                    .append('\n');
            out.append("    photos vs this release: ").append(e.evidence().stream()
                    .map(x -> x.identifier().name().toLowerCase().replace('_', ' ') + " " + word(x))
                    .collect(Collectors.joining(", "))).append('\n');
        });
        out.append("\nConfirm by checking the physical record (runout etchings first), then put the id in records.csv.\n");
        return out.toString();
    }

    private static void section(StringBuilder out, String name, List<Fact> facts) {
        out.append("  ").append(name).append(": ")
                .append(facts.isEmpty() ? "(not read)" : facts.stream().map(Fact::rawText).collect(Collectors.joining(" | ")))
                .append('\n');
    }

    private static String word(IdentifierEvidence e) {
        return switch (e.outcome()) {
            case MATCH -> "match";
            case MISMATCH -> "MISMATCH";
            case MISSING -> "-";
        };
    }

    private static String nz(String s) {
        return s == null ? "?" : s;
    }
}
