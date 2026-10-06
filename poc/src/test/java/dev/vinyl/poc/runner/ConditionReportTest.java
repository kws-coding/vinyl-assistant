package dev.vinyl.poc.runner;

import static org.junit.jupiter.api.Assertions.*;

import dev.vinyl.poc.vision.AiCall;
import dev.vinyl.poc.vision.ConditionResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** All grades, defects and numbers here are made up. */
class ConditionReportTest {

    private static RecordInput rec(String media, String sleeve, String known) {
        return new RecordInput("007", "easy", media, sleeve, known, null, "", null, null, "", "", "");
    }

    private static ConditionResult result(String mediaGrade, String sleeveGrade) {
        return new ConditionResult(
                List.of(new ConditionResult.PhotoView("photo_1", "vinyl_side_a")),
                List.of(new ConditionResult.Defect("vinyl", "scratch", "photo_1", "near label", "fine scratch", "medium"),
                        new ConditionResult.Defect("sleeve", "ring_wear", "photo_2", "centre", "faint ring", "low")),
                new ConditionResult.GradeSuggestion(mediaGrade, "one scratch", "low"),
                new ConditionResult.GradeSuggestion(sleeveGrade, "ring wear", "medium"),
                List.of("side B not photographed"),
                new AiCall("m", 2, 1000, 500, new BigDecimal("0.007"), 10));
    }

    private static String col(List<String> row, String name) {
        return row.get(ConditionReport.HEADER.indexOf(name));
    }

    @Test
    void rowHasOneValuePerHeaderColumnAndStatesTheVisualOnlyLimit() {
        List<String> row = ConditionReport.row(rec("VG+", "VG", ""), result("VG+", "VG"), false);
        assertEquals(ConditionReport.HEADER.size(), row.size());
        assertTrue(col(row, "limits").contains("Visual only"));
        assertEquals("", col(row, "user_decision"));
    }

    @Test
    void aSuggestionBelowTheUsersGradeIsFlaggedAsPossiblyGenerous() {
        List<String> row = ConditionReport.row(rec("NM", "VG+", ""), result("VG", "VG+"), false);
        assertEquals("SUGGESTED_LOWER", col(row, "media_comparison"));
        assertEquals("2", col(row, "media_steps"));
        assertEquals("SAME", col(row, "sleeve_comparison"));
        assertTrue(col(row, "flag").contains("media: suggested is 2 steps below yours (yours may be generous)"));
        assertFalse(col(row, "flag").contains("sleeve"));
    }

    @Test
    void cannotTellAndABlankUserGradeAreNotFlagged() {
        List<String> row = ConditionReport.row(rec("", "VG+", ""), result("VG+", "cannot_tell"), false);
        assertEquals("CANNOT_COMPARE", col(row, "media_comparison"));
        assertEquals("CANNOT_COMPARE", col(row, "sleeve_comparison"));
        assertEquals("", col(row, "flag"));
    }

    @Test
    void costIsZeroForACachedResultAndCountsDefectsBySurface() {
        List<String> paid = ConditionReport.row(rec("VG+", "VG+", ""), result("VG+", "VG+"), false);
        List<String> cached = ConditionReport.row(rec("VG+", "VG+", ""), result("VG+", "VG+"), true);
        assertEquals("0.007", col(paid, "ai_cost_usd"));
        assertEquals("0", col(cached, "ai_cost_usd"));
        assertEquals("1", col(paid, "vinyl_defects"));
        assertEquals("1", col(paid, "sleeve_defects"));
    }

    @Test
    void reviewRowsListEveryObservedDefectAndEveryKnownDefectWithBlankVerdicts() {
        List<List<String>> rows = ConditionReport.reviewRows(rec("VG+", "VG+", "seam split, light scuff side A; plays quiet"),
                result("VG+", "VG+"));
        assertEquals(5, rows.size());
        assertEquals("observed", rows.get(0).get(1));
        assertTrue(rows.get(0).get(2).contains("fine scratch"));
        assertTrue(rows.get(0).get(3).contains("photo_1"));
        assertEquals("known", rows.get(2).get(1));
        assertEquals("seam split", rows.get(2).get(2));
        assertEquals("plays quiet", rows.get(4).get(2));
        rows.forEach(r -> assertEquals("", r.get(4)));
        assertEquals(ConditionReport.REVIEW_HEADER.size(), rows.get(0).size());
    }

    @Test
    void knownDefectsSplitOnCommasSemicolonsAndNewlinesAndIgnoreBlanks() {
        assertEquals(List.of("a", "b", "c"), ConditionReport.knownDefects("a, b;\nc, ,"));
        assertTrue(ConditionReport.knownDefects(null).isEmpty());
        assertTrue(ConditionReport.knownDefects("").isEmpty());
    }

