package dev.vinyl.poc.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FuzzyTextTest {

    @Test
    void alnumIgnoresCaseSpacingAndPunctuation() {
        assertEquals("R1535225", FuzzyText.alnum("r1-535 225"));
        assertEquals("", FuzzyText.alnum(null));
    }

    @Test
    void exactSubstringMatchesWithZeroErrors() {
        assertTrue(FuzzyText.approxContains("BK06016B10081227966409B2", "6016B1", 0));
        assertFalse(FuzzyText.approxContains("BK06016B10081227966409B2", "6016B2", 0));
    }

    @Test
    void oneExtraOrWrongCharacterIsToleratedWhenAllowed() {
        // 00812227 has one extra 2 compared with 0081227
        assertTrue(FuzzyText.approxContains("BK060160081227966409", "00812227", 1));
        assertFalse(FuzzyText.approxContains("BK060160081227966409", "00812227", 0));
        // one substituted character
        assertTrue(FuzzyText.approxContains("BK060160081227966409", "0081X27", 1));
    }

    @Test
    void twoErrorsAreNotToleratedWhenOneIsAllowed() {
        assertFalse(FuzzyText.approxContains("BK060160081227966409", "00X12X7", 1));
    }

    @Test
    void emptyPatternMatches() {
        assertTrue(FuzzyText.approxContains("ABC", "", 0));
    }
}
