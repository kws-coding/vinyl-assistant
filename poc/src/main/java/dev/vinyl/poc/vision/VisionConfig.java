package dev.vinyl.poc.vision;

import java.math.BigDecimal;

/**
 * Settings for the vision call. The model name is configuration, not code. Defaults are starting points
 * to be tuned from measurement: effort and prices in particular are unverified.
 */
public record VisionConfig(String model, int maxTokens, String effort, int maxImageEdge, ModelPrice price) {

    public static VisionConfig fromEnvironment() {
        return new VisionConfig(
                env("VISION_MODEL", "claude-sonnet-5-5"),
                Integer.parseInt(env("VISION_MAX_TOKENS", "8000")),
                env("VISION_EFFORT", "medium"),
                Integer.parseInt(env("VISION_MAX_EDGE", "1568")),
                new ModelPrice(new BigDecimal(env("VISION_PRICE_IN", "2.00")),
                        new BigDecimal(env("VISION_PRICE_OUT", "10.00"))));
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
