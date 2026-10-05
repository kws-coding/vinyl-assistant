package dev.vinyl.poc.vision;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Price per million tokens. Editable setting; the defaults are unverified list prices. */
public record ModelPrice(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {

    public BigDecimal cost(long inputTokens, long outputTokens) {
        BigDecimal in = inputPerMillion.multiply(BigDecimal.valueOf(inputTokens));
        BigDecimal out = outputPerMillion.multiply(BigDecimal.valueOf(outputTokens));
        return in.add(out).divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
    }
}
