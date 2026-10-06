package dev.vinyl.poc.runner;

import dev.vinyl.poc.discogs.ApiCall;
import dev.vinyl.poc.vision.AiCall;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Every API call of a run, with its AI cost. Never contains credentials. Throwaway. */
public class CallLog {

    static final List<String> HEADER = List.of("kind", "record_id", "detail", "status_or_photos",
            "input_tokens", "output_tokens", "cost_usd", "elapsed_ms", "rate_remaining");

    private final List<List<String>> rows = new ArrayList<>();
    private String recordId = "";

    public void setRecord(String id) {
        this.recordId = id;
    }

    public void discogs(ApiCall c) {
        rows.add(List.of("discogs", recordId, c.pathAndQuery(), String.valueOf(c.status()), "", "", "0",
                String.valueOf(c.elapsedMillis()), c.rateRemaining() == null ? "" : String.valueOf(c.rateRemaining())));
    }

    public void ai(AiCall c) {
        rows.add(List.of("ai", recordId, c.model(), String.valueOf(c.photos()), String.valueOf(c.inputTokens()),
                String.valueOf(c.outputTokens()), c.costUsd().toPlainString(), String.valueOf(c.elapsedMillis()), ""));
    }

    public int size() {
        return rows.size();
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(Csv.join(HEADER));
        rows.forEach(r -> lines.add(Csv.join(r)));
        Files.write(file, lines);
    }
}
