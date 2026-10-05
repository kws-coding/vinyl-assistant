package dev.vinyl.poc.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Record 001 end to end, without any API call: the model readings from a live run (copied in by hand)
 * against three made-up candidate releases that have the properties of the real ones.
 */
class Record001PipelineTest {

    private static Fact f(String value, String raw) {
        return new Fact(value, raw);
    }

    // What the vision call returned for the 11 photos of record 001 (runouts partly misread).
    private final ExtractedFacts withRunouts = new ExtractedFacts(
            List.of(f("081227966409", "0 81227 96640 9")),
            List.of(f("8122796640", "8122796640"), f("R1 535225", "R1 535225")),
            List.of(f("Atlantic", "ATLANTIC")),
            List.of(f("Germany", "Made in Germany"), f("Germany", "MANUFACTURED IN GERMANY.")),
            List.of(f("180g vinyl", "THE CLASSIC ALBUM ON 180g VINYL"), f("Stereo", "STEREO")),
            List.of(f("00812227", "00812227"), f("6016-01", "6016-01")));

    private final ExtractedFacts withoutRunouts = new ExtractedFacts(
            withRunouts.barcodes(), withRunouts.catalogNumbers(), withRunouts.labels(), withRunouts.countries(),
            withRunouts.formats(), List.of());

    // Worldwide 2020 reissue: same barcode and both catalog numbers, BK matrix.
    private final ReleaseInfo correct = new ReleaseInfo("A", "Reissue 2020", "Worldwide", 2020,
            List.of("8122796640", "R1 535225"), List.of("0 81227 96640 9", "081227966409"),
            List.of("BK06016-01 A1 0081227966409 A2 JD", "BK06016-01 B1 0081227966409 B2"),
            List.of("Atlantic"), List.of("LP, Album, Reissue, Remastered, Stereo"));

    // Deluxe edition: same BK matrix, but a different barcode and catalog number.
    private final ReleaseInfo deluxe = new ReleaseInfo("B", "Deluxe 2014", "Germany", 2014,
            List.of("8122796438"), List.of("0 81227 96438 2", "081227964382"),
            List.of("BK06016-01 A1 0081227966409 A2 JD", "BK06016-01 B1 0081227966409 B2"),
            List.of("Atlantic"), List.of("LP, Album, Reissue, Remastered, Stereo"));

    // US 2014 reissue: same barcode and the R1 catalog number, but an R1 matrix.
    private final ReleaseInfo us2014 = new ReleaseInfo("C", "US 2014", "US", 2014,
            List.of("R1-535225"), List.of("0 81227 96640 9", "081227966409"),
            List.of("R1 535225", "R1-535225 A1 JD -27806- P.USA"),
            List.of("Atlantic"), List.of("LP, Album, Reissue, Remastered, Stereo"));

    private final EvidenceBuilder evidence = new EvidenceBuilder();
    private final CandidateScorer scorer = new CandidateScorer(ScoringWeights.defaults());
    private final Decider decider = new Decider(Thresholds.defaults());

    private PricedCandidate price(ExtractedFacts facts, ReleaseInfo r, double price) {
        return new PricedCandidate(scorer.score(r.releaseId(), evidence.build(facts, r)), price);
    }

    @Test
    void withRunoutPhotosTheCorrectReleaseIsConfident() {
        List<PricedCandidate> all = List.of(
                price(withRunouts, correct, 30.0), price(withRunouts, deluxe, 45.0), price(withRunouts, us2014, 25.0));
        DecisionResult r = decider.decide(all);
        assertEquals(Decision.CONFIDENT, r.decision());
        assertEquals("A", r.topReleaseId());
    }

    @Test
    void withoutRunoutPhotosItAsksForThem() {
        List<PricedCandidate> all = List.of(
                price(withoutRunouts, correct, 30.0), price(withoutRunouts, us2014, 25.0));
        DecisionResult r = decider.decide(all);
        assertEquals(Decision.NEEDS_RUNOUT_PHOTOS, r.decision());
    }

    @Test
    void theDeluxeAndTheUsReissueScoreClearlyBelowTheCorrectOne() {
        double a = price(withRunouts, correct, 0).candidate().score();
        double b = price(withRunouts, deluxe, 0).candidate().score();
        double c = price(withRunouts, us2014, 0).candidate().score();
        assertTrue(a > 0.85, "correct release scored " + a);
        assertTrue(b < 0.4 && c < 0.4, "rivals scored " + b + " and " + c);
    }
}
