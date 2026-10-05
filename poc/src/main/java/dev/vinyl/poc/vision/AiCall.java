package dev.vinyl.poc.vision;

import java.math.BigDecimal;

/** One logged model call with its cost. Thinking tokens are included in outputTokens. */
public record AiCall(String model, int photos, long inputTokens, long outputTokens, BigDecimal costUsd,
                     long elapsedMillis) {
}
