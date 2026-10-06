package dev.vinyl.poc.runner;

import java.util.List;
import java.util.Map;

/**
 * Throwaway. Scores the condition experiment from the condition report and the review sheet the user filled in.
 * It also checks the DRAFT condition bars from THRESHOLDS.md, which the user has not approved yet; keep the
 * constants in step. A bar is not judged when the sample is too small to mean anything.
 */
public final class ConditionSummary {

    // Draft bars. See THRESHOLDS.md.
    static final int MIN_RECORDS = 8;
    static final int MIN_MARKED_OBSERVED = 10;
    static final int MIN_MARKED_KNOWN = 5;
    static final double MIN_WITHIN_ONE_STEP = 0.70;
    static final double MAX_TWO_PLUS_STEPS = 0.20;
    static final double MAX_FALSE_ALARM_SHARE = 0.40;
    static final double MAX_MISSED_SHARE = 0.40;
    static final double MAX_CANNOT_TELL_SHARE = 0.30;
    static final double MAX_MEAN_COST = 0.06;
    static final double MAX_COST = 0.10;

    private ConditionSummary() {
    }

    public static String render(List<Map<String, String>> report, List<Map<String, String>> review) {
        StringBuilder out = new StringBuilder();
        out.append(String.format("Records: %d%n", report.size()));
        out.append("Bars below are DRAFT (THRESHOLDS.md), not yet approved.\n\n");
        Grades media = grades(out, "Media grade", report, "media_comparison", "media_steps");
        Grades sleeve = grades(out, "Sleeve grade", report, "sleeve_comparison", "sleeve_steps");

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
        long notVisible = count(review, "known", "not_visible");
        long known = review.stream().filter(r -> val(r, "item_type").equals("known")).count();
        out.append(String.format("Observed defects: %d (real %d, false alarm %d, unsure %d, not yet marked %d)%n", observed, real,
                falseAlarm, unsure, observed - real - falseAlarm - unsure));
        if (real + falseAlarm > 0) {
            out.append(String.format("  false alarms are %.0f%% of the observed defects you marked real or false alarm%n",
                    100.0 * falseAlarm / (real + falseAlarm)));
        }
        out.append(String.format("Your known defects: %d (found by the tool %d, missed %d, not visible in photos %d, not yet marked %d)%n",
                known, found, missed, notVisible, known - found - missed - notVisible));
        if (found + missed > 0) {
            out.append(String.format("  the tool missed %.0f%% of the visible defects you already knew about%n",
                    100.0 * missed / (found + missed)));
        }

        List<Double> costs = report.stream().filter(r -> val(r, "vision_cached").equals("false"))
                .map(r -> num(r, "ai_cost_usd")).toList();
        double cost = costs.stream().mapToDouble(Double::doubleValue).sum();
        out.append(String.format("AI cost for paid records: $%.4f%n%n", cost));

        out.append("Draft bars:\n");
        out.append(bar("Media within one step of yours >= 70%", media.compared, MIN_RECORDS,
                media.compared > 0 && media.within / (double) media.compared >= MIN_WITHIN_ONE_STEP));
        out.append(bar("Sleeve within one step of yours >= 70%", sleeve.compared, MIN_RECORDS,
                sleeve.compared > 0 && sleeve.within / (double) sleeve.compared >= MIN_WITHIN_ONE_STEP));
        out.append(bar("Media two or more steps from yours (either way) <= 20%", media.compared, MIN_RECORDS,
                media.compared > 0 && media.twoPlus / (double) media.compared <= MAX_TWO_PLUS_STEPS));
        long cannotTell = report.stream().filter(r -> val(r, "suggested_media_grade").equals("cannot_tell")).count();
        out.append(bar("Media 'cannot_tell' <= 30% of records", report.size(), MIN_RECORDS,
                !report.isEmpty() && cannotTell / (double) report.size() <= MAX_CANNOT_TELL_SHARE));
        out.append(bar("False alarms <= 40% of marked observed defects", (int) (real + falseAlarm), MIN_MARKED_OBSERVED,
                real + falseAlarm > 0 && falseAlarm / (double) (real + falseAlarm) <= MAX_FALSE_ALARM_SHARE));
        out.append(bar("Missed <= 40% of your visible known defects", (int) (found + missed), MIN_MARKED_KNOWN,
                found + missed > 0 && missed / (double) (found + missed) <= MAX_MISSED_SHARE));
        double mean = costs.isEmpty() ? 0 : cost / costs.size();
        double max = costs.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        out.append(bar("Cost per paid record: mean <= $0.06, none above $0.10", costs.size(), 1,
                mean <= MAX_MEAN_COST && max <= MAX_COST));
        out.append("Direction (suggested above or below yours) is reported, not judged: a visual grade is expected to run above"
                + " a grade that reflects play wear.\n");
        out.append(ConditionReport.LIMIT_NOTE).append('\n');
        return out.toString();
    }

    private record Grades(int compared, int within, int twoPlus) {
    }

    private static Grades grades(StringBuilder out, String label, List<Map<String, String>> report, String kindCol,
                                 String stepsCol) {
        int compared = 0;
        int same = 0;
        int lower = 0;
        int higher = 0;
        int within = 0;
        int twoPlus = 0;
        for (Map<String, String> r : report) {
            String kind = val(r, kindCol);
            if (kind.isEmpty() || kind.equals("CANNOT_COMPARE")) {
                continue;
            }
            compared++;
            int steps = (int) num(r, stepsCol);
            if (kind.equals("SAME")) {
                same++;
                within++;
            } else {
                if (kind.equals("SUGGESTED_LOWER")) {
                    lower++;
                } else if (kind.equals("SUGGESTED_HIGHER")) {
                    higher++;
                }
                if (steps <= 1) {
                    within++;
                } else {
                    twoPlus++;
                }
            }
        }
        out.append(String.format("%s: compared %d, cannot compare %d (blank grade or the tool said cannot_tell)%n", label,
                compared, report.size() - compared));
        if (compared > 0) {
            out.append(String.format("  same as yours %d (%.0f%%), within one step %d (%.0f%%)%n", same, 100.0 * same / compared,
                    within, 100.0 * within / compared));
            out.append(String.format("  suggested below yours %d (yours may be generous), above yours %d (yours may be harsh)%n",
                    lower, higher));
        }
        return new Grades(compared, within, twoPlus);
    }

    private static String bar(String text, int sample, int minSample, boolean pass) {
        if (sample < minSample) {
            return String.format("  %s: n/a (needs %d, have %d)%n", text, minSample, sample);
        }
        return String.format("  %s: %s%n", text, pass ? "PASS" : "FAIL");
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
