package dev.vinyl.poc.runner;

import static org.junit.jupiter.api.Assertions.*;

import dev.vinyl.poc.domain.ConditionNote;
import dev.vinyl.poc.vision.AiCall;
import dev.vinyl.poc.vision.ConditionResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** All data here is made up. */
class ConditionNotesTest {

    private static final AiCall CALL = new AiCall("m", 4, 1, 1, BigDecimal.ZERO, 1);

    private static ConditionResult result(List<ConditionResult.PhotoView> views, ConditionResult.Defect... defects) {
        return new ConditionResult(views, List.of(defects), new ConditionResult.GradeSuggestion("VG+", "", "low"),
                new ConditionResult.GradeSuggestion("VG+", "", "low"), List.of(), CALL);
    }

    private static List<ConditionResult.PhotoView> allViews() {
        return List.of(new ConditionResult.PhotoView("photo_1", "vinyl_side_a"), new ConditionResult.PhotoView("photo_2", "vinyl_side_b"),
                new ConditionResult.PhotoView("photo_3", "sleeve_front"), new ConditionResult.PhotoView("photo_4", "sleeve_back"));
    }

    private static RecordInput rec(String listingNotes) {
        return new RecordInput("007", "", "VG+", "VG+", "", null, "", null, null, "", "", listingNotes);
    }

    private static Map<String, String> observed(String verdict) {
        return Map.of("item_type", "observed", "verdict", verdict);
    }

    private static final ConditionResult.Defect SCUFF =
            new ConditionResult.Defect("vinyl", "scuff", "photo_1", "side A", "Light scuff", "medium");
    private static final ConditionResult.Defect RING =
            new ConditionResult.Defect("sleeve", "ring_wear", "photo_3", "front", "Faint ring wear", "high");

    @Test
    void usesTheUsersVerdictsAndTheirOwnListingNotes() {
        ConditionNote.Draft d = ConditionNotes.draft(rec("Light surface noise on side B."), result(allViews(), SCUFF, RING),
                List.of(observed("real"), observed("false_alarm")));
        assertEquals("Vinyl: light scuff (side A). Sleeve: no defects seen in photos. Light surface noise on side B.", d.text());
        assertTrue(d.warnings().isEmpty());
    }

    @Test
    void anUnmarkedSheetTreatsDefectsAsNotReviewedAndSaysSo() {
        ConditionNote.Draft d = ConditionNotes.draft(rec(""), result(allViews(), SCUFF, RING), List.of());
        assertTrue(d.text().contains("light scuff (side A)"));
        assertTrue(d.warnings().stream().anyMatch(w -> w.contains("does not match the observed defects")));
    }

    @Test
    void anUnrecognisedVerdictIsNotReviewedWithAWarning() {
        ConditionNote.Draft d = ConditionNotes.draft(rec(""), result(allViews(), SCUFF), List.of(observed("yes")));
        assertTrue(d.warnings().stream().anyMatch(w -> w.contains("'yes' not recognised")));
    }

    @Test
    void missingSidesMeanNoNoneSeenStatement() {
        List<ConditionResult.PhotoView> oneSide = List.of(new ConditionResult.PhotoView("photo_1", "vinyl_side_a"),
                new ConditionResult.PhotoView("photo_2", "sleeve_front"));
        ConditionNote.Draft d = ConditionNotes.draft(rec(""), result(oneSide), List.of());
        assertEquals("", d.text());
        assertEquals(2, d.warnings().size());
    }
}
