package dev.vinyl.poc.domain;

import static dev.vinyl.poc.domain.Identifier.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceBuilderTest {

    private final EvidenceBuilder builder = new EvidenceBuilder();

    private static Fact f(String value, String raw) {
        return new Fact(value, raw);
    }

    private static ExtractedFacts facts(List<Fact> barcodes, List<Fact> catnos, List<Fact> labels,
                                        List<Fact> countries, List<Fact> formats, List<Fact> matrices) {
        return new ExtractedFacts(barcodes, catnos, labels, countries, formats, matrices);
    }

    private static ExtractedFacts none() {
        return facts(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static ReleaseInfo release(String country, List<String> catnos, List<String> barcodes,
                                       List<String> matrices, List<String> labels, List<String> formats) {
        return new ReleaseInfo("1", "T", country, 2020, catnos, barcodes, matrices, labels, formats);
    }

    private IdentifierEvidence one(Identifier id, ExtractedFacts facts, ReleaseInfo r) {
        return builder.build(facts, r).stream().filter(e -> e.identifier() == id).findFirst().orElseThrow();
    }

    private ReleaseInfo withBarcodes(String... b) {
        return release("US", List.of(), List.of(b), List.of(), List.of(), List.of());
    }

    @Test
    void alwaysReturnsOneEvidencePerIdentifier() {
        assertEquals(6, builder.build(none(), release("US", List.of(), List.of(), List.of(), List.of(), List.of())).size());
    }

    // barcode

    @Test
    void barcodeMatchesAcrossSpacingAndLeadingZeros() {
        ExtractedFacts photo = facts(List.of(f("081227966409", "0 81227 96640 9")), List.of(), List.of(), List.of(), List.of(), List.of());
        IdentifierEvidence e = one(BARCODE, photo, withBarcodes("0 81227 96640 9"));
        assertEquals(Outcome.MATCH, e.outcome());
        assertEquals("0 81227 96640 9", e.rawText());
        assertEquals(Outcome.MATCH, one(BARCODE, photo, withBarcodes("0081227966409")).outcome());
    }

    @Test
    void barcodeMismatchWhenDigitsDiffer() {
        ExtractedFacts photo = facts(List.of(f("081227966409", "0 81227 96640 9")), List.of(), List.of(), List.of(), List.of(), List.of());
        assertEquals(Outcome.MISMATCH, one(BARCODE, photo, withBarcodes("081227964382")).outcome());
    }

    @Test
    void nonBarcodeJunkInTheCandidateBarcodeListIsIgnored() {
        ExtractedFacts photo = facts(List.of(f("081227966409", "x")), List.of(), List.of(), List.of(), List.of(), List.of());
        // only junk listed: nothing to compare, so missing, not mismatch
        assertEquals(Outcome.MISSING,
                one(BARCODE, photo, withBarcodes("ASCAP", "BK06016-01 A1 0081227966409 A2 JD X+S")).outcome());
        assertEquals(Outcome.MATCH, one(BARCODE, photo, withBarcodes("ASCAP", "081227966409")).outcome());
    }

    @Test
    void barcodeMissingWhenNothingWasRead() {
        assertEquals(Outcome.MISSING, one(BARCODE, none(), withBarcodes("081227966409")).outcome());
    }

    @Test
    void aTooShortBarcodeReadIsIgnoredNotACompare() {
        ExtractedFacts photo = facts(List.of(f("0812", "0812")), List.of(), List.of(), List.of(), List.of(), List.of());
        assertEquals(Outcome.MISSING, one(BARCODE, photo, withBarcodes("081227966409")).outcome());
    }

    // catalog number

    @Test
    void catalogNumberIgnoresSpacingAndPunctuation() {
        ExtractedFacts photo = facts(List.of(), List.of(f("R1 535225", "R1 535225")), List.of(), List.of(), List.of(), List.of());
        ReleaseInfo r = release("US", List.of("R1-535225"), List.of(), List.of(), List.of(), List.of());
        assertEquals(Outcome.MATCH, one(CATALOG_NUMBER, photo, r).outcome());
    }

    @Test
    void anyObservedCatalogNumberMayMatchAnyListedOne() {
        ExtractedFacts photo = facts(List.of(), List.of(f("8122796640", "a"), f("R1 535225", "b")),
                List.of(), List.of(), List.of(), List.of());
        ReleaseInfo r = release("US", List.of("R1 535225"), List.of(), List.of(), List.of(), List.of());
        IdentifierEvidence e = one(CATALOG_NUMBER, photo, r);
        assertEquals(Outcome.MATCH, e.outcome());
        assertEquals("b", e.rawText());
    }

    @Test
    void catalogNumberMismatch() {
        ExtractedFacts photo = facts(List.of(), List.of(f("8122796640", "8122796640")), List.of(), List.of(), List.of(), List.of());
        ReleaseInfo r = release("US", List.of("8122796438"), List.of(), List.of(), List.of(), List.of());
        assertEquals(Outcome.MISMATCH, one(CATALOG_NUMBER, photo, r).outcome());
    }

    // label

    @Test
    void labelMatchesWhenOneNameContainsTheOther() {
        ExtractedFacts photo = facts(List.of(), List.of(), List.of(f("Atlantic", "ATLANTIC")), List.of(), List.of(), List.of());
        ReleaseInfo r = release("US", List.of(), List.of(), List.of(), List.of("Atlantic Recording Corporation"), List.of());
        assertEquals(Outcome.MATCH, one(LABEL, photo, r).outcome());
        ReleaseInfo other = release("US", List.of(), List.of(), List.of(), List.of("Island"), List.of());
        assertEquals(Outcome.MISMATCH, one(LABEL, photo, other).outcome());
    }

    // country

    private ExtractedFacts country(String v) {
        return facts(List.of(), List.of(), List.of(), List.of(f(v, "Made in " + v)), List.of(), List.of());
    }

    private ReleaseInfo inCountry(String c) {
        return release(c, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void worldwideAndEuropeSayNothingAboutManufacture() {
        assertEquals(Outcome.MISSING, one(COUNTRY, country("Germany"), inCountry("Worldwide")).outcome());
        assertEquals(Outcome.MISSING, one(COUNTRY, country("Germany"), inCountry("Europe")).outcome());
    }

    @Test
    void specificCountryMatchesOrMismatches() {
        assertEquals(Outcome.MATCH, one(COUNTRY, country("Germany"), inCountry("Germany")).outcome());
        assertEquals(Outcome.MISMATCH, one(COUNTRY, country("Germany"), inCountry("US")).outcome());
    }

    @Test
    void countryAliasesAndCombinedNamesMatch() {
        assertEquals(Outcome.MATCH, one(COUNTRY, country("USA"), inCountry("US")).outcome());
        assertEquals(Outcome.MATCH, one(COUNTRY, country("USA"), inCountry("USA & Canada")).outcome());
        assertEquals(Outcome.MATCH, one(COUNTRY, country("UK"), inCountry("United Kingdom")).outcome());
    }

    // format

    private ReleaseInfo format(String... d) {
        return release("US", List.of(), List.of(), List.of(), List.of(), List.of(d));
    }

    private ExtractedFacts formatFact(String v) {
        return facts(List.of(), List.of(), List.of(), List.of(), List.of(f(v, v)), List.of());
    }

    @Test
    void stereoAndMonoAreComparedOthersAreIgnored() {
        assertEquals(Outcome.MATCH, one(FORMAT, formatFact("Stereo"), format("LP, Album, Stereo")).outcome());
        assertEquals(Outcome.MISMATCH, one(FORMAT, formatFact("Stereo"), format("LP, Album, Mono")).outcome());
        assertEquals(Outcome.MISSING, one(FORMAT, formatFact("180g vinyl"), format("LP, Album, Stereo")).outcome());
        assertEquals(Outcome.MISSING, one(FORMAT, formatFact("Stereo"), format("LP, Album")).outcome());
    }

    // matrix

    private ExtractedFacts matrixFact(String... values) {
        List<Fact> m = java.util.Arrays.stream(values).map(v -> f(v, v)).toList();
        return facts(List.of(), List.of(), List.of(), List.of(), List.of(), m);
    }

    private ReleaseInfo matrices(String... m) {
        return release("US", List.of(), List.of(), List.of(m), List.of(), List.of());
    }

    @Test
    void partialRunoutTextMatchesInsideAListedRunout() {
        assertEquals(Outcome.MATCH,
                one(MATRIX, matrixFact("6016-01"), matrices("BK06016-01 B1 0081227966409 B2")).outcome());
    }

    @Test
    void oneMisreadCharacterStillMatches() {
        // actual etching is 0081227, the model read 00812227
        assertEquals(Outcome.MATCH,
                one(MATRIX, matrixFact("00812227"), matrices("BK06016-01 A1 0081227966409 A2 JD")).outcome());
    }

    @Test
    void runoutThatFitsNoListedRunoutIsAMismatch() {
        assertEquals(Outcome.MISMATCH,
                one(MATRIX, matrixFact("6016-01"), matrices("BD 16357-01 A1 A1 JD", "BD 16357-01 B1")).outcome());
    }

    @Test
    void tooShortARunoutReadIsIgnored() {
        assertEquals(Outcome.MISSING, one(MATRIX, matrixFact("A1"), matrices("BK06016-01 A1")).outcome());
    }

    @Test
    void candidateWithNoListedRunoutsCannotBeCompared() {
        assertEquals(Outcome.MISSING, one(MATRIX, matrixFact("6016-01"), matrices()).outcome());
    }

    // barcode, more cases

    @Test
    void anyObservedBarcodeMayMatchAndKeepsItsOwnRawText() {
        ExtractedFacts photo = facts(List.of(f("999999999999", "wrong"), f("081227966409", "right")),
                List.of(), List.of(), List.of(), List.of(), List.of());
        IdentifierEvidence e = one(BARCODE, photo, withBarcodes("081227966409"));
        assertEquals(Outcome.MATCH, e.outcome());
        assertEquals("right", e.rawText());
    }

    @Test
    void aSixDigitBarcodeReadIsIgnored() {
        ExtractedFacts photo = facts(List.of(f("081227", "081227")), List.of(), List.of(), List.of(), List.of(), List.of());
        assertEquals(Outcome.MISSING, one(BARCODE, photo, withBarcodes("081227966409")).outcome());
    }

    // matrix, boundaries

    @Test
    void aFiveCharacterRunoutMustMatchExactly() {
        assertEquals(Outcome.MATCH, one(MATRIX, matrixFact("60160"), matrices("BK06016-01 B1")).outcome());
        assertEquals(Outcome.MISMATCH, one(MATRIX, matrixFact("60161"), matrices("BK06016-01 B1")).outcome());
    }

    @Test
    void sixCharactersAllowOneErrorButNotTwo() {
        assertEquals(Outcome.MATCH, one(MATRIX, matrixFact("6X1601"), matrices("BK06016-01 B1")).outcome());
        assertEquals(Outcome.MISMATCH, one(MATRIX, matrixFact("6XX601"), matrices("BK06016-01 B1")).outcome());
    }

    @Test
    void aLaterObservedRunoutCanMatchAndAMismatchKeepsTheFirstRawText() {
        IdentifierEvidence match = one(MATRIX, matrixFact("ZZZZZZ", "6016-01"), matrices("BK06016-01 B1"));
        assertEquals(Outcome.MATCH, match.outcome());
        assertEquals("6016-01", match.rawText());
        IdentifierEvidence miss = one(MATRIX, matrixFact("QQQQQQ", "WWWWWW"), matrices("BK06016-01 B1"));
        assertEquals(Outcome.MISMATCH, miss.outcome());
        assertEquals("QQQQQQ", miss.rawText());
    }

    // country, more cases

    @Test
    void aMissingCountryOrNoCountryReadIsMissing() {
        assertEquals(Outcome.MISSING, one(COUNTRY, country("Germany"), inCountry(null)).outcome());
        assertEquals(Outcome.MISSING, one(COUNTRY, none(), inCountry("US")).outcome());
    }

    @Test
    void anyCountryInACombinedNameMatches() {
        assertEquals(Outcome.MATCH, one(COUNTRY, country("Austria"), inCountry("Germany, Austria & Switzerland")).outcome());
    }

    @Test
    void westGermanyCountsAsGermany() {
        assertEquals(Outcome.MATCH, one(COUNTRY, country("West Germany"), inCountry("Germany")).outcome());
    }

    // format, more cases

    @Test
    void monoMatchesMonoAndMismatchesStereo() {
        assertEquals(Outcome.MATCH, one(FORMAT, formatFact("Mono"), format("LP, Album, Mono")).outcome());
        assertEquals(Outcome.MISMATCH, one(FORMAT, formatFact("Mono"), format("LP, Album, Stereo")).outcome());
    }

    @Test
    void aReleaseListingBothMatchesEitherReadAndNoReadIsMissing() {
        assertEquals(Outcome.MATCH, one(FORMAT, formatFact("Stereo"), format("LP, Stereo, Mono")).outcome());
        assertEquals(Outcome.MATCH, one(FORMAT, formatFact("Mono"), format("LP, Stereo, Mono")).outcome());
        assertEquals(Outcome.MISSING, one(FORMAT, none(), format("LP, Album, Stereo")).outcome());
    }

    // probes: a read with no letters or digits says nothing, so it should not count against a release

    @Test
    void aPunctuationOnlyLabelReadIsNotAMismatch() {
        ExtractedFacts photo = facts(List.of(), List.of(), List.of(f("-", "-")), List.of(), List.of(), List.of());
        ReleaseInfo r = release("US", List.of(), List.of(), List.of(), List.of("Atlantic"), List.of());
        assertEquals(Outcome.MISSING, one(LABEL, photo, r).outcome());
    }

    @Test
    void aPunctuationOnlyCatalogNumberReadIsNotAMismatch() {
        ExtractedFacts photo = facts(List.of(), List.of(f("-", "-")), List.of(), List.of(), List.of(), List.of());
        ReleaseInfo r = release("US", List.of("8122796640"), List.of(), List.of(), List.of(), List.of());
        assertEquals(Outcome.MISSING, one(CATALOG_NUMBER, photo, r).outcome());
    }

    @Test
    void aPunctuationOnlyCountryReadIsNotAMismatch() {
        assertEquals(Outcome.MISSING, one(COUNTRY, country("."), inCountry("Germany")).outcome());
    }
}
