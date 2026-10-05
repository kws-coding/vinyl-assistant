package dev.vinyl.poc.vision;

import java.util.List;

public record VisionResult(List<PhotoReading> photos, List<Observation> observations, AiCall call) {

    public VisionResult {
        photos = List.copyOf(photos);
        observations = List.copyOf(observations);
    }

    public List<Observation> observationsFor(String field) {
        return observations.stream().filter(o -> o.field().equals(field)).toList();
    }
}
