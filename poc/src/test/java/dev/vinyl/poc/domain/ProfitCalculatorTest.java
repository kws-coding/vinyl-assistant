package dev.vinyl.poc.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProfitCalculatorTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    // Test postage of 4.50 is made up; it is not a real label cost.
    private final ProfitSettings settings = ProfitSettings.defaults(d("4.50"));

    @Test
    void usesTheBriefFormulaWithNoTax() {
        // fee = 0.136 * (30 + 5.50) + 0.40 = 5.228
        // profit = 35.50 - 5.228 - 4.50 - 1.00 - 10 = 14.772
        ProfitEstimate e = new ProfitCalculator(settings).estimate(d("30.00"), d("10.00"));
        assertEquals(d("5.23"), e.fee());
        assertEquals(d("14.77"), e.profit());
        assertEquals(d("0.00"), e.tax());
    }

    @Test
    void feeIsChargedOnTaxToo() {
        ProfitSettings taxed = new ProfitSettings(d("0.136"), d("0.40"), d("5.50"), d("4.50"), d("1.00"), d("0.08"));
        // tax = 0.08 * 35.50 = 2.84; fee = 0.136 * 38.34 + 0.40 = 5.61424
        // profit = 35.50 - 5.61424 - 4.50 - 1.00 - 10 = 14.38576
        ProfitEstimate e = new ProfitCalculator(taxed).estimate(d("30.00"), d("10.00"));
        assertEquals(d("2.84"), e.tax());
        assertEquals(d("5.61"), e.fee());
        assertEquals(d("14.39"), e.profit());
    }

    @Test
    void profitCanBeNegative() {
        ProfitEstimate e = new ProfitCalculator(settings).estimate(d("8.00"), d("12.00"));
        assertTrue(e.profit().signum() < 0);
    }

    @Test
    void roundsOnlyTheReportedAmounts() {
        // fee 0.136 * 5.50 + 0.40 = 1.148; profit = 5.50 - 1.148 - 4.50 - 1.00 - 0 = -1.148
        ProfitEstimate e = new ProfitCalculator(settings).estimate(d("0.00"), d("0.00"));
        assertEquals(d("1.15"), e.fee());
        assertEquals(d("-1.15"), e.profit());
    }

    @Test
    void roundsHalfUpAtTheHalfCent() {
        // fee = 0.10 * 0.05 = 0.005 -> 0.01; profit = 0.05 - 0.005 = 0.045 -> 0.05 (half-even would give 0.00 and 0.04)
        ProfitSettings s = new ProfitSettings(d("0.10"), d("0"), d("0"), d("0"), d("0"), d("0"));
        ProfitEstimate e = new ProfitCalculator(s).estimate(d("0.05"), d("0"));
        assertEquals(d("0.01"), e.fee());
        assertEquals(d("0.05"), e.profit());
    }

    @Test
    void withNoFeesProfitIsWhatIsChargedMinusCosts() {
        ProfitSettings s = new ProfitSettings(d("0"), d("0"), d("5.50"), d("4.50"), d("1.00"), d("0"));
        ProfitEstimate e = new ProfitCalculator(s).estimate(d("20.00"), d("3.00"));
        assertEquals(d("0.00"), e.fee());
        assertEquals(d("17.00"), e.profit());
    }
}
