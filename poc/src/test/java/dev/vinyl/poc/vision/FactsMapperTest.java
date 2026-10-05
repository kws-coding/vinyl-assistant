package dev.vinyl.poc.vision;

import static org.junit.jupiter.api.Assertions.*;

import dev.vinyl.poc.domain.ExtractedFacts;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactsMapperTest {

    @Test
    void groupsObservationsByFieldAndKeepsRawText() {
        VisionResult r = new VisionResult(List.of(), List.of(
                new Observation("barcode_digits", "081227966409", "0 81227 96640 9", "photo_1"),
                new Observation("catalog_number", "8122796640", "8122796640", "photo_1"),
                new Observation("matrix_text", "6016-01", "6016-01", "photo_10"),
                new Observation("artist", "Led Zeppelin", "Led Zeppelin", "photo_1"),
                new Observation("label", "", "ATLANTIC", "photo_1")),
                new AiCall("m", 1, 1, 1, BigDecimal.ZERO, 1));
        ExtractedFacts f = FactsMapper.toFacts(r);
        assertEquals("0 81227 96640 9", f.barcodes().get(0).rawText());
        assertEquals(1, f.catalogNumbers().size());
        assertEquals("6016-01", f.matrices().get(0).value());
        assertTrue(f.labels().isEmpty(), "an observation without a value is dropped, not guessed");
    }
}
