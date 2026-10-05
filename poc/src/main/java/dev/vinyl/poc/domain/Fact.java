package dev.vinyl.poc.domain;

/** One value read from a photo, with the raw text it came from. */
public record Fact(String value, String rawText) {

    public Fact {
        if (value == null || value.isBlank() || rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("a fact needs a value and the raw text it came from");
        }
    }
}
