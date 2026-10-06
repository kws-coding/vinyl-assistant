package dev.vinyl.poc.vision;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import tools.jackson.databind.json.JsonMapper;

/**
 * One call per record: sends all of a record's photos and gets back, per photo, its role plus structured
 * observations with raw text. The model never grades condition and never picks a release.
 */
public class VisionClient {

    static final String PROMPT = """
            These are all the photos of ONE used vinyl record, in arbitrary order. They are labelled \
            photo_1, photo_2, ... in the order given. For each photo, return its role: front, back, labels, \
            barcode, runout_a, runout_b, other, or unclear (use unclear when you cannot tell; never guess).
            Then list observations ONLY for text that is actually visible: catalog_number, label, country, \
            barcode_digits, format, matrix_text, artist, title, year. Each observation needs the photo it came \
            from and the raw text exactly as printed or etched. For partly legible etchings give only the \
            characters you can read. If something is not visible, omit it. Do not grade condition and do not \
            comment on how the record plays.""";

    private static final List<String> ROLES =
            List.of("front", "back", "labels", "barcode", "runout_a", "runout_b", "other", "unclear");
    private static final List<String> FIELDS = List.of("catalog_number", "label", "country", "barcode_digits",
            "format", "matrix_text", "artist", "title", "year");

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String baseUrl;
    private final String apiKey;
    private final VisionConfig config;
    private final Consumer<AiCall> listener;

    public VisionClient(String baseUrl, String apiKey, VisionConfig config, Consumer<AiCall> listener) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.config = config;
        this.listener = listener;
    }

    public static VisionClient fromEnvironment(VisionConfig config, Consumer<AiCall> listener) {
        String key = System.getenv("VINYL_ANTHROPIC_KEY");
        if (key == null || key.isBlank()) {
            throw new VisionException("VINYL_ANTHROPIC_KEY is not set");
        }
        return new VisionClient("https://api.anthropic.com", key, config, listener);
    }

    /** Photos must already be resized JPEGs, for example from {@link ImageResizer}. */
    public VisionResult extract(List<Path> jpegs) {
        long start = System.currentTimeMillis();
        String body = requestBody(jpegs);
        HttpResponse<String> response = post(body);
        if (response.statusCode() != 200) {
            throw new VisionException("Anthropic API returned HTTP " + response.statusCode());
        }
        MessageResponse msg = mapper.readValue(response.body(), MessageResponse.class);
        if ("max_tokens".equals(msg.stopReason()) || "refusal".equals(msg.stopReason())) {
            throw new VisionException("Model stopped with " + msg.stopReason());
        }
        String text = msg.content().stream().filter(b -> "text".equals(b.type())).map(Block::text)
                .findFirst().orElseThrow(() -> new VisionException("Model returned no text"));
        Payload payload = mapper.readValue(text, Payload.class);

        long in = msg.usage().inputTokens();
        long out = msg.usage().outputTokens();
        AiCall call = new AiCall(config.model(), jpegs.size(), in, out, config.price().cost(in, out),
                System.currentTimeMillis() - start);
        listener.accept(call);
        return new VisionResult(
                payload.photos().stream().map(p -> new PhotoReading(p.id(), PhotoRole.parse(p.role()))).toList(),
                payload.observations().stream()
                        .map(o -> new Observation(o.field(), o.value(), o.rawText(), o.photo())).toList(),
                call);
    }

    String requestBody(List<Path> jpegs) {
        List<Object> content = new ArrayList<>();
        for (int i = 0; i < jpegs.size(); i++) {
            content.add(Map.of("type", "text", "text", "photo_" + (i + 1)));
            content.add(Map.of("type", "image", "source", Map.of("type", "base64", "media_type", "image/jpeg",
                    "data", base64(jpegs.get(i)))));
        }
        content.add(Map.of("type", "text", "text", PROMPT));

        Map<String, Object> outputConfig = new LinkedHashMap<>();
        outputConfig.put("effort", config.effort());
        outputConfig.put("format", Map.of("type", "json_schema", "schema", schema()));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", config.model());
        request.put("max_tokens", config.maxTokens());
        request.put("output_config", outputConfig);
        request.put("messages", List.of(Map.of("role", "user", "content", content)));
        return mapper.writeValueAsString(request);
    }

    private static Map<String, Object> schema() {
        Map<String, Object> photo = objectSchema(
                Map.of("id", Map.of("type", "string"), "role", Map.of("type", "string", "enum", ROLES)),
                List.of("id", "role"));
        Map<String, Object> observation = objectSchema(
                Map.of("field", Map.of("type", "string", "enum", FIELDS), "value", Map.of("type", "string"),
                        "raw_text", Map.of("type", "string"), "photo", Map.of("type", "string")),
                List.of("field", "value", "raw_text", "photo"));
        return objectSchema(
                Map.of("photos", Map.of("type", "array", "items", photo),
                        "observations", Map.of("type", "array", "items", observation)),
                List.of("photos", "observations"));
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", properties);
        s.put("required", required);
        s.put("additionalProperties", false);
        return s;
    }

    private HttpResponse<String> post(String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new VisionException("Anthropic request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisionException("Interrupted during the Anthropic request", e);
        }
    }

    private static String base64(Path p) {
        try {
            return java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(p));
        } catch (IOException e) {
            throw new VisionException("Cannot read photo " + p.getFileName(), e);
        }
    }

    record MessageResponse(List<Block> content, @JsonProperty("stop_reason") String stopReason, Usage usage) {
    }

    record Block(String type, String text) {
    }

    record Usage(@JsonProperty("input_tokens") long inputTokens, @JsonProperty("output_tokens") long outputTokens) {
    }

    record Payload(List<PhotoItem> photos, List<ObservationItem> observations) {
    }

    record PhotoItem(String id, String role) {
    }

    record ObservationItem(String field, String value, @JsonProperty("raw_text") String rawText, String photo) {
    }
}
