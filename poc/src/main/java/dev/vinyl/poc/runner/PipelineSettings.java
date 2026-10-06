package dev.vinyl.poc.runner;

import dev.vinyl.poc.domain.ProfitSettings;
import dev.vinyl.poc.domain.ScoringWeights;
import dev.vinyl.poc.domain.Thresholds;

/**
 * Everything the pipeline needs besides the clients. maxPhotos caps cost: a record with more photos is not sent
 * to the vision model. postageIsPlaceholder is true when the real label cost was not supplied.
 */
public record PipelineSettings(int maxCandidates, int maxImageEdge, int maxPhotos, ScoringWeights weights,
                               Thresholds thresholds, ProfitSettings discogsProfit, ProfitSettings ebayProfit,
                               boolean postageIsPlaceholder) {
}
