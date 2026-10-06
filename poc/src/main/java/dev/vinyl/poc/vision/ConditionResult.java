package dev.vinyl.poc.vision;

import java.util.List;

/**
 * What the model saw on the vinyl surface and the sleeve, and the grades it suggests. The suggestion is visual only
 * and is never the grade used; the user's grade is. grade is "cannot_tell" when the model could not judge.
 */
public record ConditionResult(List<PhotoView> photos, List<Defect> defects, GradeSuggestion media,
                              GradeSuggestion sleeve, List<String> notAssessable, AiCall call) {

    public ConditionResult {
        photos = List.copyOf(photos);
        defects = List.copyOf(defects);
        notAssessable = List.copyOf(notAssessable);
    }

    /** view is vinyl_side_a, vinyl_side_b, sleeve_front, sleeve_back, sleeve_other, label, other or unclear. */
    public record PhotoView(String id, String view) {
    }

    /** surface is vinyl or sleeve; confidence is low, medium or high. */
    public record Defect(String surface, String kind, String photo, String location, String description,
                         String confidence) {
    }

    public record GradeSuggestion(String grade, String basis, String confidence) {
    }

    public List<Defect> defectsOn(String surface) {
        return defects.stream().filter(d -> d.surface().equals(surface)).toList();
    }
}
