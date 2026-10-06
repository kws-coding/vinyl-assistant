package dev.vinyl.poc.vision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps our own model output (never Discogs content) on disk so re-running scoring or pricing does not pay
 * for the vision call again.
 */
public class VisionCache {

    private final Path dir;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public VisionCache(Path dir) {
        this.dir = dir;
    }

    public Optional<VisionResult> get(String recordId) {
        Path f = dir.resolve(recordId + ".json");
        if (!Files.exists(f)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(Files.readString(f), VisionResult.class));
        } catch (IOException e) {
            throw new VisionException("Cannot read cached vision result for record " + recordId, e);
        }
    }

    public void put(String recordId, VisionResult result) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(recordId + ".json"), mapper.writeValueAsString(result));
        } catch (IOException e) {
            throw new VisionException("Cannot write cached vision result for record " + recordId, e);
        }
    }
}
