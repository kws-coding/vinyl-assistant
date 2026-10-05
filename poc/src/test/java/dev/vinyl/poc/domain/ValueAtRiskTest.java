package dev.vinyl.poc.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValueAtRiskTest {

    private PricedCandidate pc(String id, double score, Double price) {
        return new PricedCandidate(new ScoredCandidate(id, score, List.of()), price);
    }

    @Test
    void priceGapIsWeightedByRivalScore() {
        ValueAtRisk v = ValueAtRisk.compute(List.of(pc("top", 0.9, 20.0), pc("rival", 0.5, 10.0)), 0.4);
        assertEquals(5.0, v.amount(), 1e-9);
        assertEquals("rival", v.rivalReleaseId());
    }

    @Test
    void largestWeightedGapWins() {
        ValueAtRisk v = ValueAtRisk.compute(
                List.of(pc("top", 0.9, 20.0), pc("a", 0.5, 18.0), pc("b", 0.6, 10.0)), 0.4);
        assertEquals(6.0, v.amount(), 1e-9);
        assertEquals("b", v.rivalReleaseId());
    }

    @Test
    void implausibleRivalsAreIgnored() {
        ValueAtRisk v = ValueAtRisk.compute(List.of(pc("top", 0.9, 20.0), pc("far", 0.2, 100.0)), 0.4);
        assertEquals(0.0, v.amount(), 1e-9);
        assertNull(v.rivalReleaseId());
    }

    @Test
    void unpricedPlausibleRivalsAreCountedNotGuessed() {
        ValueAtRisk v = ValueAtRisk.compute(List.of(pc("top", 0.9, 20.0), pc("x", 0.6, null)), 0.4);
        assertEquals(0.0, v.amount(), 1e-9);
        assertEquals(1, v.unpricedRivals());
    }

    @Test
    void unpricedTopCountsEveryPlausibleRival() {
        ValueAtRisk v = ValueAtRisk.compute(List.of(pc("top", 0.9, null), pc("x", 0.6, 10.0)), 0.4);
        assertEquals(1, v.unpricedRivals());
    }

    @Test
    void emptyListHasNoRisk() {
        assertEquals(0.0, ValueAtRisk.compute(List.of(), 0.4).amount(), 1e-9);
    }
}
