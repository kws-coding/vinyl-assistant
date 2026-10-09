package dev.vinyl.poc.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MixedPhotosCheckTest {

    @Test
    void sameAlbumReadWithDifferentCaseAndSpacingIsNotAConflict() {
        assertTrue(MixedPhotosCheck.conflict(List.of("DEVO", "D E V O", "Devo"),
                List.of("Freedom Of Choice", "FREEDOM OF CHOICE")).isEmpty());
        assertTrue(MixedPhotosCheck.conflict(List.of("David Bowie"), List.of("Pin Ups", "PINUPS")).isEmpty());
    }

    @Test
    void aShorterFormOfTheSameNameIsNotAConflict() {
        assertTrue(MixedPhotosCheck.conflict(List.of("Led Zeppelin"), List.of("Led Zeppelin II", "Led Zeppelin")).isEmpty());
    }

    @Test
    void twoDifferentArtistsAreAConflict() {
        var c = MixedPhotosCheck.conflict(List.of("STEEL PULSE", "David Bowie"), List.of());
        assertTrue(c.isPresent());
        assertEquals("different artists read: \"STEEL PULSE\" and \"David Bowie\"", c.get());
    }

    @Test
    void twoDifferentTitlesAreAConflictEvenWithNoArtist() {
        assertTrue(MixedPhotosCheck.conflict(List.of(), List.of("BABYLON THE BANDIT", "Pin Ups")).isPresent());
    }

    @Test
    void tinyReadsAreIgnored() {
        assertTrue(MixedPhotosCheck.conflict(List.of("AB", "Devo"), List.of()).isEmpty());
    }
}
