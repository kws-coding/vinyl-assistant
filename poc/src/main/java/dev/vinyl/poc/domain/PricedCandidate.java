package dev.vinyl.poc.domain;

/** A scored candidate with its suggested price at the user's grade; null when no price is known. */
public record PricedCandidate(ScoredCandidate candidate, Double price) {

    public boolean hasPrice() {
        return price != null;
    }
}
