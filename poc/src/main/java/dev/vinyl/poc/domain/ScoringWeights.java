package dev.vinyl.poc.domain;

import java.util.EnumMap;
import java.util.Map;

/** How much each identifier counts toward a candidate's score. Editable settings, not model output. */
public record ScoringWeights(Map<Identifier, Double> weights) {

    public ScoringWeights {
        weights = Map.copyOf(weights);
    }

    public static ScoringWeights defaults() {
        Map<Identifier, Double> w = new EnumMap<>(Identifier.class);
        w.put(Identifier.BARCODE, 3.0);
        w.put(Identifier.MATRIX, 3.0);
        w.put(Identifier.CATALOG_NUMBER, 2.0);
        w.put(Identifier.COUNTRY, 1.0);
        w.put(Identifier.LABEL, 1.0);
        w.put(Identifier.FORMAT, 0.5);
        return new ScoringWeights(w);
    }

    public double of(Identifier identifier) {
        return weights.getOrDefault(identifier, 0.0);
    }

    public double total() {
        return weights.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
