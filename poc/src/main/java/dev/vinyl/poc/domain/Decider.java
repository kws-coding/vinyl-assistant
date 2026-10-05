package dev.vinyl.poc.domain;

import java.util.Comparator;
import java.util.List;

/**
 * Decides whether a record is confidently identified, needs the user's call, or needs runout photos.
 * Confident requires a high score, no mismatch, and value at risk within the limit.
 * Missing matrix evidence with an open question points at runout photos; anything else open goes to the user.
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
        Decision decision = t.isMissing(Identifier.MATRIX) ? Decision.NEEDS_RUNOUT_PHOTOS : Decision.NEEDS_USER_CALL;
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
        return sb.toString().trim();
    }
}
