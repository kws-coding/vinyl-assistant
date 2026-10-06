package dev.vinyl.poc.runner;

import dev.vinyl.poc.discogs.DiscogsClient;
import dev.vinyl.poc.discogs.DiscogsException;
import dev.vinyl.poc.discogs.GradeMapper;
import dev.vinyl.poc.discogs.PriceDto;
import dev.vinyl.poc.domain.CandidateScorer;
import dev.vinyl.poc.domain.Decider;
import dev.vinyl.poc.domain.Decision;
import dev.vinyl.poc.domain.DecisionResult;
import dev.vinyl.poc.domain.EbayAlert;
import dev.vinyl.poc.domain.EbayMatch;
import dev.vinyl.poc.domain.EvidenceBuilder;
import dev.vinyl.poc.domain.ExtractedFacts;
import dev.vinyl.poc.domain.PricedCandidate;
import dev.vinyl.poc.domain.ProfitCalculator;
import dev.vinyl.poc.domain.ReleaseInfo;
import dev.vinyl.poc.domain.ScoredCandidate;
import dev.vinyl.poc.vision.FactsMapper;
import dev.vinyl.poc.vision.ImageResizer;
import dev.vinyl.poc.vision.PhotoRole;
import dev.vinyl.poc.vision.VisionCache;
import dev.vinyl.poc.vision.VisionClient;
import dev.vinyl.poc.vision.VisionResult;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One record, start to finish: resize photos, read them, find candidates, score each, decide, price the top
 * candidate at the user's media grade and estimate profit. Produces one report row.
 */
public class RecordPipeline {

    private final VisionClient vision;
    private final VisionCache cache;
    private final DiscogsClient discogs;
    private final PipelineSettings settings;
    private final Path resizedDir;
    private final boolean refresh;

    public RecordPipeline(VisionClient vision, VisionCache cache, DiscogsClient discogs, PipelineSettings settings,
                          Path resizedDir, boolean refresh) {
        this.vision = vision;
        this.cache = cache;
        this.discogs = discogs;
        this.settings = settings;
        this.resizedDir = resizedDir;
        this.refresh = refresh;
    }

