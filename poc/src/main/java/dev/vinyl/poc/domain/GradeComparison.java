package dev.vinyl.poc.domain;

/**
 * How a suggested grade sits against the user's grade. The user's grade is always the one used; this only
 * says whether the two differ, by how many steps, and which way, so the user can look again.
 */
public record GradeComparison(Kind kind, int steps) {

    public enum Kind {
        SAME, SUGGESTED_LOWER, SUGGESTED_HIGHER, CANNOT_COMPARE
    }

    /** Either grade may be null (blank, unknown, or "cannot tell"). */
    public static GradeComparison compare(Grade user, Grade suggested) {
        if (user == null || suggested == null) {
            return new GradeComparison(Kind.CANNOT_COMPARE, 0);
        }
        int diff = suggested.ordinal() - user.ordinal();
        if (diff == 0) {
            return new GradeComparison(Kind.SAME, 0);
        }
        return new GradeComparison(diff < 0 ? Kind.SUGGESTED_LOWER : Kind.SUGGESTED_HIGHER, Math.abs(diff));
    }

    public boolean differs() {
        return kind == Kind.SUGGESTED_LOWER || kind == Kind.SUGGESTED_HIGHER;
    }

    /** True when the suggestion is the same or one step away. Cannot-compare is not counted as close. */
    public boolean withinOneStep() {
        return kind == Kind.SAME || (differs() && steps == 1);
    }

    public String describe() {
        return switch (kind) {
            case SAME -> "matches";
            case SUGGESTED_LOWER -> "suggested is " + steps + " step" + (steps == 1 ? "" : "s")
                    + " below yours (yours may be generous)";
            case SUGGESTED_HIGHER -> "suggested is " + steps + " step" + (steps == 1 ? "" : "s")
                    + " above yours (yours may be harsh)";
            case CANNOT_COMPARE -> "cannot compare";
        };
    }
}
