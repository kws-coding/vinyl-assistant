package dev.vinyl.poc.runner;

import static org.junit.jupiter.api.Assertions.*;

import dev.vinyl.poc.vision.PhotoReading;
import dev.vinyl.poc.vision.PhotoRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhotoRoleScorerTest {

    private static final List<String> FILES = List.of("a.jpg", "b.jpg", "c.jpg", "d.jpg");

    private static List<PhotoReading> read(PhotoRole... roles) {
        java.util.ArrayList<PhotoReading> out = new java.util.ArrayList<>();
        for (int i = 0; i < roles.length; i++) {
            out.add(new PhotoReading("photo_" + (i + 1), roles[i]));
        }
        return out;
    }

    private static Map<String, PhotoRole> truth() {
        return Map.of("a.jpg", PhotoRole.FRONT, "b.jpg", PhotoRole.BACK, "c.jpg", PhotoRole.RUNOUT_A,
                "d.jpg", PhotoRole.BARCODE);
    }

    @Test
    void countsCorrectWrongAndUnclearSeparately() {
        PhotoRoleScorer.Totals t = PhotoRoleScorer.score("007", FILES,
                read(PhotoRole.FRONT, PhotoRole.LABELS, PhotoRole.UNCLEAR, PhotoRole.BARCODE), truth());
        assertEquals(4, t.photos());
        assertEquals(2, t.correct());
        assertEquals(1, t.wrong());
        assertEquals(1, t.unclear());
        assertEquals(2, t.problems().size());
        assertTrue(t.problems().get(0).contains("b.jpg: model labels, truth back"));
    }

    @Test
    void photosWithNoTruthRoleAreSkipped() {
        PhotoRoleScorer.Totals t = PhotoRoleScorer.score("007", FILES,
                read(PhotoRole.FRONT, PhotoRole.BACK, PhotoRole.OTHER, PhotoRole.OTHER),
                Map.of("a.jpg", PhotoRole.FRONT));
        assertEquals(1, t.photos());
        assertEquals(1, t.correct());
    }

    @Test
    void aMismatchedPhotoCountIsReportedNotScored() {
        PhotoRoleScorer.Totals t = PhotoRoleScorer.score("007", FILES, read(PhotoRole.FRONT), truth());
        assertEquals(0, t.photos());
        assertTrue(t.problems().get(0).contains("4 photos on disk but 1 read"));
    }

    @Test
    void totalsAddUp() {
        PhotoRoleScorer.Totals a = PhotoRoleScorer.score("1", FILES,
                read(PhotoRole.FRONT, PhotoRole.BACK, PhotoRole.RUNOUT_A, PhotoRole.BARCODE), truth());
        PhotoRoleScorer.Totals b = PhotoRoleScorer.score("2", FILES,
                read(PhotoRole.BACK, PhotoRole.BACK, PhotoRole.RUNOUT_A, PhotoRole.BARCODE), truth());
        PhotoRoleScorer.Totals sum = PhotoRoleScorer.Totals.empty().plus(a).plus(b);
        assertEquals(2, sum.records());
        assertEquals(8, sum.photos());
        assertEquals(7, sum.correct());
        assertEquals(1, sum.wrong());
    }

    @Test
    void theRolesCsvIsReadCaseInsensitivelyAndATypoIsAnError(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("photo_roles.csv");
        Files.writeString(f, "record_id,filename,role\n001,IMG_1.HEIC,Front\n001,img_2.heic,runout_a\n");
        Map<String, Map<String, PhotoRole>> m = PhotoRolesCsv.read(f);
        assertEquals(PhotoRole.FRONT, m.get("001").get("img_1.heic"));
        assertEquals(PhotoRole.RUNOUT_A, m.get("001").get("img_2.heic"));
        Files.writeString(f, "record_id,filename,role\n001,x.jpg,frnt\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PhotoRolesCsv.read(f));
        assertTrue(e.getMessage().contains("frnt"));
        Files.writeString(f, "id,name,role\n");
        assertThrows(IllegalArgumentException.class, () -> PhotoRolesCsv.read(f));
    }
}
