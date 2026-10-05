package dev.vinyl.poc.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * fee    = rate * (item + shipping_charged + tax) + per_order
 * profit = item + shipping_charged - fee - postage - supplies - cost_basis
 * Intermediate values are exact; only the reported amounts are rounded to cents.
 */
public class ProfitCalculator {

    private final ProfitSettings settings;

    public ProfitCalculator(ProfitSettings settings) {
        this.settings = settings;
    }

    public ProfitEstimate estimate(BigDecimal item, BigDecimal costBasis) {
        BigDecimal shipping = settings.shippingCharged();
        BigDecimal tax = settings.taxRate().multiply(item.add(shipping));
        BigDecimal fee = settings.feeRate().multiply(item.add(shipping).add(tax)).add(settings.perOrderFee());
        BigDecimal profit = item.add(shipping).subtract(fee).subtract(settings.postage())
                .subtract(settings.supplies()).subtract(costBasis);
        return new ProfitEstimate(cents(item), cents(tax), cents(fee), cents(profit));
    }

    private static BigDecimal cents(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
