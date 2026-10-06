package dev.vinyl.poc.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Drafts the text for the Discogs comments field from a fixed template, so wording stays consistent and every
 * statement traces to something observed or to the user's own notes. Nothing is invented: the only claims are the
 * defects the model saw (and the user kept), "none seen in photos" where both sides were photographed, and the
 * user's own words. Discogs allows 500 characters and no HTML. The note never mentions how the record plays unless
 * the user wrote it.
 */
public final class ConditionNote {

    public static final int MAX_CHARS = 500;

    /** What the user decided about an observed defect. UNREVIEWED means they have not looked yet. */
    public enum Review {
        UNREVIEWED, REAL, FALSE_ALARM, UNSURE
    }

    /** confidence is low, medium or high; surface is vinyl or sleeve. */
    public record NoteDefect(String surface, String description, String location, String confidence, Review review) {
    }

    /** warnings are things the user should look at before using the text. valid is false when it cannot be posted as is. */
    public record Draft(String text, List<String> warnings) {

        public Draft {
            warnings = List.copyOf(warnings);
        }

        public boolean valid() {
            return text.length() <= MAX_CHARS && text.indexOf('<') < 0 && text.indexOf('>') < 0;
        }
    }

    private ConditionNote() {
    }

    public static Draft draft(List<NoteDefect> defects, boolean vinylBothSides, boolean sleeveFrontAndBack,
                              String userNotes) {
        List<String> warnings = new ArrayList<>();
        List<NoteDefect> usable = new ArrayList<>();
        int unreviewed = 0;
        int lowUnreviewed = 0;
        int unsure = 0;
        for (NoteDefect d : defects) {
            switch (d.review()) {
                case REAL -> usable.add(d);
                case UNREVIEWED -> {
                    if ("low".equals(d.confidence())) {
                        lowUnreviewed++;
                    } else {
                        usable.add(d);
                        unreviewed++;
                    }
                }
                case UNSURE -> unsure++;
                case FALSE_ALARM -> { }
            }
        }
        if (unreviewed > 0) {
            warnings.add(unreviewed + " defect(s) used before you reviewed them");
        }
        if (lowUnreviewed > 0) {
            warnings.add(lowUnreviewed + " low-confidence defect(s) left out because they are not reviewed");
        }
        if (unsure > 0) {
            warnings.add(unsure + " defect(s) you marked unsure were left out");
        }
        usable.sort(Comparator.comparingInt((NoteDefect d) -> rank(d.confidence())).reversed());

        String cleanedNotes = clean(userNotes == null ? "" : userNotes);
        if (userNotes != null && !cleanedNotes.equals(normalise(userNotes))) {
            warnings.add("< and > were removed from your notes because Discogs does not allow HTML");
        }

        List<NoteDefect> kept = new ArrayList<>(usable);
        String text = build(kept, vinylBothSides, sleeveFrontAndBack, cleanedNotes);
        int dropped = 0;
        while (text.length() > MAX_CHARS && !kept.isEmpty()) {
            kept.remove(kept.size() - 1);
            dropped++;
            text = build(kept, vinylBothSides, sleeveFrontAndBack, cleanedNotes);
        }
        if (dropped > 0) {
            warnings.add("shortened: " + dropped + " defect(s) dropped to fit " + MAX_CHARS + " characters");
        }
        if (text.length() > MAX_CHARS) {
            warnings.add("over " + MAX_CHARS + " characters even without defects; shorten your notes");
        }
        if (!vinylBothSides) {
            warnings.add("not both sides of the vinyl were photographed; no 'none seen' statement for the vinyl");
        }
        if (!sleeveFrontAndBack) {
            warnings.add("not both sides of the sleeve were photographed; no 'none seen' statement for the sleeve");
        }
        return new Draft(text, warnings);
    }

    private static String build(List<NoteDefect> defects, boolean vinylBothSides, boolean sleeveFrontAndBack,
                                String notes) {
        List<String> parts = new ArrayList<>();
        clause(parts, "Vinyl", defects, "vinyl", vinylBothSides, "no marks seen in photos");
        clause(parts, "Sleeve", defects, "sleeve", sleeveFrontAndBack, "no defects seen in photos");
        if (!notes.isEmpty()) {
            parts.add(notes);
        }
        return String.join(" ", parts);
    }

    private static void clause(List<String> parts, String label, List<NoteDefect> defects, String surface,
                               boolean covered, String none) {
        List<String> items = defects.stream().filter(d -> d.surface().equals(surface)).map(ConditionNote::describe)
                .collect(Collectors.toList());
        if (!items.isEmpty()) {
            parts.add(label + ": " + String.join("; ", items) + ".");
        } else if (covered) {
            parts.add(label + ": " + none + ".");
        }
    }

    private static String describe(NoteDefect d) {
        String text = clean(d.description());
        if (text.length() > 1 && Character.isUpperCase(text.charAt(0)) && Character.isLowerCase(text.charAt(1))) {
            text = text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
        }
        String where = clean(d.location() == null ? "" : d.location());
        return where.isEmpty() ? text : text + " (" + where + ")";
    }

    private static int rank(String confidence) {
        return switch (confidence == null ? "" : confidence) {
            case "high" -> 2;
            case "medium" -> 1;
            default -> 0;
        };
    }

    private static String normalise(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Collapses whitespace and removes the characters HTML needs. */
    private static String clean(String s) {
        return normalise(s.replace("<", "").replace(">", ""));
    }
}
