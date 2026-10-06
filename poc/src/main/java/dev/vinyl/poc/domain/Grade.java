package dev.vinyl.poc.domain;

import java.util.Locale;
import java.util.Optional;

/** The Discogs condition scale, lowest to highest. NM and M- are the same step, as on Discogs. */
public enum Grade {
    POOR("P"), FAIR("F"), GOOD("G"), GOOD_PLUS("G+"), VERY_GOOD("VG"), VERY_GOOD_PLUS("VG+"), NEAR_MINT("NM"),
    MINT("M");

    private final String label;

    Grade(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Empty for blank text, "cannot_tell" or anything that is not a grade: the caller must not guess. */
    public static Optional<Grade> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        return switch (t) {
            case "M", "MINT" -> Optional.of(MINT);
            case "NM", "NM-", "M-", "NMORM-", "NEARMINT" -> Optional.of(NEAR_MINT);
            case "VG+", "VERYGOODPLUS" -> Optional.of(VERY_GOOD_PLUS);
            case "VG", "VERYGOOD" -> Optional.of(VERY_GOOD);
            case "G+", "GOODPLUS" -> Optional.of(GOOD_PLUS);
            case "G", "GOOD" -> Optional.of(GOOD);
            case "F", "FAIR" -> Optional.of(FAIR);
            case "P", "POOR" -> Optional.of(POOR);
            default -> Optional.empty();
        };
    }
}
