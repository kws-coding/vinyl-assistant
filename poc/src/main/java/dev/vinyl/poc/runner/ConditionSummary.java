package dev.vinyl.poc.runner;

import java.util.List;
import java.util.Map;

/**
 * Throwaway. Scores the condition experiment from the condition report and the review sheet the user filled in.
 * No pass/fail bars yet: none has been proposed or approved for this experiment.
 */
public final class ConditionSummary {

    private ConditionSummary() {
    }

    public static String render(List<Map<String, String>> report, List<Map<String, String>> review) {
        StringBuilder out = new StringBuilder();
        out.append(String.format("Records: %d%n", report.size()));
        grades(out, "Media grade", report, "media_comparison", "media_steps");
        grades(out, "Sleeve grade", report, "sleeve_comparison", "sleeve_steps");

        long flagged = report.stream().filter(r -> !val(r, "flag").isEmpty()).count();
        long change = report.stream().filter(r -> !val(r, "flag").isEmpty() && val(r, "user_decision").equals("change")).count();
        long keep = report.stream().filter(r -> !val(r, "flag").isEmpty() && val(r, "user_decision").equals("keep")).count();
        out.append(String.format("Flags: %d record(s) where a suggested grade differs from yours.%n", flagged));
        if (change + keep == 0) {
            out.append("  user_decision not filled in yet (keep or change), so it is unknown how often a flag would have changed your grade.\n");
        } else {
            out.append(String.format("  after seeing the flag you would change %d and keep %d (%.0f%% would change)%n", change, keep,
                    100.0 * change / (change + keep)));
        }

        long real = count(review, "observed", "real");
        long falseAlarm = count(review, "observed", "false_alarm");
        long unsure = count(review, "observed", "unsure");
        long observed = review.stream().filter(r -> val(r, "item_type").equals("observed")).count();
        long found = count(review, "known", "found");
        long missed = count(review, "known", "missed");
        long known = review.stream().filter(r -> val(r, "item_type").equals("known")).count();
        out.append(String.format("Observed defects: %d (real %d, false alarm %d, unsure %d, not yet marked %d)%n", observed, real,
                falseAlarm, unsure, observed - real - falseAlarm - unsure));
        if (real + falseAlarm > 0) {
            out.append(String.format("  false alarms are %.0f%% of the observed defects you marked%n",
                    100.0 * falseAlarm / (real + falseAlarm)));
        }
        out.append(String.format("Your known defects: %d (found by the tool %d, missed %d, not yet marked %d)%n", known, found,
                missed, known - found - missed));
        if (found + missed > 0) {
            out.append(String.format("  the tool missed %.0f%% of the defects you already knew about%n",
                    100.0 * missed / (found + missed)));
        }

        double cost = report.stream().filter(r -> val(r, "vision_cached").equals("false"))
                .mapToDouble(r -> num(r, "ai_cost_usd")).sum();
        out.append(String.format("AI cost for paid records: $%.4f%n", cost));
        out.append(ConditionReport.LIMIT_NOTE).append('\n');
        return out.toString();
    }

    private static void grades(StringBuilder out, String label, List<Map<String, String>> report, String kindCol,
                               String stepsCol) {
        long compared = report.stream().filter(r -> !val(r, kindCol).equals("CANNOT_COMPARE") && !val(r, kindCol).isEmpty()).count();
        long cannot = report.size() - compared;
        long same = report.stream().filter(r -> val(r, kindCol).equals("SAME")).count();
        long lower = report.stream().filter(r -> val(r, kindCol).equals("SUGGESTED_LOWER")).count();
        long higher = report.stream().filter(r -> val(r, kindCol).equals("SUGGESTED_HIGHER")).count();
        long within = report.stream().filter(r -> val(r, kindCol).equals("SAME")
                || ((val(r, kindCol).equals("SUGGESTED_LOWER") || val(r, kindCol).equals("SUGGESTED_HIGHER"))
                && val(r, stepsCol).equals("1"))).count();
        out.append(String.format("%s: compared %d, cannot compare %d (blank grade or the tool said cannot_tell)%n", label,
                compared, cannot));
        if (compared > 0) {
            out.append(String.format("  same as yours %d (%.0f%%), within one step %d (%.0f%%)%n", same, 100.0 * same / compared,
                    within, 100.0 * within / compared));
            out.append(String.format("  suggested below yours %d (yours may be generous), above yours %d (yours may be harsh)%n",
                    lower, higher));
        }
    }

    private static long count(List<Map<String, String>> review, String type, String verdict) {
        return review.stream().filter(r -> val(r, "item_type").equals(type) && val(r, "verdict").equalsIgnoreCase(verdict)).count();
    }

    private static String val(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.trim();
    }

    private static double num(Map<String, String> row, String key) {
        try {
            return Double.parseDouble(val(row, key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
