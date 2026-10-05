package dev.vinyl.poc.domain;

import java.util.Comparator;
import java.util.List;

/**
 * Money at stake if the top candidate is wrong: for each other plausible candidate, the price gap to the
 * top candidate weighted by that candidate's score. The largest such value is reported.
 *
 * @param amount         largest weighted price gap, 0 when there are no priced rivals
 * @param rivalReleaseId the rival that produced it, or null
 * @param unpricedRivals plausible rivals whose price is unknown, so their gap could not be computed
 */
public record ValueAtRisk(double amount, String rivalReleaseId, int unpricedRivals) {

    public static ValueAtRisk compute(PricedCandidate top, List<PricedCandidate> others, double plausibleScore) {
        double best = 0;
        String bestId = null;
        int unpriced = 0;
        for (PricedCandidate other : others) {
            if (other.candidate().score() < plausibleScore) {
                continue;
            }
            if (!top.hasPrice() || !other.hasPrice()) {
                unpriced++;
                continue;
            }
            double risk = Math.abs(top.price() - other.price()) * other.candidate().score();
            if (risk > best) {
                best = risk;
                bestId = other.candidate().releaseId();
            }
        }
        return new ValueAtRisk(best, bestId, unpriced);
    }

    /** Picks the highest-scoring candidate, then computes the risk against the rest. */
    public static ValueAtRisk compute(List<PricedCandidate> all, double plausibleScore) {
        if (all.isEmpty()) {
            return new ValueAtRisk(0, null, 0);
        }
        PricedCandidate top = all.stream()
                .max(Comparator.comparingDouble(p -> p.candidate().score())).orElseThrow();
        List<PricedCandidate> others = all.stream().filter(p -> p != top).toList();
        return compute(top, others, plausibleScore);
    }
}
