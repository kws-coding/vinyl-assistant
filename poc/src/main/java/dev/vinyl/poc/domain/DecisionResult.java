package dev.vinyl.poc.domain;

/** The decision, the top candidate it is about (null when there are none), the risk, and a plain-text reason. */
public record DecisionResult(Decision decision, String topReleaseId, ValueAtRisk risk, String reason) {
}
