package dev.vinyl.poc.runner;

import dev.vinyl.poc.domain.ConditionNote;
import dev.vinyl.poc.domain.ConditionNote.NoteDefect;
import dev.vinyl.poc.domain.ConditionNote.Review;
import dev.vinyl.poc.vision.ConditionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Throwaway. Joins one record's cached condition result, its marked review rows and the user's notes into a note draft. */
public final class ConditionNotes {

    private ConditionNotes() {
    }

    /** reviewRows are the rows of the review sheet for this one record (any item type); may be empty. */
    public static ConditionNote.Draft draft(RecordInput rec, ConditionResult r, List<Map<String, String>> reviewRows) {
        List<String> extra = new ArrayList<>();
        List<Map<String, String>> observed = reviewRows.stream()
                .filter(x -> "observed".equals(x.getOrDefault("item_type", "").trim())).toList();
        boolean aligned = observed.size() == r.defects().size();
        if (!aligned && !r.defects().isEmpty()) {
            extra.add("review sheet does not match the observed defects (" + observed.size() + " rows, "
                    + r.defects().size() + " defects); all defects treated as not reviewed");
        }
        List<NoteDefect> defects = new ArrayList<>();
        for (int i = 0; i < r.defects().size(); i++) {
            ConditionResult.Defect d = r.defects().get(i);
            String verdict = aligned ? observed.get(i).getOrDefault("verdict", "") : "";
            Review review = parse(verdict, extra);
            defects.add(new NoteDefect(d.surface(), d.description(), d.location(), d.confidence(), review));
        }
        Set<String> views = r.photos().stream().map(ConditionResult.PhotoView::view).collect(Collectors.toSet());
        boolean vinyl = views.contains("vinyl_side_a") && views.contains("vinyl_side_b");
        boolean sleeve = views.contains("sleeve_front") && views.contains("sleeve_back");
        ConditionNote.Draft base = ConditionNote.draft(defects, vinyl, sleeve, rec.listingNotes());
        List<String> warnings = new ArrayList<>(extra);
        warnings.addAll(base.warnings());
        return new ConditionNote.Draft(base.text(), warnings);
    }

    private static Review parse(String verdict, List<String> warnings) {
        String v = verdict == null ? "" : verdict.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "" -> Review.UNREVIEWED;
            case "real" -> Review.REAL;
            case "false_alarm" -> Review.FALSE_ALARM;
            case "unsure" -> Review.UNSURE;
            default -> {
                warnings.add("verdict '" + v + "' not recognised (use real, false_alarm or unsure); treated as not reviewed");
                yield Review.UNREVIEWED;
            }
        };
    }
}
