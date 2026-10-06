package dev.vinyl.poc.runner;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** All rows and numbers here are made up. */
class SummaryTest {

    private static Map<String, String> row(String... kv) {
        java.util.HashMap<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void countsOutcomesPerBucketAndFlagsAHighValueUnflaggedError() {
        List<Map<String, String>> rows = List.of(
                row("record_id", "2", "bucket", "easy", "outcome", "correct", "vision_cached", "false", "ai_cost_usd", "0.03"),
                row("record_id", "3", "bucket", "easy", "outcome", "WRONG_UNFLAGGED", "top_release_id", "9",
                        "truth_release_id", "8", "error_gap_usd", "40.00", "vision_cached", "false", "ai_cost_usd", "0.04"),
                row("record_id", "4", "bucket", "ambiguous", "outcome", "flagged_correct", "decision", "NEEDS_USER_CALL"));
        String s = Summary.render(rows, 25, null);
        assertTrue(s.contains("Records in report: 3 (3 with an answer key)"));
        assertTrue(s.contains("HIGH VALUE, gap $40.00"));
        assertTrue(s.contains("Bar 1, unflagged high-value errors (>= $25): 1. FAIL"));
        assertTrue(s.contains("Bar 4, unflagged wrong, any value: 1 of 3. FAIL"));
        assertTrue(s.contains("easy"));
    }

    @Test
    void aWrongUnflaggedRecordUnderTheCutoffDoesNotFailBarOneButAnUnknownGapNeedsChecking() {
        String small = Summary.render(List.of(row("record_id", "2", "bucket", "easy", "outcome", "WRONG_UNFLAGGED",
                "error_gap_usd", "4.00")), 25, null);
        assertTrue(small.contains("Bar 1, unflagged high-value errors (>= $25): 0. PASS"));
        String unknown = Summary.render(List.of(row("record_id", "2", "bucket", "easy", "outcome", "WRONG_UNFLAGGED")),
                25, null);
        assertTrue(unknown.contains("price gap unknown"));
        assertTrue(unknown.contains("CHECK"));
    }

    @Test
    void costUsesOnlyPaidRecordsAndChecksTheDraftBar() {
        List<Map<String, String>> rows = List.of(
                row("record_id", "2", "outcome", "correct", "vision_cached", "false", "ai_cost_usd", "0.05"),
                row("record_id", "3", "outcome", "correct", "vision_cached", "true", "ai_cost_usd", "0"),
                row("record_id", "4", "outcome", "correct", "vision_cached", "false", "ai_cost_usd", "0.12"));
        String s = Summary.render(rows, 25, null);
        assertTrue(s.contains("AI cost over 2 paid record(s): mean $0.0850, max $0.1200. Bar 5: FAIL"));
    }

    @Test
    void pricingErrorIsGroupedByEbayMatchAndHasNoPassFail() {
        List<Map<String, String>> rows = List.of(
                row("record_id", "2", "pricing_error_usd", "-4.00", "ebay_match", "same"),
                row("record_id", "3", "pricing_error_usd", "10.00", "ebay_match", "same"),
                row("record_id", "4", "pricing_error_usd", "2.00", "ebay_match", ""));
        String s = Summary.render(rows, 25, null);
        assertTrue(s.contains("match=same: n=2, median absolute $7.00, median signed $3.00"));
        assertTrue(s.contains("match=unsure: n=1"));
        assertTrue(s.contains("under discussion"));
    }

    @Test
    void effortBarsUseMediansOfWhatTheUserFilledIn() {
        List<Map<String, String>> rows = List.of(
                row("record_id", "2", "human_minutes", "8", "discogs_touches", "1", "annoyance", "2"),
                row("record_id", "3", "human_minutes", "12", "discogs_touches", "0", "annoyance", "3"),
                row("record_id", "4", "human_minutes", "9", "discogs_touches", "2", "annoyance", "1"));
        String s = Summary.render(rows, 25, null);
        assertTrue(s.contains("Human minutes per record: median 9.0 over 3 record(s). Bar 2: PASS"));
        assertTrue(s.contains("Discogs touches per record: median 1.0"));
        assertTrue(s.contains("Annoyance (1 to 5): median 2.0"));
        assertTrue(Summary.render(List.of(row("record_id", "2")), 25, null).contains("not filled in yet"));
    }

    @Test
    void flagsForRunoutPhotosAreCountedAgainstMissingRunoutRoles() {
        List<Map<String, String>> rows = List.of(
                row("record_id", "2", "decision", "NEEDS_RUNOUT_PHOTOS", "missing_roles", "runout_a runout_b"),
                row("record_id", "3", "decision", "NEEDS_RUNOUT_PHOTOS", "missing_roles", ""));
        assertTrue(Summary.render(rows, 25, null)
                .contains("Flagged for runout photos: 2 (1 of them with no runout photo among the roles)"));
    }

    @Test
    void roleAccuracyIsShownWhenScoredAndSaysSoWhenNot() {
        PhotoRoleScorer.Totals t = new PhotoRoleScorer.Totals(1, 10, 9, 0, 1, List.of("record 1 x.jpg: model unclear, truth front"));
        String s = Summary.render(List.of(row("record_id", "2")), 25, t);
        assertTrue(s.contains("9 of 10 correct (90%), 0 wrong, 1 unclear"));
        assertTrue(s.contains("Bar 7: PASS"));
        assertTrue(s.contains("model unclear, truth front"));
        assertTrue(Summary.render(List.of(row("record_id", "2")), 25, null).contains("no photo_roles.csv"));
    }

    @Test
    void medianHandlesOddAndEvenCounts() {
        assertEquals(2.0, Summary.median(List.of(3.0, 1.0, 2.0)), 1e-9);
        assertEquals(2.5, Summary.median(List.of(4.0, 1.0, 2.0, 3.0)), 1e-9);
    }
}
