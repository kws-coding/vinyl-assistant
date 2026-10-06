package dev.vinyl.poc.domain;

import static dev.vinyl.poc.domain.ConditionNote.Review.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.vinyl.poc.domain.ConditionNote.Draft;
import dev.vinyl.poc.domain.ConditionNote.NoteDefect;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Defects and notes here are made up. */
class ConditionNoteTest {

    private static NoteDefect vinyl(String desc, String where, String conf, ConditionNote.Review r) {
        return new NoteDefect("vinyl", desc, where, conf, r);
    }

    private static NoteDefect sleeve(String desc, String where, String conf, ConditionNote.Review r) {
        return new NoteDefect("sleeve", desc, where, conf, r);
    }

    @Test
    void usesAFixedTemplateWithReviewedDefectsAndTheUsersOwnNotes() {
        Draft d = ConditionNote.draft(List.of(
                        vinyl("Light scuff", "side A, near the label", "medium", REAL),
                        sleeve("Faint ring wear", "centre", "medium", REAL)),
                true, true, "Plays with light surface noise.");
        assertEquals("Vinyl: light scuff (side A, near the label). Sleeve: faint ring wear (centre). "
                + "Plays with light surface noise.", d.text());
        assertTrue(d.valid());
        assertTrue(d.warnings().isEmpty());
    }

    @Test
    void saysNoneSeenOnlyWhenBothSidesWerePhotographedAndOtherwiseWarnsInsteadOfClaiming() {
        Draft covered = ConditionNote.draft(List.of(), true, true, "");
        assertEquals("Vinyl: no marks seen in photos. Sleeve: no defects seen in photos.", covered.text());
        Draft partial = ConditionNote.draft(List.of(), false, true, "");
        assertEquals("Sleeve: no defects seen in photos.", partial.text());
        assertTrue(partial.warnings().stream().anyMatch(w -> w.contains("both sides of the vinyl")));
    }

    @Test
    void falseAlarmsAreLeftOutAndUnsureOnesAreLeftOutWithAWarning() {
        Draft d = ConditionNote.draft(List.of(
                        vinyl("glare streak", "side B", "high", FALSE_ALARM),
                        vinyl("possible scratch", "side A", "medium", UNSURE)),
                true, true, "");
        assertEquals("Vinyl: no marks seen in photos. Sleeve: no defects seen in photos.", d.text());
        assertTrue(d.warnings().stream().anyMatch(w -> w.contains("1 defect(s) you marked unsure were left out")));
    }

    @Test
    void unreviewedDefectsAreUsedWithAWarningUnlessTheyAreLowConfidence() {
        Draft d = ConditionNote.draft(List.of(
                        vinyl("scratch", "side A", "high", UNREVIEWED),
                        vinyl("speck", "side B", "low", UNREVIEWED)),
                true, true, "");
        assertTrue(d.text().contains("scratch (side A)"));
        assertFalse(d.text().contains("speck"));
        assertTrue(d.warnings().contains("1 defect(s) used before you reviewed them"));
        assertTrue(d.warnings().stream().anyMatch(w -> w.contains("1 low-confidence defect(s) left out")));
    }

    @Test
    void neverMentionsPlaybackUnlessTheUserWroteIt() {
        Draft d = ConditionNote.draft(List.of(vinyl("scuff", "side A", "high", REAL)), true, true, "");
        String lower = d.text().toLowerCase();
        assertFalse(lower.contains("play"));
        assertFalse(lower.contains("noise"));
        assertFalse(lower.contains("grade"));
    }

    @Test
    void staysWithinFiveHundredCharactersByDroppingTheLeastConfidentDefectsAndSaysSo() {
        java.util.ArrayList<NoteDefect> many = new java.util.ArrayList<>();
        many.add(vinyl("faint hairline mark", "side B", "low", REAL));
        for (int i = 0; i < 12; i++) {
            many.add(vinyl("distinct scratch number " + i + " running across several tracks", "side A", "high", REAL));
        }
        Draft d = ConditionNote.draft(many, true, true, "Seller note kept whole.");
        assertTrue(d.text().length() <= ConditionNote.MAX_CHARS);
        assertTrue(d.valid());
        assertTrue(d.text().endsWith("Seller note kept whole."));
        assertFalse(d.text().contains("hairline"), "the lowest-confidence defect goes first");
        assertTrue(d.warnings().stream().anyMatch(w -> w.startsWith("shortened:")));
    }

    @Test
    void notesTooLongOnTheirOwnAreReportedNotCutOff() {
        String longNotes = "x".repeat(520);
        Draft d = ConditionNote.draft(List.of(), true, true, longNotes);
        assertTrue(d.text().endsWith(longNotes));
        assertFalse(d.valid());
        assertTrue(d.warnings().stream().anyMatch(w -> w.contains("even without defects")));
    }

    @Test
    void htmlCharactersAreRemovedWithAWarningFromEverythingInTheText() {
        Draft d = ConditionNote.draft(List.of(vinyl("scratch <b>deep</b>", "side A", "high", REAL)), true, true,
                "Light <i>pops</i> track 2");
        assertFalse(d.text().contains("<"));
        assertFalse(d.text().contains(">"));
        assertTrue(d.valid());
        assertTrue(d.text().contains("Light ipops/i track 2"));
        assertTrue(d.warnings().stream().anyMatch(w -> w.contains("Discogs does not allow HTML")));
    }

    @Test
    void whitespaceInTheUsersNotesIsTidiedWithoutAWarning() {
        Draft d = ConditionNote.draft(List.of(), true, true, "  two   spaces\nand a newline ");
        assertTrue(d.text().endsWith("two spaces and a newline"));
        assertTrue(d.warnings().isEmpty());
    }
}
