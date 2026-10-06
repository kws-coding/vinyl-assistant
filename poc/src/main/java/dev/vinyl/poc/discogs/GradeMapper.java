package dev.vinyl.poc.discogs;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Maps the user's grade ("VG+", "NM", ...) to the key Discogs uses in price suggestions. */
public final class GradeMapper {

    private GradeMapper() {
    }

    public static Optional<String> suggestionKey(String grade, Set<String> availableKeys) {
        String marker = switch (grade == null ? "" : grade.trim().toUpperCase(Locale.ROOT).replace(" ", "")) {
            case "M", "MINT" -> "(M)";
            case "NM", "NM-", "M-" -> "(NM or M-)";
            case "VG+" -> "(VG+)";
            case "VG" -> "(VG)";
            case "G+" -> "(G+)";
            case "G" -> "(G)";
            case "F", "FAIR" -> "(F)";
            case "P", "POOR" -> "(P)";
            default -> null;
        };
        if (marker == null) {
            return Optional.empty();
        }
        return availableKeys.stream().filter(k -> k.endsWith(marker)).findFirst();
    }
}
