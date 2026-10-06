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

    // Boundary tests use weights where scores come out exact: both identifiers matched is 1.0, one is 0.5.
    private final CandidateScorer halves = new CandidateScorer(
            new ScoringWeights(java.util.Map.of(BARCODE, 1.0, LABEL, 1.0)));

    private PricedCandidate half(String id, double price, boolean labelMatches) {
        return new PricedCandidate(halves.score(id, List.of(
                IdentifierEvidence.match(BARCODE, "x"),
                labelMatches ? IdentifierEvidence.match(LABEL, "x") : IdentifierEvidence.missing(LABEL),
                IdentifierEvidence.match(MATRIX, "x"))), price);
    }

    @Test
    void riskExactlyAtTheLimitIsStillConfidentAndJustOverIsNot() {
        Decider d = new Decider(new Thresholds(0.7, 0.4, 5.0));
        // rival score 0.5, gap 10 => risk 5.0
        DecisionResult atLimit = d.decide(List.of(half("A", 20.0, true), half("B", 10.0, false)));
        assertEquals(5.0, atLimit.risk().amount(), 1e-9);
        assertEquals(Decision.CONFIDENT, atLimit.decision());
        // gap 11 => risk 5.5
        DecisionResult over = d.decide(List.of(half("A", 20.0, true), half("B", 9.0, false)));
        assertEquals(Decision.NEEDS_USER_CALL, over.decision());
        assertTrue(over.reason().contains("over the limit"));
        assertEquals("B", over.risk().rivalReleaseId());
    }

    @Test
    void scoreExactlyAtTheConfidentLevelIsConfident() {
        List<PricedCandidate> one = List.of(half("A", 20.0, false)); // score 0.5
        assertEquals(Decision.CONFIDENT, new Decider(new Thresholds(0.5, 0.4, 5.0)).decide(one).decision());
        DecisionResult below = new Decider(new Thresholds(0.51, 0.4, 5.0)).decide(one);
        assertEquals(Decision.NEEDS_USER_CALL, below.decision());
        assertTrue(below.reason().contains("below the confident level"));
    }

    @Test
    void highestScoreWinsWhateverTheListOrder() {
        IdentifierEvidence[] full = {
                IdentifierEvidence.match(BARCODE, "x"), IdentifierEvidence.match(CATALOG_NUMBER, "x"),
                IdentifierEvidence.match(MATRIX, "x"), IdentifierEvidence.match(COUNTRY, "x"),
                IdentifierEvidence.match(LABEL, "x")};
        DecisionResult r = decider.decide(List.of(
                priced("weak", 20.0, IdentifierEvidence.match(BARCODE, "x")),
                priced("strong", 20.0, full)));
        assertEquals("strong", r.topReleaseId());
        assertEquals(Decision.CONFIDENT, r.decision());
    }

    @Test
    void tiedPressingsAtTheSamePriceHaveNoMoneyAtRisk() {
        IdentifierEvidence[] full = {
                IdentifierEvidence.match(BARCODE, "x"), IdentifierEvidence.match(CATALOG_NUMBER, "x"),
                IdentifierEvidence.match(MATRIX, "x"), IdentifierEvidence.match(COUNTRY, "x"),
                IdentifierEvidence.match(LABEL, "x")};
        DecisionResult same = decider.decide(List.of(priced("A", 20.0, full), priced("B", 20.0, full)));
        assertEquals(Decision.CONFIDENT, same.decision());
        assertEquals(0.0, same.risk().amount(), 1e-9);
        DecisionResult apart = decider.decide(List.of(priced("A", 20.0, full), priced("B", 40.0, full)));
        assertEquals(Decision.NEEDS_USER_CALL, apart.decision());
        assertEquals(20.0 * 10.0 / 10.5, apart.risk().amount(), 1e-9); // gap 20 x rival score
    }

    @Test
    void reasonsNameWhatTheUserShouldLookAt() {
        IdentifierEvidence[] full = {
                IdentifierEvidence.match(BARCODE, "x"), IdentifierEvidence.match(CATALOG_NUMBER, "x"),
                IdentifierEvidence.match(MATRIX, "x"), IdentifierEvidence.match(COUNTRY, "x"),
                IdentifierEvidence.match(LABEL, "x")};
        assertTrue(decider.decide(List.of(priced("A", 20.0, full), priced("B", null, full)))
                .reason().contains("no price"));
        assertTrue(decider.decide(List.of(priced("A", 20.0,
                IdentifierEvidence.match(BARCODE, "x"), IdentifierEvidence.mismatch(LABEL, "x"),
                IdentifierEvidence.match(MATRIX, "x")))).reason().contains("mismatch"));
    }

    @Test
    void aMismatchWithNoRunoutsGoesToTheUserNotToRunoutPhotos() {
        DecisionResult r = decider.decide(List.of(priced("A", 20.0,
                IdentifierEvidence.match(BARCODE, "x"), IdentifierEvidence.mismatch(LABEL, "x"),
                IdentifierEvidence.missing(MATRIX))));
        assertEquals(Decision.NEEDS_USER_CALL, r.decision());
        assertTrue(r.reason().contains("mismatch"));
    }

    @Test
    void noBarcodeOrCatalogMatchGoesToTheUserWithAHintToPhotographThem() {
        DecisionResult r = decider.decide(List.of(priced("A", 20.0,
                IdentifierEvidence.match(LABEL, "x"), IdentifierEvidence.match(COUNTRY, "x"),
                IdentifierEvidence.missing(BARCODE), IdentifierEvidence.missing(CATALOG_NUMBER),
                IdentifierEvidence.missing(MATRIX))));
        assertEquals(Decision.NEEDS_USER_CALL, r.decision());
        assertTrue(r.reason().contains("photograph the barcode or catalog number"));
    }

    @Test
    void aCatalogNumberMatchAloneIsEnoughToAskForRunouts() {
        DecisionResult r = decider.decide(List.of(priced("A", 20.0,
                IdentifierEvidence.match(CATALOG_NUMBER, "x"), IdentifierEvidence.match(LABEL, "x"),
                IdentifierEvidence.missing(BARCODE), IdentifierEvidence.missing(MATRIX))));
        assertEquals(Decision.NEEDS_RUNOUT_PHOTOS, r.decision());
    }
}
