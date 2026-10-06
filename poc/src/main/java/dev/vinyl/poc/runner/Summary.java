package dev.vinyl.poc.runner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Throwaway. Turns report rows into the measures in the project brief: per-bucket correct, flagged, or wrong
 * and unflagged; high-value unflagged errors; AI cost; pricing error; human effort; photo role accuracy. It also
 * checks the DRAFT bars from THRESHOLDS.md, which the user has not approved yet; keep the constants in step.
 */
public final class Summary {

    // Draft bars. See THRESHOLDS.md.
    static final double DRAFT_MAX_MEDIAN_MINUTES = 10;
    static final double DRAFT_MAX_MEAN_COST = 0.07;
    static final double DRAFT_MAX_COST = 0.10;
    static final double DRAFT_MAX_UNFLAGGED_SHARE = 0.10;
    static final double DRAFT_MAX_MEDIAN_TOUCHES = 1;
    static final double DRAFT_MAX_MEDIAN_ANNOYANCE = 2;
    static final Map<String, Double> DRAFT_MIN_SAFE_SHARE = Map.of(
            "easy", 0.90, "ambiguous", 0.80, "price_sensitive", 0.80, "runout_dependent", 0.80);
    private static final List<String> BUCKET_ORDER =
            List.of("easy", "ambiguous", "price_sensitive", "runout_dependent", "edge");

    private Summary() {
    }

