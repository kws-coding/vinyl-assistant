package dev.vinyl.poc.domain;

import java.math.BigDecimal;

/**
 * Editable profit inputs. The eBay fee model is an unverified placeholder.
 *
 * @param feeRate         fraction of (item + shipping charged + tax), for example 0.136
 * @param perOrderFee     flat fee per order
 * @param shippingCharged what the buyer pays for shipping
 * @param postage         what the label really costs us
 * @param supplies        mailer and other supplies per item
 * @param taxRate         sales tax as a fraction of (item + shipping charged); collected from the buyer
 */
public record ProfitSettings(BigDecimal feeRate, BigDecimal perOrderFee, BigDecimal shippingCharged,
                             BigDecimal postage, BigDecimal supplies, BigDecimal taxRate) {

    /**
     * Placeholders from the project brief. Postage has no default because the real label cost is not known
     * here, so the caller must supply it. Tax defaults to zero.
     */
    public static ProfitSettings defaults(BigDecimal postage) {
        return new ProfitSettings(new BigDecimal("0.136"), new BigDecimal("0.40"), new BigDecimal("5.50"),
                postage, new BigDecimal("1.00"), BigDecimal.ZERO);
    }

    /**
     * Discogs: the 9% selling fee from the project brief, no flat fee. Unverified: whether the fee applies to
     * shipping as well as the item, and whether payment processing fees come on top. Shipping charged,
     * supplies and tax are the same placeholders as above.
     */
    public static ProfitSettings discogs(BigDecimal postage) {
        return new ProfitSettings(new BigDecimal("0.09"), BigDecimal.ZERO, new BigDecimal("5.50"),
                postage, new BigDecimal("1.00"), BigDecimal.ZERO);
    }
}
