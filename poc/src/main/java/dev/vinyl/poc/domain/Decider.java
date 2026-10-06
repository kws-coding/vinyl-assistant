package dev.vinyl.poc.domain;

import java.util.Comparator;
import java.util.List;

/**
 * Decides whether a record is confidently identified, needs the user's call, or needs runout photos.
 * Confident requires a high score, no mismatch, and value at risk within the limit.
 * Runout photos are asked for only when the release is otherwise pinned down (a barcode or catalog number
 * matched, nothing mismatched) and only the matrix is missing. A mismatch, or no barcode or catalog number
 * read at all, goes to the user with a reason that says what to look at.
 */
public class Decider {

    private final Thresholds thresholds;

    public Decider(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    public DecisionResult decide(List<PricedCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new DecisionResult(Decision.NEEDS_USER_CALL, null, new ValueAtRisk(0, null, 0),
                    "No candidate releases found.");
        }
        PricedCandidate top = candidates.stream()
                .max(Comparator.comparingDouble(p -> p.candidate().score())).orElseThrow();
        ScoredCandidate t = top.candidate();
        ValueAtRisk risk = ValueAtRisk.compute(candidates, thresholds.plausibleScore());

        boolean strong = t.score() >= thresholds.confidentScore() && !t.hasMismatch();
        boolean riskOk = risk.unpricedRivals() == 0 && risk.amount() <= thresholds.riskLimit();
        if (strong && riskOk) {
            return new DecisionResult(Decision.CONFIDENT, t.releaseId(), risk, "Strong match, risk within limit.");
        }

        String why = describe(t, risk);
        boolean pinnedDown = t.hasMatch(Identifier.BARCODE) || t.hasMatch(Identifier.CATALOG_NUMBER);
        boolean runoutsWouldHelp = t.isMissing(Identifier.MATRIX) && !t.hasMismatch() && pinnedDown;
        Decision decision = runoutsWouldHelp ? Decision.NEEDS_RUNOUT_PHOTOS : Decision.NEEDS_USER_CALL;
        return new DecisionResult(decision, t.releaseId(), risk, why);
    }

    private String describe(ScoredCandidate top, ValueAtRisk risk) {
        StringBuilder sb = new StringBuilder();
        if (top.score() < thresholds.confidentScore()) {
            sb.append("Top score ").append(String.format("%.2f", top.score())).append(" is below the confident level. ");
        }
        if (top.hasMismatch()) {
            sb.append("Top candidate has a mismatch. ");
        }
        if (risk.unpricedRivals() > 0) {
            sb.append(risk.unpricedRivals()).append(" plausible rival(s) have no price. ");
        } else if (risk.amount() > thresholds.riskLimit()) {
            sb.append("Value at risk ").append(String.format("%.2f", risk.amount()))
                    .append(" against rival ").append(risk.rivalReleaseId()).append(" is over the limit. ");
        }
        if (top.isMissing(Identifier.MATRIX)) {
            sb.append("No matrix/runout evidence. ");
        }
        if (!top.hasMatch(Identifier.BARCODE) && !top.hasMatch(Identifier.CATALOG_NUMBER)) {
            sb.append("No barcode or catalog number matched: photograph the barcode or catalog number. ");
        }
        return sb.toString().trim();
    }
}
