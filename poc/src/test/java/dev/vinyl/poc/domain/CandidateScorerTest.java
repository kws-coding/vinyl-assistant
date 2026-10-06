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

    @Test
    void noEvidenceScoresZero() {
        assertEquals(0.0, scorer.score("A", List.of()).score(), 1e-9);
    }

    @Test
    void aMatchAndAMismatchOffsetByWeight() {
        // barcode +3, catalog number -2 => 1 / 10.5
        ScoredCandidate c = scorer.score("A", List.of(
                IdentifierEvidence.match(BARCODE, "x"),
                IdentifierEvidence.mismatch(CATALOG_NUMBER, "y")));
        assertEquals(1.0 / 10.5, c.score(), 1e-9);
        assertTrue(c.hasMismatch());
    }

    @Test
    void identifierWithoutAWeightCountsForNothing() {
        CandidateScorer onlyBarcode = new CandidateScorer(new ScoringWeights(java.util.Map.of(BARCODE, 1.0)));
        ScoredCandidate c = onlyBarcode.score("A", List.of(
                IdentifierEvidence.match(BARCODE, "x"), IdentifierEvidence.match(LABEL, "y")));
        assertEquals(1.0, c.score(), 1e-9);
    }

    @Test
    void zeroTotalWeightScoresZero() {
        CandidateScorer none = new CandidateScorer(new ScoringWeights(java.util.Map.of()));
        assertEquals(0.0, none.score("A", List.of(IdentifierEvidence.match(BARCODE, "x"))).score(), 1e-9);
    }

    @Test
    void anIdentifierWithNoEvidenceEntryCountsAsMissing() {
        ScoredCandidate c = scorer.score("A", List.of(IdentifierEvidence.match(BARCODE, "x")));
        assertTrue(c.isMissing(MATRIX));
        assertFalse(c.hasMismatch());
    }
}
