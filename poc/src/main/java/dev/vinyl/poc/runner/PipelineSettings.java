package dev.vinyl.poc.runner;

import dev.vinyl.poc.domain.ProfitSettings;
import dev.vinyl.poc.domain.ScoringWeights;
import dev.vinyl.poc.domain.Thresholds;

/** Everything the pipeline needs besides the clients. profit is null when real postage was not supplied. */
public record PipelineSettings(int maxCandidates, int maxImageEdge, ScoringWeights weights, Thresholds thresholds,
                               ProfitSettings profit) {
}
