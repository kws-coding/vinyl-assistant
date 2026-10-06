package dev.vinyl.poc.runner;

import static org.junit.jupiter.api.Assertions.*;

import dev.vinyl.poc.domain.Decision;
import dev.vinyl.poc.domain.DecisionResult;
import dev.vinyl.poc.domain.ValueAtRisk;
import org.junit.jupiter.api.Test;

class RecordPipelineTest {

    private static DecisionResult result(Decision decision, String top) {
        return new DecisionResult(decision, top, new ValueAtRisk(0, null, 0), "");
    }

    @Test
    void outcomeSeparatesCorrectFlaggedAndWrongUnflagged() {
        assertEquals("correct", RecordPipeline.outcome(result(Decision.CONFIDENT, "1"), "1"));
        assertEquals("WRONG_UNFLAGGED", RecordPipeline.outcome(result(Decision.CONFIDENT, "2"), "1"));
        assertEquals("flagged_correct", RecordPipeline.outcome(result(Decision.NEEDS_USER_CALL, "1"), "1"));
        assertEquals("flagged_incorrect", RecordPipeline.outcome(result(Decision.NEEDS_RUNOUT_PHOTOS, "2"), "1"));
    }

    @Test
    void outcomeIsBlankWithoutAnAnswerKey() {
        assertEquals("", RecordPipeline.outcome(result(Decision.CONFIDENT, "1"), null));
        assertEquals("", RecordPipeline.outcome(result(Decision.CONFIDENT, "1"), ""));
    }

    @Test
    void aNoCandidateResultIsFlaggedIncorrectNotUnflagged() {
        assertEquals("flagged_incorrect", RecordPipeline.outcome(result(Decision.NEEDS_USER_CALL, null), "1"));
    }

    @Test
    void priceMissingIsTrueOnlyWhenThereIsATopReleaseWithoutAPrice() {
        assertEquals("true", RecordPipeline.priceMissing(result(Decision.CONFIDENT, "1"), null));
        assertEquals("false", RecordPipeline.priceMissing(result(Decision.CONFIDENT, "1"), 20.0));
        assertEquals("", RecordPipeline.priceMissing(result(Decision.NEEDS_USER_CALL, null), null));
    }

    @Test
    void reportHeaderAndRowHaveTheSameNumberOfColumns() {
        ReportRow row = new ReportRow("1", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "", "");
        assertEquals(ReportRow.HEADER.size(), row.fields().size());
        assertTrue(ReportRow.HEADER.contains("price_missing"));
    }

    @Test
    void photoCapAllowsUpToTheLimitAndRefusesAboveItBeforeAnyCall() throws Exception {
        RecordPipeline.checkPhotoCap(11, 11);
        RecordPipeline.checkPhotoCap(1, 11);
        java.io.IOException e = assertThrows(java.io.IOException.class, () -> RecordPipeline.checkPhotoCap(12, 11));
        assertTrue(e.getMessage().contains("12 photos"));
        assertTrue(e.getMessage().contains("limit of 11"));
    }

    @Test
    void theEbayAlertUsesTheTopPriceAndNeverNeedsEbayData() {
        // made-up values
        RecordInput withHigh = new RecordInput("1", "", "VG+", "", "", null, "", null, new java.math.BigDecimal("90"),
                "same", "", "");
        assertTrue(RecordPipeline.ebayAlert(30.0, withHigh).contains("3.0x"));
        assertEquals("", RecordPipeline.ebayAlert(null, withHigh));
        RecordInput none = new RecordInput("1", "", "VG+", "", "", null, "", null, null, "", "", "");
        assertEquals("", RecordPipeline.ebayAlert(30.0, none));
    }
}
