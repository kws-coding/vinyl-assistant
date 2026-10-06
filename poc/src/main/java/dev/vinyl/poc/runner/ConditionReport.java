package dev.vinyl.poc.runner;

import dev.vinyl.poc.domain.Grade;
import dev.vinyl.poc.domain.GradeComparison;
import dev.vinyl.poc.vision.ConditionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Throwaway. Builds the condition report row and the review sheet rows for one record. */
public final class ConditionReport {

    static final String LIMIT_NOTE =
            "Visual only: warps, surface noise and groove wear cannot be seen in photos. The user's grade is the one used.";

    static final List<String> HEADER = List.of("record_id", "user_media_grade", "suggested_media_grade",
            "media_comparison", "media_steps", "media_confidence", "media_basis", "user_sleeve_grade",
            "suggested_sleeve_grade", "sleeve_comparison", "sleeve_steps", "sleeve_confidence", "sleeve_basis",
            "flag", "vinyl_defects", "sleeve_defects", "defects", "not_assessable", "photos", "vision_cached",
            "ai_cost_usd", "input_tokens", "output_tokens", "limits", "user_decision");

    static final List<String> REVIEW_HEADER = List.of("record_id", "item_type", "item", "detail", "verdict");

    private ConditionReport() {
    }

    public static List<String> row(RecordInput rec, ConditionResult r, boolean cached) {
        GradeComparison media = compare(rec.mediaGrade(), r.media().grade());
        GradeComparison sleeve = compare(rec.sleeveGrade(), r.sleeve().grade());
        List<String> flags = new ArrayList<>();
        if (media.differs()) {
            flags.add("media: " + media.describe());
        }
        if (sleeve.differs()) {
            flags.add("sleeve: " + sleeve.describe());
        }
        String defects = r.defects().stream().map(d -> d.surface() + " " + d.kind() + " (" + d.confidence() + ") "
                + d.photo()).collect(Collectors.joining("; "));
        return List.of(rec.id(), rec.mediaGrade(), r.media().grade(), media.kind().name(),
                String.valueOf(media.steps()), r.media().confidence(), r.media().basis(), rec.sleeveGrade(),
                r.sleeve().grade(), sleeve.kind().name(), String.valueOf(sleeve.steps()), r.sleeve().confidence(),
                r.sleeve().basis(), String.join("; ", flags), String.valueOf(r.defectsOn("vinyl").size()),
                String.valueOf(r.defectsOn("sleeve").size()), defects, String.join("; ", r.notAssessable()),
                String.valueOf(r.photos().size()), String.valueOf(cached), cached ? "0" : r.call().costUsd().toPlainString(),
                cached ? "0" : String.valueOf(r.call().inputTokens()),
                cached ? "0" : String.valueOf(r.call().outputTokens()), LIMIT_NOTE, "");
    }

    /**
     * One row per observed defect (verdict to be filled in: real, false_alarm or unsure) and one per defect the
     * user already knew about (verdict: found or missed). Code counts the verdicts; the model never judges its own.
     */
    public static List<List<String>> reviewRows(RecordInput rec, ConditionResult r) {
        List<List<String>> rows = new ArrayList<>();
        for (ConditionResult.Defect d : r.defects()) {
            rows.add(List.of(rec.id(), "observed", d.surface() + " " + d.kind() + ": " + d.description(),
                    d.photo() + "; " + d.location() + "; confidence " + d.confidence(), ""));
        }
        for (String known : knownDefects(rec.knownDefects())) {
            rows.add(List.of(rec.id(), "known", known, "", ""));
        }
        return rows;
    }

    static List<String> knownDefects(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split("[;,\\n]")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    private static GradeComparison compare(String userGrade, String suggested) {
        return GradeComparison.compare(Grade.parse(userGrade).orElse(null), Grade.parse(suggested).orElse(null));
    }
}
