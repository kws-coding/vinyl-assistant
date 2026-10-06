package dev.vinyl.poc.domain;

import java.util.List;

/** A candidate release with the score computed from its evidence. */
public record ScoredCandidate(String releaseId, double score, List<IdentifierEvidence> evidence) {

    public ScoredCandidate {
        evidence = List.copyOf(evidence);
    }

    public boolean hasMismatch() {
        return evidence.stream().anyMatch(e -> e.outcome() == Outcome.MISMATCH);
    }

    public boolean hasMatch(Identifier identifier) {
        return evidence.stream().anyMatch(e -> e.identifier() == identifier && e.outcome() == Outcome.MATCH);
    }

    public boolean isMissing(Identifier identifier) {
        return evidence.stream().noneMatch(e -> e.identifier() == identifier && e.outcome() != Outcome.MISSING);
    }
}
