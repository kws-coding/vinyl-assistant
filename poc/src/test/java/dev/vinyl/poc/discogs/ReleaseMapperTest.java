package dev.vinyl.poc.discogs;

import static org.junit.jupiter.api.Assertions.*;

import dev.vinyl.poc.domain.ReleaseInfo;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseMapperTest {

    private static ReleaseDto dto(List<ReleaseDto.Label> labels, List<ReleaseDto.Format> formats,
                                  List<ReleaseDto.Identifier> identifiers) {
        return new ReleaseDto(42, "Title", "US", 1969, List.of(new ReleaseDto.Artist("Artist")),
                labels, formats, identifiers, null, null);
    }

    @Test
    void mapsPlainValuesAndSplitsIdentifiersByType() {
        ReleaseInfo info = ReleaseMapper.toInfo(dto(
                List.of(new ReleaseDto.Label("Atlantic", "SD 8236")),
                List.of(new ReleaseDto.Format("Vinyl", List.of("LP", "Album", "Stereo"), null)),
                List.of(new ReleaseDto.Identifier("Barcode", "081227966409"),
                        new ReleaseDto.Identifier("Matrix / Runout", "BK06016-01 B1"),
                        new ReleaseDto.Identifier("Rights Society", "ASCAP"))));
        assertEquals("42", info.releaseId());
        assertEquals("US", info.country());
        assertEquals(List.of("081227966409"), info.barcodes());
        assertEquals(List.of("BK06016-01 B1"), info.matrices());
        assertEquals(List.of("SD 8236"), info.catalogNumbers());
        assertEquals(List.of("Atlantic"), info.labels());
        assertEquals(List.of("LP, Album, Stereo"), info.formatDescriptions());
    }

    @Test
    void nullListsAndNullValuesBecomeEmptyLists() {
        ReleaseInfo info = ReleaseMapper.toInfo(dto(null, null, null));
        assertTrue(info.labels().isEmpty());
        assertTrue(info.catalogNumbers().isEmpty());
        assertTrue(info.barcodes().isEmpty());
        assertTrue(info.matrices().isEmpty());
        assertTrue(info.formatDescriptions().isEmpty());
        ReleaseInfo noDescriptions = ReleaseMapper.toInfo(dto(List.of(),
                List.of(new ReleaseDto.Format("Vinyl", null, null)), List.of()));
        assertTrue(noDescriptions.formatDescriptions().isEmpty());
    }

    @Test
    void blankValuesAreDroppedAndDuplicatesCollapsed() {
        ReleaseInfo info = ReleaseMapper.toInfo(dto(
                Arrays.asList(new ReleaseDto.Label("Atlantic", "SD 8236"), new ReleaseDto.Label("Atlantic", "  "),
                        new ReleaseDto.Label("Atlantic", null)),
                List.of(), List.of()));
        assertEquals(List.of("Atlantic"), info.labels());
        assertEquals(List.of("SD 8236"), info.catalogNumbers());
    }
}
