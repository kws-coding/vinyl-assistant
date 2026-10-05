package dev.vinyl.poc.domain;

import static dev.vinyl.poc.domain.Identifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeciderTest {

    private final CandidateScorer scorer = new CandidateScorer(ScoringWeights.defaults());
    private final Decider decider = new Decider(Thresholds.defaults());

    private PricedCandidate priced(String id, Double price, IdentifierEvidence... evidence) {
        return new PricedCandidate(scorer.score(id, List.of(evidence)), price);
    }

    private IdentifierEvidence[] beforeRunout() {
        return new IdentifierEvidence[] {
                IdentifierEvidence.match(BARCODE, "0 81227 96640 9"),
                IdentifierEvidence.match(CATALOG_NUMBER, "8122796640"),
                IdentifierEvidence.match(COUNTRY, "MANUFACTURED IN GERMANY."),
                IdentifierEvidence.match(LABEL, "ATLANTIC"),
                IdentifierEvidence.missing(MATRIX)};
    }

    @Test
    void record001WithoutRunoutPhotosNeedsRunoutPhotos() {
        // Two pressings tie on barcode and catalog number; test prices differ by 10.
        DecisionResult r = decider.decide(List.of(
                priced("19502752", 30.0, beforeRunout()),
                priced("5734489", 20.0, beforeRunout())));
        assertEquals(Decision.NEEDS_RUNOUT_PHOTOS, r.decision());
        assertTrue(r.risk().amount() > Thresholds.defaults().riskLimit());
    }

    @Test
    void record001WithRunoutEvidenceIsConfident() {
        DecisionResult r = decider.decide(List.of(
                priced("19502752", 30.0,
                        IdentifierEvidence.match(BARCODE, "0 81227 96640 9"),
                        IdentifierEvidence.match(CATALOG_NUMBER, "8122796640"),
                        IdentifierEvidence.match(MATRIX, "6016-01"),
                        IdentifierEvidence.match(COUNTRY, "MANUFACTURED IN GERMANY."),
                        IdentifierEvidence.match(LABEL, "ATLANTIC")),
                priced("23080868", 45.0,
                        IdentifierEvidence.mismatch(BARCODE, "0 81227 96640 9"),
                        IdentifierEvidence.mismatch(CATALOG_NUMBER, "8122796640"),
                        IdentifierEvidence.match(MATRIX, "6016-01"),
                        IdentifierEvidence.match(COUNTRY, "MANUFACTURED IN GERMANY."),
                        IdentifierEvidence.match(LABEL, "ATLANTIC"))));
        assertEquals(Decision.CONFIDENT, r.decision());
        assertEquals("19502752", r.topReleaseId());
    }

    @Test
    void closeRivalWithSmallPriceGapStaysConfident() {
        IdentifierEvidence[] full = {
                IdentifierEvidence.match(BARCODE, "x"), IdentifierEvidence.match(CATALOG_NUMBER, "x"),
                IdentifierEvidence.match(MATRIX, "x"), IdentifierEvidence.match(COUNTRY, "x"),
                IdentifierEvidence.match(LABEL, "x")};
        DecisionResult r = decider.decide(List.of(priced("A", 20.0, full), priced("B", 18.0, full)));
        assertEquals(Decision.CONFIDENT, r.decision());
    }

    @Test
    void unpricedPlausibleRivalIsNotConfident() {
        IdentifierEvidence[] full = {
                IdentifierEvidence.match(BARCODE, "x"), IdentifierEvidence.match(CATALOG_NUMBER, "x"),
                IdentifierEvidence.match(MATRIX, "x"), IdentifierEvidence.match(COUNTRY, "x"),
                IdentifierEvidence.match(LABEL, "x")};
        DecisionResult r = decider.decide(List.of(priced("A", 20.0, full), priced("B", null, full)));
        assertEquals(Decision.NEEDS_USER_CALL, r.decision());
        assertEquals(1, r.risk().unpricedRivals());
    }

    @Test
    void mismatchOnTopCandidateNeedsUserCall() {
        DecisionResult r = decider.decide(List.of(priced("A", 20.0,
                IdentifierEvidence.match(BARCODE, "x"), IdentifierEvidence.match(CATALOG_NUMBER, "x"),
                IdentifierEvidence.match(MATRIX, "x"), IdentifierEvidence.match(COUNTRY, "x"),
                IdentifierEvidence.mismatch(LABEL, "x"))));
        assertEquals(Decision.NEEDS_USER_CALL, r.decision());
    }

    @Test
    void noCandidatesNeedsUserCall() {
        assertEquals(Decision.NEEDS_USER_CALL, decider.decide(List.of()).decision());
    }
}
