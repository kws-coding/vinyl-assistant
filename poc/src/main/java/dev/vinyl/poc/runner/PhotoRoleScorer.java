package dev.vinyl.poc.runner;

import dev.vinyl.poc.vision.PhotoReading;
import dev.vinyl.poc.vision.PhotoRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Throwaway. Compares the roles the model gave with the roles the user wrote in photo_roles.csv. The model's
 * photo_1, photo_2, ... follow the sorted file listing, the same order the pipeline sends them in. An unclear
 * answer is counted separately: the model is allowed to say it cannot tell, so that is not a wrong answer.
 */
public final class PhotoRoleScorer {

    private PhotoRoleScorer() {
    }

    /** problems lists each wrong or unclear photo, and each record that could not be scored. */
    public record Totals(int records, int photos, int correct, int wrong, int unclear, List<String> problems) {

        public static Totals empty() {
            return new Totals(0, 0, 0, 0, 0, List.of());
        }

        public Totals plus(Totals o) {
            List<String> all = new ArrayList<>(problems);
            all.addAll(o.problems);
            return new Totals(records + o.records, photos + o.photos, correct + o.correct, wrong + o.wrong,
                    unclear + o.unclear, all);
        }
    }

    /** truth is keyed by lower-case file name. Photos with no truth role are skipped, not counted. */
    public static Totals score(String recordId, List<String> fileNames, List<PhotoReading> readings,
                               Map<String, PhotoRole> truth) {
        if (fileNames.size() != readings.size()) {
            return new Totals(0, 0, 0, 0, 0, List.of("record " + recordId + ": " + fileNames.size()
                    + " photos on disk but " + readings.size() + " read, not scored"));
        }
        int photos = 0;
        int correct = 0;
        int wrong = 0;
        int unclear = 0;
        List<String> problems = new ArrayList<>();
        for (PhotoReading r : readings) {
            int index = indexOf(r.id());
            if (index < 0 || index >= fileNames.size()) {
                problems.add("record " + recordId + ": unreadable photo id " + r.id());
                continue;
            }
            String name = fileNames.get(index);
            PhotoRole expected = truth.get(name.toLowerCase(Locale.ROOT));
            if (expected == null) {
                continue;
            }
            photos++;
            if (r.role() == expected) {
                correct++;
            } else if (r.role() == PhotoRole.UNCLEAR) {
                unclear++;
                problems.add("record " + recordId + " " + name + ": model unclear, truth " + label(expected));
            } else {
                wrong++;
                problems.add("record " + recordId + " " + name + ": model " + label(r.role()) + ", truth "
                        + label(expected));
            }
        }
        return new Totals(1, photos, correct, wrong, unclear, problems);
    }

    private static int indexOf(String id) {
        try {
            return Integer.parseInt(id.replace("photo_", "").trim()) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String label(PhotoRole r) {
        return r.name().toLowerCase(Locale.ROOT);
    }
}
