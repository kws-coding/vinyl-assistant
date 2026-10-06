package dev.vinyl.poc.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** All prices here are made-up test values, not real sales. */
class EbayAlertTest {

    private final EbayAlert alert = EbayAlert.defaults();

    @Test
    void aHighSaleFarAboveTheDiscogsPriceRaisesAnAlertThatSaysWhetherThePressingIsConfirmed() {
        String same = alert.check(30.0, 90.0, EbayMatch.SAME).orElseThrow();
        assertTrue(same.contains("$90.00"));
        assertTrue(same.contains("3.0x"));
        assertTrue(same.contains("same pressing confirmed"));
        assertTrue(alert.check(30.0, 90.0, EbayMatch.UNSURE).orElseThrow().contains("pressing not confirmed"));
    }

    @Test
    void aSaleMarkedAsAnotherPressingRaisesNothing() {
        assertEquals(Optional.empty(), alert.check(30.0, 90.0, EbayMatch.NO));
    }

    @Test
    void bothTheRatioAndTheDollarGapMustBeMet() {
        // exactly 2x and exactly $20 above: raised
        assertTrue(alert.check(20.0, 40.0, EbayMatch.SAME).isPresent());
        // 3x but only $10 above: too small to matter
        assertTrue(alert.check(5.0, 15.0, EbayMatch.SAME).isEmpty());
        // $30 above but under 2x
        assertTrue(alert.check(60.0, 90.0, EbayMatch.SAME).isEmpty());
    }

    @Test
    void anEbaySaleBelowTheDiscogsPriceRaisesNothing() {
        assertTrue(alert.check(30.0, 10.0, EbayMatch.SAME).isEmpty());
    }

    @Test
    void missingOrZeroPricesRaiseNothing() {
        assertTrue(alert.check(null, 90.0, EbayMatch.SAME).isEmpty());
        assertTrue(alert.check(30.0, null, EbayMatch.SAME).isEmpty());
        assertTrue(alert.check(0.0, 90.0, EbayMatch.SAME).isEmpty());
    }

    @Test
    void matchParsingTreatsBlankAsUnsureAndRejectsAnythingElse() {
        assertEquals(EbayMatch.UNSURE, EbayMatch.parse(""));
        assertEquals(EbayMatch.UNSURE, EbayMatch.parse(null));
        assertEquals(EbayMatch.SAME, EbayMatch.parse(" Same "));
        assertEquals(EbayMatch.NO, EbayMatch.parse("no"));
        assertThrows(IllegalArgumentException.class, () -> EbayMatch.parse("maybe"));
    }
}
