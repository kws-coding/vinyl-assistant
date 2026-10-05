package dev.vinyl.poc.domain;

/** How one extracted value compares with the candidate release. */
public enum Outcome {
    MATCH,
    MISMATCH,
    /** Not visible in the photos, or the candidate lists no value to compare. */
    MISSING
}