    @Test
    void summaryCountsAgreementDirectionVerdictsAndDecisions() {
        List<Map<String, String>> report = List.of(
                Map.of("media_comparison", "SAME", "media_steps", "0", "sleeve_comparison", "SUGGESTED_HIGHER", "sleeve_steps", "1",
                        "flag", "sleeve: x", "user_decision", "keep", "vision_cached", "false", "ai_cost_usd", "0.04"),
                Map.of("media_comparison", "SUGGESTED_LOWER", "media_steps", "2", "sleeve_comparison", "CANNOT_COMPARE",
                        "sleeve_steps", "0", "flag", "media: y", "user_decision", "change", "vision_cached", "false",
                        "ai_cost_usd", "0.05"),
                Map.of("media_comparison", "SUGGESTED_LOWER", "media_steps", "1", "sleeve_comparison", "SAME", "sleeve_steps", "0",
                        "flag", "media: z", "user_decision", "", "vision_cached", "true", "ai_cost_usd", "0"));
        List<Map<String, String>> review = List.of(
                Map.of("item_type", "observed", "verdict", "real"), Map.of("item_type", "observed", "verdict", "false_alarm"),
                Map.of("item_type", "observed", "verdict", ""), Map.of("item_type", "known", "verdict", "found"),
                Map.of("item_type", "known", "verdict", "missed"));
        String s = ConditionSummary.render(report, review);
        assertTrue(s.contains("Media grade: compared 3, cannot compare 0"));
        assertTrue(s.contains("same as yours 1 (33%), within one step 2 (67%)"));
        assertTrue(s.contains("suggested below yours 2 (yours may be generous), above yours 0"));
        assertTrue(s.contains("Sleeve grade: compared 2, cannot compare 1"));
        assertTrue(s.contains("after seeing the flag you would change 1 and keep 1 (50% would change)"));
        assertTrue(s.contains("Observed defects: 3 (real 1, false alarm 1, unsure 0, not yet marked 1)"));
        assertTrue(s.contains("false alarms are 50% of the observed defects you marked real or false alarm"));
        assertTrue(s.contains("found by the tool 1, missed 1, not visible in photos 0, not yet marked 0"));
        assertTrue(s.contains("Media within one step of yours >= 70%: n/a (needs 8, have 3)"));
        assertTrue(s.contains("AI cost for paid records: $0.0900"));
    }

    @Test
    void conditionPhotoCapRefusesAboveTheLimit() throws Exception {
        ConditionMain.checkCap(8, 8);
        java.io.IOException e = assertThrows(java.io.IOException.class, () -> ConditionMain.checkCap(9, 8));
        assertTrue(e.getMessage().contains("9 condition photos"));
        assertTrue(e.getMessage().contains("limit of 8"));
    }

    @Test
    void knownDefectsMarkedNotVisibleDoNotCountAsMissedAndBarsAreJudgedOnlyWithEnoughData() {
        java.util.ArrayList<Map<String, String>> report = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            report.add(Map.of("media_comparison", i < 7 ? "SAME" : "SUGGESTED_LOWER", "media_steps", i < 7 ? "0" : "1",
                    "sleeve_comparison", "SAME", "sleeve_steps", "0", "suggested_media_grade", "VG+", "flag", "",
                    "vision_cached", "false", "ai_cost_usd", "0.05"));
        }
        java.util.ArrayList<Map<String, String>> review = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            review.add(Map.of("item_type", "known", "verdict", i < 4 ? "found" : "not_visible"));
        }
        for (int i = 0; i < 10; i++) {
            review.add(Map.of("item_type", "observed", "verdict", i < 8 ? "real" : "false_alarm"));
        }
        String s = ConditionSummary.render(report, review);
        assertTrue(s.contains("not visible in photos 2"));
        assertTrue(s.contains("Media within one step of yours >= 70%: PASS"));
        assertTrue(s.contains("Media two or more steps from yours (either way) <= 20%: PASS"));
        assertTrue(s.contains("False alarms <= 40% of marked observed defects: PASS"));
        assertTrue(s.contains("Missed <= 40% of your visible known defects: n/a (needs 5, have 4)"));
        assertTrue(s.contains("Cost per paid record: mean <= $0.06, none above $0.10: PASS"));
    }

    @Test
    void aBadResultFailsTheBars() {
        java.util.ArrayList<Map<String, String>> report = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            report.add(Map.of("media_comparison", "SUGGESTED_HIGHER", "media_steps", "3", "sleeve_comparison", "SAME",
                    "sleeve_steps", "0", "suggested_media_grade", i < 4 ? "cannot_tell" : "NM", "flag", "x",
                    "vision_cached", "false", "ai_cost_usd", "0.12"));
        }
        String s = ConditionSummary.render(report, List.of());
        assertTrue(s.contains("Media within one step of yours >= 70%: FAIL"));
        assertTrue(s.contains("Media two or more steps from yours (either way) <= 20%: FAIL"));
        assertTrue(s.contains("Media 'cannot_tell' <= 30% of records: FAIL"));
        assertTrue(s.contains("Cost per paid record: mean <= $0.06, none above $0.10: FAIL"));
    }
}
