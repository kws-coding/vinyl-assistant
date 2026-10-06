package dev.vinyl.poc.runner;

import dev.vinyl.poc.domain.ProfitSettings;
import dev.vinyl.poc.domain.ScoringWeights;
import dev.vinyl.poc.domain.Thresholds;

/**
 * Everything the pipeline needs besides the clients. profit is null when real postage was not supplied.
 * maxPhotos caps cost: a record with more photos is not sent to the vision model.
 */
public record PipelineSettings(int maxCandidates, int maxImageEdge, int maxPhotos, ScoringWeights weights,
                               Thresholds thresholds, ProfitSettings profit) {
}
