package dev.vinyl.poc.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * fee    = rate * (item + shipping_charged + tax) + per_order
 * net    = item + shipping_charged - fee - postage - supplies   (what a sale leaves before the cost of the record)
 * profit = net - cost_basis
 * Intermediate values are exact; only the reported amounts are rounded to cents.
 */
public class ProfitCalculator {

    private final ProfitSettings settings;

    public ProfitCalculator(ProfitSettings settings) {
        this.settings = settings;
    }

    public ProfitEstimate estimate(BigDecimal item, BigDecimal costBasis) {
        BigDecimal tax = tax(item);
        return new ProfitEstimate(cents(item), cents(tax), cents(fee(item)), cents(net(item).subtract(costBasis)));
    }

    /** What a sale leaves before the cost of the record. Needs no purchase price. */
    public BigDecimal netBeforeCost(BigDecimal item) {
        return cents(net(item));
    }

    private BigDecimal tax(BigDecimal item) {
        return settings.taxRate().multiply(item.add(settings.shippingCharged()));
    }

    private BigDecimal fee(BigDecimal item) {
        BigDecimal base = item.add(settings.shippingCharged()).add(tax(item));
        return settings.feeRate().multiply(base).add(settings.perOrderFee());
    }

    private BigDecimal net(BigDecimal item) {
        return item.add(settings.shippingCharged()).subtract(fee(item)).subtract(settings.postage())
                .subtract(settings.supplies());
    }

    private static BigDecimal cents(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
