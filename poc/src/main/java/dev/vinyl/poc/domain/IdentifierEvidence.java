package dev.vinyl.poc.domain;

/**
 * One piece of evidence for one candidate. Keeps the raw text it came from; null when not visible.
 */
public record IdentifierEvidence(Identifier identifier, Outcome outcome, String rawText) {

    public IdentifierEvidence {
        if (identifier == null || outcome == null) {
            throw new IllegalArgumentException("identifier and outcome are required");
        }
        if (outcome != Outcome.MISSING && (rawText == null || rawText.isBlank())) {
            throw new IllegalArgumentException("a match or mismatch must keep its raw text");
        }
    }

    public static IdentifierEvidence match(Identifier id, String rawText) {
        return new IdentifierEvidence(id, Outcome.MATCH, rawText);
    }

    public static IdentifierEvidence mismatch(Identifier id, String rawText) {
        return new IdentifierEvidence(id, Outcome.MISMATCH, rawText);
    }

    public static IdentifierEvidence missing(Identifier id) {
        return new IdentifierEvidence(id, Outcome.MISSING, null);
    }
}