    public ReportRow run(RecordInput rec, Path recordDir) throws IOException {
        List<String> notes = new ArrayList<>();
        if (!rec.notes().isEmpty()) {
            notes.add(rec.notes());
        }

        VisionResult read = cache.get(rec.id()).filter(r -> !refresh).orElse(null);
        boolean cached = read != null;
        if (!cached) {
            List<Path> photos = ImageResizer.listPhotos(recordDir);
            if (photos.isEmpty()) {
                throw new IOException("No photos found in " + recordDir.getFileName());
            }
            checkPhotoCap(photos.size(), settings.maxPhotos());
            List<Path> jpegs = ImageResizer.resize(photos, resizedDir.resolve(rec.id()), settings.maxImageEdge());
            read = vision.extract(jpegs);
            cache.put(rec.id(), read);
        }
        ExtractedFacts facts = FactsMapper.toFacts(read);

        CandidateFinder.Found found = new CandidateFinder(discogs, settings.maxCandidates()).find(facts, read);
        if (found.skipped() > 0) {
            notes.add(found.skipped() + " candidate(s) could not be fetched");
        }

        EvidenceBuilder evidence = new EvidenceBuilder();
        CandidateScorer scorer = new CandidateScorer(settings.weights());
        List<ScoredCandidate> scored = new ArrayList<>();
        for (ReleaseInfo r : found.releases()) {
            scored.add(scorer.score(r.releaseId(), evidence.build(facts, r)));
        }

        double plausible = settings.thresholds().plausibleScore();
        ScoredCandidate best = scored.stream().max(java.util.Comparator.comparingDouble(ScoredCandidate::score))
                .orElse(null);
        List<PricedCandidate> priced = new ArrayList<>();
        for (ScoredCandidate s : scored) {
            boolean needsPrice = s == best || s.score() >= plausible;
            Double price = needsPrice ? price(s.releaseId(), rec.mediaGrade(), notes) : null;
            priced.add(new PricedCandidate(s, price));
        }

        DecisionResult decision = new Decider(settings.thresholds()).decide(priced);
        if (found.releases().isEmpty()) {
            notes.add("no candidates from search stage " + found.stage());
        }

        Double topPrice = priced.stream().filter(p -> p.candidate().releaseId().equals(decision.topReleaseId()))
                .map(PricedCandidate::price).findFirst().orElse(null);
        if (topPrice != null && settings.postageIsPlaceholder()) {
            notes.add("net and profit use placeholder postage (POC_POSTAGE not set)");
        }
        String netDiscogs = topPrice == null ? ""
                : new ProfitCalculator(settings.discogsProfit()).netBeforeCost(BigDecimal.valueOf(topPrice))
                        .toPlainString();
        String profitDiscogs = "";
        String profitEbay = "";
        if (topPrice != null && rec.costBasis() != null) {
            BigDecimal price = BigDecimal.valueOf(topPrice);
            profitDiscogs = new ProfitCalculator(settings.discogsProfit()).estimate(price, rec.costBasis())
                    .profit().toPlainString();
            profitEbay = new ProfitCalculator(settings.ebayProfit()).estimate(price, rec.costBasis())
                    .profit().toPlainString();
        } else if (topPrice != null) {
            notes.add("profit not computed: cost_basis blank (net before cost is shown)");
        }

        String pricingError = "";
        if (topPrice != null && rec.ebaySoldAvg() != null) {
            pricingError = BigDecimal.valueOf(topPrice).subtract(rec.ebaySoldAvg())
                    .setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        // When the pick is wrong, how far apart the two prices are: what a confident mistake would have cost.
        String errorGap = "";
        String truth = rec.truthReleaseId();
        if (topPrice != null && !truth.isEmpty() && decision.topReleaseId() != null
                && !truth.equals(decision.topReleaseId())) {
            Double truthPrice = price(truth, rec.mediaGrade(), notes);
            if (truthPrice != null) {
                errorGap = String.format("%.2f", Math.abs(topPrice - truthPrice));
            }
        }

        double topScore = best == null ? 0 : best.score();
        return new ReportRow(rec.id(), rec.bucket(), rec.mediaGrade(), decision.decision().name(),
                nz(decision.topReleaseId()), rec.truthReleaseId(), outcome(decision, rec.truthReleaseId()),
                String.format("%.2f", topScore), String.format("%.2f", decision.risk().amount()),
                nz(decision.risk().rivalReleaseId()), found.stage(), String.valueOf(found.releases().size()),
                String.valueOf(read.photos().size()), missingRoles(read), topPrice == null ? "" : String.format("%.2f", topPrice),
                priceMissing(decision, topPrice), netDiscogs, profitDiscogs, profitEbay,
                rec.ebaySoldAvg() == null ? "" : rec.ebaySoldAvg().toPlainString(),
                rec.ebaySoldHigh() == null ? "" : rec.ebaySoldHigh().toPlainString(), rec.ebayMatch(),
                ebayAlert(topPrice, rec), pricingError, errorGap,
                String.valueOf(cached), cached ? "0" : read.call().costUsd().toPlainString(),
                cached ? "0" : String.valueOf(read.call().inputTokens()),
                cached ? "0" : String.valueOf(read.call().outputTokens()), decision.reason(),
                String.join("; ", notes));
    }

    /** Blank when there is nothing to warn about. Never changes the decision or the price. */
    static String ebayAlert(Double topPrice, RecordInput rec) {
        Double high = rec.ebaySoldHigh() == null ? null : rec.ebaySoldHigh().doubleValue();
        return EbayAlert.defaults().check(topPrice, high, EbayMatch.parse(rec.ebayMatch())).orElse("");
    }

    /** Refuses before any paid call. Photos are never dropped silently: one of them may be the runout. */
    static void checkPhotoCap(int photos, int max) throws IOException {
        if (photos > max) {
            throw new IOException("Not sent to the vision model: " + photos + " photos is over the limit of " + max
                    + " (POC_MAX_PHOTOS). Remove photos or raise the limit.");
        }
    }

    /** "true" when there is a top release but no price for it at the user's grade; blank when there is no top release. */
    static String priceMissing(DecisionResult d, Double topPrice) {
        if (d.topReleaseId() == null) {
            return "";
        }
        return String.valueOf(topPrice == null);
    }

    /** correct / flagged_correct / flagged_incorrect / WRONG_UNFLAGGED, or blank without an answer key. */
    static String outcome(DecisionResult d, String truth) {
        if (truth == null || truth.isEmpty()) {
            return "";
        }
        boolean right = truth.equals(d.topReleaseId());
        if (d.decision() == Decision.CONFIDENT) {
            return right ? "correct" : "WRONG_UNFLAGGED";
        }
        return right ? "flagged_correct" : "flagged_incorrect";
    }

    private static String missingRoles(VisionResult read) {
        Set<PhotoRole> roles = read.photos().stream().map(p -> p.role()).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        if (!roles.contains(PhotoRole.RUNOUT_A)) {
            missing.add("runout_a");
        }
        if (!roles.contains(PhotoRole.RUNOUT_B)) {
            missing.add("runout_b");
        }
        long unclear = read.photos().stream().filter(p -> p.role() == PhotoRole.UNCLEAR).count();
        if (unclear > 0) {
            missing.add(unclear + " photo(s) with unclear role");
        }
        return String.join(" ", missing);
    }

    private Double price(String releaseId, String grade, List<String> notes) {
        try {
            Map<String, PriceDto> suggestions = discogs.getPriceSuggestions(releaseId);
            String key = GradeMapper.suggestionKey(grade, suggestions.keySet()).orElse(null);
            if (key == null) {
                notes.add("no price suggestion for grade '" + grade + "' on " + releaseId);
                return null;
            }
            PriceDto p = suggestions.get(key);
            if (!"USD".equals(p.currency())) {
                notes.add("price for " + releaseId + " is in " + p.currency() + ", not USD; ignored");
                return null;
            }
            return p.value();
        } catch (DiscogsException e) {
            notes.add("no price for " + releaseId + ": " + e.getMessage());
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
