package dev.vinyl.poc.domain;

import java.math.BigDecimal;

/** An estimate only. The actual label cost is recorded after the sale. All amounts are rounded to cents. */
public record ProfitEstimate(BigDecimal item, BigDecimal tax, BigDecimal fee, BigDecimal profit) {
}
