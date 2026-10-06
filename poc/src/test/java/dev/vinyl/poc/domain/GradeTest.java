package dev.vinyl.poc.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GradeTest {

    @Test
    void parsesTheDiscogsScaleAndTreatsNmAndMMinusAsTheSameStep() {
        assertEquals(Grade.MINT, Grade.parse("M").orElseThrow());
        assertEquals(Grade.NEAR_MINT, Grade.parse("NM").orElseThrow());
        assertEquals(Grade.NEAR_MINT, Grade.parse(" m- ").orElseThrow());
        assertEquals(Grade.NEAR_MINT, Grade.parse("Near Mint (NM or M-)".replace("Near Mint (", "").replace(")", "")).orElseThrow());
        assertEquals(Grade.VERY_GOOD_PLUS, Grade.parse("vg +").orElseThrow());
        assertEquals(Grade.VERY_GOOD, Grade.parse("VG").orElseThrow());
        assertEquals(Grade.GOOD_PLUS, Grade.parse("G+").orElseThrow());
        assertEquals(Grade.POOR, Grade.parse("poor").orElseThrow());
    }

    @Test
    void blankUnknownAndCannotTellAreEmptyNotGuessed() {
        assertTrue(Grade.parse(null).isEmpty());
        assertTrue(Grade.parse("").isEmpty());
        assertTrue(Grade.parse("cannot_tell").isEmpty());
        assertTrue(Grade.parse("EX").isEmpty());
    }

    @Test
    void theScaleRunsFromPoorToMint() {
        assertTrue(Grade.POOR.ordinal() < Grade.FAIR.ordinal());
        assertTrue(Grade.VERY_GOOD.ordinal() < Grade.VERY_GOOD_PLUS.ordinal());
        assertTrue(Grade.NEAR_MINT.ordinal() < Grade.MINT.ordinal());
    }

    @Test
    void comparisonSaysWhichWayAndHowFar() {
        GradeComparison lower = GradeComparison.compare(Grade.NEAR_MINT, Grade.VERY_GOOD);
        assertEquals(GradeComparison.Kind.SUGGESTED_LOWER, lower.kind());
        assertEquals(2, lower.steps());
        assertTrue(lower.differs());
        assertFalse(lower.withinOneStep());
        assertTrue(lower.describe().contains("2 steps below yours (yours may be generous)"));

        GradeComparison higher = GradeComparison.compare(Grade.VERY_GOOD, Grade.VERY_GOOD_PLUS);
        assertEquals(GradeComparison.Kind.SUGGESTED_HIGHER, higher.kind());
        assertTrue(higher.withinOneStep());
        assertTrue(higher.describe().contains("1 step above yours (yours may be harsh)"));
    }

    @Test
    void sameAndCannotCompare() {
        GradeComparison same = GradeComparison.compare(Grade.VERY_GOOD_PLUS, Grade.VERY_GOOD_PLUS);
        assertFalse(same.differs());
        assertTrue(same.withinOneStep());
        GradeComparison none = GradeComparison.compare(null, Grade.VERY_GOOD);
        assertEquals(GradeComparison.Kind.CANNOT_COMPARE, none.kind());
        assertFalse(none.differs());
        assertFalse(none.withinOneStep());
        assertEquals(GradeComparison.Kind.CANNOT_COMPARE, GradeComparison.compare(Grade.MINT, null).kind());
    }
}