    /** roles may be null when there is no photo_roles.csv. */
    public static String render(List<Map<String, String>> rows, double highValueUsd, PhotoRoleScorer.Totals roles) {
        StringBuilder out = new StringBuilder();
        List<Map<String, String>> keyed = rows.stream().filter(r -> !val(r, "outcome").isEmpty()).toList();
        out.append(String.format("Records in report: %d (%d with an answer key)%n", rows.size(), keyed.size()));
        out.append("Bars below are DRAFT (THRESHOLDS.md), not yet approved.\n\n");

        // per bucket
        Map<String, List<Map<String, String>>> byBucket = new TreeMap<>(Comparator
                .comparingInt((String b) -> BUCKET_ORDER.contains(b) ? BUCKET_ORDER.indexOf(b) : BUCKET_ORDER.size())
                .thenComparing(b -> b));
        keyed.forEach(r -> byBucket.computeIfAbsent(val(r, "bucket").isEmpty() ? "(none)" : val(r, "bucket"),
                k -> new ArrayList<>()).add(r));
        out.append(String.format("%-18s %3s %8s %16s %18s %16s %s%n", "bucket", "n", "correct", "flagged_correct",
                "flagged_incorrect", "WRONG_UNFLAGGED", "safe share (bar 3)"));
        Boolean bar3 = null;
        for (Map.Entry<String, List<Map<String, String>>> e : byBucket.entrySet()) {
            List<Map<String, String>> g = e.getValue();
            long c = count(g, "correct");
            long fc = count(g, "flagged_correct");
            long fi = count(g, "flagged_incorrect");
            long w = count(g, "WRONG_UNFLAGGED");
            double safe = (g.size() - w) / (double) g.size();
            Double min = DRAFT_MIN_SAFE_SHARE.get(e.getKey());
            String bar = min == null ? "no bar" : (safe >= min ? "PASS" : "FAIL") + String.format(" (>= %.0f%%)", min * 100);
            if (min != null) {
                bar3 = (bar3 == null || bar3) && safe >= min;
            }
            out.append(String.format("%-18s %3d %8d %16d %18d %16d %.0f%%  %s%n", e.getKey(), g.size(), c, fc, fi, w,
                    safe * 100, bar));
        }

        // wrong and unflagged
        out.append('\n');
        List<Map<String, String>> wrong = keyed.stream().filter(r -> val(r, "outcome").equals("WRONG_UNFLAGGED")).toList();
        int high = 0;
        int unknown = 0;
        for (Map<String, String> r : wrong) {
            Double gap = num(r, "error_gap_usd");
            String kind;
            if (gap == null) {
                unknown++;
                kind = "price gap unknown";
            } else if (gap >= highValueUsd) {
                high++;
                kind = String.format("HIGH VALUE, gap $%.2f", gap);
            } else {
                kind = String.format("gap $%.2f", gap);
            }
            out.append(String.format("Wrong and unflagged: record %s picked %s, truth %s (%s)%n", val(r, "record_id"),
                    val(r, "top_release_id"), val(r, "truth_release_id"), kind));
        }
        String bar1 = keyed.isEmpty() ? "n/a" : high > 0 ? "FAIL" : unknown > 0 ? "CHECK (a gap is unknown)" : "PASS";
        out.append(String.format("Bar 1, unflagged high-value errors (>= $%.0f): %d. %s%n", highValueUsd, high, bar1));
        String bar4 = keyed.isEmpty() ? "n/a"
                : status(wrong.size() / (double) keyed.size() <= DRAFT_MAX_UNFLAGGED_SHARE);
        out.append(String.format("Bar 4, unflagged wrong, any value: %d of %d. %s%n", wrong.size(), keyed.size(), bar4));
        out.append(String.format("Bar 3, safe share per bucket: %s%n", bar3 == null ? "n/a" : status(bar3)));

        // flags caused by missing runouts
        List<Map<String, String>> runoutFlags = rows.stream()
                .filter(r -> val(r, "decision").equals("NEEDS_RUNOUT_PHOTOS")).toList();
        long missingRunouts = runoutFlags.stream().filter(r -> val(r, "missing_roles").contains("runout")).count();
        out.append(String.format("%nFlagged for runout photos: %d (%d of them with no runout photo among the roles)%n",
                runoutFlags.size(), missingRunouts));

        // cost
        List<Double> costs = rows.stream().filter(r -> val(r, "vision_cached").equals("false"))
                .map(r -> num(r, "ai_cost_usd")).filter(d -> d != null).toList();
        if (costs.isEmpty()) {
            out.append("AI cost: no paid calls in this report. Bar 5: n/a\n");
        } else {
            double mean = costs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double max = costs.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            out.append(String.format("AI cost over %d paid record(s): mean $%.4f, max $%.4f. Bar 5: %s%n", costs.size(),
                    mean, max, status(mean <= DRAFT_MAX_MEAN_COST && max <= DRAFT_MAX_COST)));
        }

        // pricing error by how sure the user was of the eBay pressing
        Map<String, List<Double>> errs = new LinkedHashMap<>();
        for (Map<String, String> r : rows) {
            Double e = num(r, "pricing_error_usd");
            if (e != null) {
                String m = val(r, "ebay_match").isEmpty() ? "unsure" : val(r, "ebay_match").toLowerCase(Locale.ROOT);
                errs.computeIfAbsent(m, k -> new ArrayList<>()).add(e);
            }
        }
        if (errs.isEmpty()) {
            out.append("Pricing error vs eBay: no records with eBay data. Bar 6: n/a (still under discussion)\n");
        } else {
            errs.forEach((m, list) -> out.append(String.format(
                    "Pricing error vs eBay, match=%s: n=%d, median absolute $%.2f, median signed $%.2f%n", m,
                    list.size(), median(list.stream().map(Math::abs).toList()), median(list))));
            out.append("Bar 6 is still under discussion, so no pass or fail.\n");
        }

        // human effort
        out.append(medianLine(rows, "human_minutes", "Human minutes per record", DRAFT_MAX_MEDIAN_MINUTES, "Bar 2"));
        out.append(medianLine(rows, "discogs_touches", "Discogs touches per record", DRAFT_MAX_MEDIAN_TOUCHES, "Bar 8"));
        out.append(medianLine(rows, "annoyance", "Annoyance (1 to 5)", DRAFT_MAX_MEDIAN_ANNOYANCE, "Bar 8"));

        // roles
        if (roles == null) {
            out.append("Photo role accuracy: no photo_roles.csv. Bar 7: n/a\n");
        } else if (roles.photos() == 0) {
            out.append("Photo role accuracy: no photos could be scored. Bar 7: n/a\n");
        } else {
            out.append(String.format("Photo role accuracy: %d of %d correct (%.0f%%), %d wrong, %d unclear, across %d record(s). Bar 7: %s%n",
                    roles.correct(), roles.photos(), 100.0 * roles.correct() / roles.photos(), roles.wrong(),
                    roles.unclear(), roles.records(), status(roles.correct() / (double) roles.photos() >= 0.90)));
        }
        if (roles != null) {
            roles.problems().forEach(p -> out.append("  ").append(p).append('\n'));
        }
        return out.toString();
    }

    private static String medianLine(List<Map<String, String>> rows, String column, String label, double max, String bar) {
        List<Double> values = rows.stream().map(r -> num(r, column)).filter(d -> d != null).toList();
        if (values.isEmpty()) {
            return label + ": not filled in yet. " + bar + ": n/a\n";
        }
        double m = median(values);
        return String.format("%s: median %.1f over %d record(s). %s: %s (draft: %.0f or less)%n", label, m, values.size(),
                bar, status(m <= max), max);
    }

    private static String status(boolean pass) {
        return pass ? "PASS" : "FAIL";
    }

    private static long count(List<Map<String, String>> rows, String outcome) {
        return rows.stream().filter(r -> val(r, "outcome").equals(outcome)).count();
    }

    static double median(List<Double> values) {
        List<Double> s = values.stream().sorted().toList();
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2;
    }

    private static String val(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.trim();
    }

    private static Double num(Map<String, String> row, String key) {
        String v = val(row, key);
        if (v.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(v.replace("$", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
