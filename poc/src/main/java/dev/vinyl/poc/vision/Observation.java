package dev.vinyl.poc.vision;

/** One value read from a photo, with the raw text it came from. Never a guess: absent values are omitted. */
public record Observation(String field, String value, String rawText, String photo) {
}
