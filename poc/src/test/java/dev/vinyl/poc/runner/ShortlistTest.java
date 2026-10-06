package dev.vinyl.poc.runner;

import static org.junit.jupiter.api.Assertions.*;

import dev.vinyl.poc.domain.ExtractedFacts;
import dev.vinyl.poc.domain.Fact;
import dev.vinyl.poc.domain.Identifier;
import dev.vinyl.poc.domain.IdentifierEvidence;
import dev.vinyl.poc.domain.ReleaseInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Made-up releases and photo reads. */
class ShortlistTest {

    private static ReleaseInfo release(String id, String matrix) {
        return new ReleaseInfo(id, "Test Album", "Germany", 2020, List.of("CAT 1"), List.of("081227966409"),
                matrix.isEmpty() ? List.of() : List.of(matrix), List.of("Test Label"), List.of("LP", "Album"));
    }

    private static ExtractedFacts facts() {
        return new ExtractedFacts(List.of(new Fact("081227966409", "0 81227 96640 9")), List.of(), List.of(),
                List.of(), List.of(), List.of(new Fact("6016-01", "6016-01")));
    }

    @Test
    void candidatesComeInReleaseIdOrderWithNoScoresOrTopPick() {
        List<Shortlist.Entry> entries = List.of(
                new Shortlist.Entry(release("900", "BK06016-01"), List.of(IdentifierEvidence.match(Identifier.MATRIX, "6016-01"))),
                new Shortlist.Entry(release("100", "BD 16357-01"), List.of(IdentifierEvidence.mismatch(Identifier.MATRIX, "6016-01"))),
                new Shortlist.Entry(release("500", ""), List.of(IdentifierEvidence.missing(Identifier.MATRIX))));
        String s = Shortlist.render("007", facts(), "barcode", 3, entries);
        assertTrue(s.indexOf("  100  ") < s.indexOf("  500  "));
        assertTrue(s.indexOf("  500  ") < s.indexOf("  900  "));
        assertTrue(s.contains("no scores, no top pick"));
        assertFalse(s.matches("(?s).*\\b[01]\\.\\d\\d\\b.*"), "no score-like numbers");
        assertFalse(s.toLowerCase().contains("confident"));
    }

    @Test
    void showsTheRawPhotoTextTheListedRunoutsAndEachIdentifierOutcome() {
        List<Shortlist.Entry> entries = List.of(new Shortlist.Entry(release("100", "BD 16357-01"),
                List.of(IdentifierEvidence.mismatch(Identifier.MATRIX, "6016-01"),
                        IdentifierEvidence.match(Identifier.BARCODE, "0 81227 96640 9"))));
        String s = Shortlist.render("007", facts(), "barcode", 1, entries);
        assertTrue(s.contains("0 81227 96640 9"));
        assertTrue(s.contains("BD 16357-01"));
        assertTrue(s.contains("matrix MISMATCH"));
        assertTrue(s.contains("barcode match"));
        assertTrue(s.contains("https://www.discogs.com/release/100"));
    }

    @Test
    void anEmptyShortlistSaysSoAndNothingIsReadMeansNotRead() {
        ExtractedFacts none = new ExtractedFacts(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        String s = Shortlist.render("007", none, "none", 0, List.of());
        assertTrue(s.contains("No plausible release"));
        assertTrue(s.contains("barcode: (not read)"));
    }
}
