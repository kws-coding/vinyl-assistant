package dev.vinyl.poc.domain;

import java.util.Locale;

/** Text helpers for comparing etched or printed text that may be partly misread. */
public final class FuzzyText {

    private FuzzyText() {
    }

    /** Uppercase letters and digits only: "R1-535225" and "r1 535225" both become "R1535225". */
    public static String alnum(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * True when some substring of text is within maxErrors single-character edits (insert, delete or
     * substitute) of pattern. Sellers' algorithm: edit distance where the match may start anywhere in text.
     */
    public static boolean approxContains(String text, String pattern, int maxErrors) {
        int m = pattern.length();
        if (m == 0) {
            return true;
        }
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int i = 0; i <= m; i++) {
            prev[i] = i;
        }
        if (prev[m] <= maxErrors) {
            return true;
        }
        for (int j = 1; j <= text.length(); j++) {
            cur[0] = 0;
            for (int i = 1; i <= m; i++) {
                int cost = pattern.charAt(i - 1) == text.charAt(j - 1) ? 0 : 1;
                cur[i] = Math.min(Math.min(cur[i - 1] + 1, prev[i] + 1), prev[i - 1] + cost);
            }
            if (cur[m] <= maxErrors) {
                return true;
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return false;
    }
}
