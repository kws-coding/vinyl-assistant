package dev.vinyl.poc.vision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/** Keeps our own model output on disk so re-running a report does not pay for the call again. */
public class ConditionCache {

    private final Path dir;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public ConditionCache(Path dir) {
        this.dir = dir;
    }

    public Optional<ConditionResult> get(String recordId) {
        Path f = dir.resolve(recordId + ".json");
        if (!Files.exists(f)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(Files.readString(f), ConditionResult.class));
        } catch (IOException e) {
            throw new VisionException("Cannot read cached condition result for record " + recordId, e);
        }
    }

    public void put(String recordId, ConditionResult result) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(recordId + ".json"), mapper.writeValueAsString(result));
        } catch (IOException e) {
            throw new VisionException("Cannot write cached condition result for record " + recordId, e);
        }
    }
}
