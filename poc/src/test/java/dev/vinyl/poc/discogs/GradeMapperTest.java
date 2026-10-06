package dev.vinyl.poc.discogs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GradeMapperTest {

    // The VG+ and NM keys come from a real price-suggestions response. The others follow Discogs' grade
    // names and have not been seen live.
    private static final Set<String> KEYS = Set.of("Mint (M)", "Near Mint (NM or M-)", "Very Good Plus (VG+)",
            "Very Good (VG)", "Good Plus (G+)", "Good (G)", "Fair (F)", "Poor (P)");

    private static Optional<String> key(String grade) {
        return GradeMapper.suggestionKey(grade, KEYS);
    }

    @Test
    void everyGradeFindsItsOwnKey() {
        assertEquals("Mint (M)", key("M").orElseThrow());
        assertEquals("Near Mint (NM or M-)", key("NM").orElseThrow());
        assertEquals("Very Good Plus (VG+)", key("VG+").orElseThrow());
        assertEquals("Very Good (VG)", key("VG").orElseThrow());
        assertEquals("Good Plus (G+)", key("G+").orElseThrow());
        assertEquals("Good (G)", key("G").orElseThrow());
        assertEquals("Fair (F)", key("F").orElseThrow());
        assertEquals("Poor (P)", key("P").orElseThrow());
    }

    @Test
    void aPlainGradeNeverPicksItsPlusNeighbour() {
        assertEquals("Very Good (VG)", key("VG").orElseThrow());
        assertEquals("Good (G)", key("G").orElseThrow());
    }

    @Test
    void caseAndSpacingAreForgiven() {
        assertEquals("Very Good Plus (VG+)", key(" vg+ ").orElseThrow());
        assertEquals("Very Good Plus (VG+)", key("VG +").orElseThrow());
        assertEquals("Near Mint (NM or M-)", key("nm-").orElseThrow());
        assertEquals("Near Mint (NM or M-)", key("M-").orElseThrow());
    }

    @Test
    void unknownBlankOrNullGradesFindNothing() {
        assertTrue(key("EX").isEmpty());
        assertTrue(key("").isEmpty());
        assertTrue(key(null).isEmpty());
    }

    @Test
    void aGradeDiscogsDidNotOfferFindsNothingInsteadOfSubstituting() {
        assertTrue(GradeMapper.suggestionKey("VG+", Set.of("Very Good (VG)", "Near Mint (NM or M-)")).isEmpty());
    }
}
