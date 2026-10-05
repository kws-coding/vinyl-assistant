package dev.vinyl.poc.vision;

import dev.vinyl.poc.domain.ExtractedFacts;
import dev.vinyl.poc.domain.Fact;
import java.util.List;

/** Maps the model's observations to plain domain facts. Observations without text are dropped, not guessed. */
public final class FactsMapper {

    private FactsMapper() {
    }

    public static ExtractedFacts toFacts(VisionResult result) {
        return new ExtractedFacts(
                facts(result, "barcode_digits"), facts(result, "catalog_number"), facts(result, "label"),
                facts(result, "country"), facts(result, "format"), facts(result, "matrix_text"));
    }

    private static List<Fact> facts(VisionResult result, String field) {
        return result.observationsFor(field).stream()
                .filter(o -> o.value() != null && !o.value().isBlank() && o.rawText() != null && !o.rawText().isBlank())
                .map(o -> new Fact(o.value(), o.rawText())).toList();
    }
}
