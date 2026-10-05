package dev.vinyl.poc.domain;

import java.util.List;

/**
 * Scores a candidate from per-identifier evidence. A match adds its weight, a mismatch subtracts it,
 * and missing evidence adds nothing. The result is divided by the total weight and clamped to 0..1.
 */
public class CandidateScorer {

    private final ScoringWeights weights;

    public CandidateScorer(ScoringWeights weights) {
        this.weights = weights;
    }

    public ScoredCandidate score(String releaseId, List<IdentifierEvidence> evidence) {
        double sum = 0;
        for (IdentifierEvidence e : evidence) {
            double w = weights.of(e.identifier());
            switch (e.outcome()) {
                case MATCH -> sum += w;
                case MISMATCH -> sum -= w;
                case MISSING -> { }
            }
        }
        double total = weights.total();
        double score = total == 0 ? 0 : Math.max(0, Math.min(1, sum / total));
        return new ScoredCandidate(releaseId, score, evidence);
    }
}
