package dev.vinyl.poc.domain;

import static dev.vinyl.poc.domain.Identifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateScorerTest {

    private final CandidateScorer scorer = new CandidateScorer(ScoringWeights.defaults());

    @Test
    void allMatchesScoreOne() {
        ScoredCandidate c = scorer.score("A", List.of(
                IdentifierEvidence.match(BARCODE, "0 81227 96640 9"),
                IdentifierEvidence.match(MATRIX, "BK06016-01"),
                IdentifierEvidence.match(CATALOG_NUMBER, "8122796640"),
                IdentifierEvidence.match(COUNTRY, "MANUFACTURED IN GERMANY."),
                IdentifierEvidence.match(LABEL, "ATLANTIC"),
                IdentifierEvidence.match(FORMAT, "STEREO")));
        assertEquals(1.0, c.score(), 1e-9);
        assertFalse(c.hasMismatch());
    }

    @Test
    void missingEvidenceAddsNothingAndIsNotAMismatch() {
        ScoredCandidate c = scorer.score("A", List.of(
                IdentifierEvidence.match(BARCODE, "0 81227 96640 9"),
                IdentifierEvidence.missing(MATRIX)));
        assertEquals(3.0 / 10.5, c.score(), 1e-9);
        assertFalse(c.hasMismatch());
        assertTrue(c.isMissing(MATRIX));
        assertFalse(c.isMissing(BARCODE));
    }

    @Test
    void mismatchSubtractsAndScoreNeverGoesBelowZero() {
        ScoredCandidate c = scorer.score("A", List.of(
                IdentifierEvidence.mismatch(BARCODE, "0 81227 96640 9"),
                IdentifierEvidence.mismatch(CATALOG_NUMBER, "8122796640")));
        assertEquals(0.0, c.score(), 1e-9);
        assertTrue(c.hasMismatch());
    }

    @Test
    void record001_correctReleaseOutranksDeluxeWithSameMatrix() {
        // Photos: barcode and catalog number read from cover, partial BK06016-01 on side B.
        ScoredCandidate correct = scorer.score("19502752", List.of(
                IdentifierEvidence.match(BARCODE, "0 81227 96640 9"),
                IdentifierEvidence.match(CATALOG_NUMBER, "8122796640"),
                IdentifierEvidence.match(MATRIX, "6016-01"),
                IdentifierEvidence.match(COUNTRY, "MANUFACTURED IN GERMANY."),
                IdentifierEvidence.match(LABEL, "ATLANTIC")));
        // Germany 2014 Deluxe: same BK matrix, but different barcode and catalog number.
        ScoredCandidate deluxe = scorer.score("23080868", List.of(
                IdentifierEvidence.mismatch(BARCODE, "0 81227 96640 9"),
                IdentifierEvidence.mismatch(CATALOG_NUMBER, "8122796640"),
                IdentifierEvidence.match(MATRIX, "6016-01"),
                IdentifierEvidence.match(COUNTRY, "MANUFACTURED IN GERMANY."),
                IdentifierEvidence.match(LABEL, "ATLANTIC")));
        assertTrue(correct.score() > deluxe.score());
        assertTrue(deluxe.hasMismatch());
    }

    @Test
    void matchOrMismatchWithoutRawTextIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> IdentifierEvidence.match(BARCODE, " "));
        assertThrows(IllegalArgumentException.class, () -> IdentifierEvidence.mismatch(BARCODE, null));
    }
}
