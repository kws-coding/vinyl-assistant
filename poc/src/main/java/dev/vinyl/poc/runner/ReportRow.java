package dev.vinyl.poc.runner;

import java.util.List;

/** One report row per record. All values are text for CSV; blanks mean unknown or not applicable. */
public record ReportRow(String recordId, String bucket, String mediaGrade, String decision, String topReleaseId,
                        String truthReleaseId, String outcome, String topScore, String valueAtRisk,
                        String rivalReleaseId, String searchStage, String candidates, String photos,
                        String missingRoles, String suggestedPriceUsd, String priceMissing, String netBeforeCostDiscogsUsd, String profitDiscogsUsd,
                        String profitEbayUsd,
                        String ebaySoldAvg, String ebaySoldHigh, String ebayMatch, String ebayAlert,
                        String pricingErrorUsd, String errorGapUsd, String visionCached, String aiCostUsd,
                        String inputTokens, String outputTokens, String reason, String notes) {

    static final List<String> HEADER = List.of("record_id", "bucket", "media_grade", "decision", "top_release_id",
            "truth_release_id", "outcome", "top_score", "value_at_risk", "rival_release_id", "search_stage",
            "candidates", "photos", "missing_roles", "suggested_price_usd", "price_missing", "net_before_cost_discogs_usd", "profit_discogs_usd", "profit_ebay_usd", "ebay_sold_avg", "ebay_sold_high", "ebay_match", "ebay_alert",
            "pricing_error_usd", "error_gap_usd", "vision_cached", "ai_cost_usd", "input_tokens", "output_tokens", "reason",
            "notes", "human_minutes", "discogs_touches", "annoyance");

    List<String> fields() {
        return List.of(recordId, bucket, mediaGrade, decision, topReleaseId, truthReleaseId, outcome, topScore,
                valueAtRisk, rivalReleaseId, searchStage, candidates, photos, missingRoles, suggestedPriceUsd,
                priceMissing, netBeforeCostDiscogsUsd, profitDiscogsUsd, profitEbayUsd, ebaySoldAvg, ebaySoldHigh, ebayMatch, ebayAlert, pricingErrorUsd, errorGapUsd, visionCached, aiCostUsd, inputTokens, outputTokens,
                reason, notes, "", "", "");
    }
}
