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
 * One call per record for the condition experiment: sends the vinyl-surface and sleeve photos and gets back the
 * defects it can see plus a suggested media grade and sleeve grade. The method takes photos and nothing else, so
 * the user's grade, notes and known defects cannot reach the model: the test is blind by construction.
 */
public class ConditionClient {

    static final String PROMPT = """
            These are photos of ONE used vinyl record: the vinyl surface under a lamp and the sleeve, in \
            arbitrary order. They are labelled photo_1, photo_2, ... in the order given. For each photo return \
            its view: vinyl_side_a, vinyl_side_b, sleeve_front, sleeve_back, sleeve_other, label, other, or \
            unclear (use unclear when you cannot tell; never guess).
            List every defect you can actually see. Vinyl surface: scuffs, scratches, marks. Sleeve: ring wear, \
            seam splits, writing, corner dings, foxing, stains. Each defect needs the photo it came from, where \
            it is, a short description, and your confidence in words (low, medium, high). Glare, reflections, \
            dust and fingerprints are often mistaken for defects: if a mark could be one of those, give low \
            confidence.
            Then suggest a media grade and a sleeve grade on the Discogs scale (M, NM, VG+, VG, G+, G, F, P) \
            from what is visible, with the defects the grade is based on and your confidence in words. These \
            photos cannot show warps, surface noise or groove wear, so the media grade is visual only: do not \
            say anything about how the record plays. If the photos do not let you judge the vinyl or the \
            sleeve, answer cannot_tell for it; never guess. Put anything you could not assess, for example a \
            side hidden by glare, in not_assessable.""";

    private static final List<String> VIEWS = List.of("vinyl_side_a", "vinyl_side_b", "sleeve_front",
            "sleeve_back", "sleeve_other", "label", "other", "unclear");
    private static final List<String> KINDS = List.of("scuff", "scratch", "mark", "other_vinyl", "ring_wear",
            "seam_split", "writing", "corner_ding", "foxing", "stain", "other_sleeve");
    private static final List<String> GRADES = List.of("M", "NM", "VG+", "VG", "G+", "G", "F", "P", "cannot_tell");
    private static final List<String> CONFIDENCE = List.of("low", "medium", "high");

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String baseUrl;
    private final String apiKey;
    private final VisionConfig config;
    private final Consumer<AiCall> listener;

    public ConditionClient(String baseUrl, String apiKey, VisionConfig config, Consumer<AiCall> listener) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.config = config;
        this.listener = listener;
    }

    public static ConditionClient fromEnvironment(VisionConfig config, Consumer<AiCall> listener) {
        String key = System.getenv("VINYL_ANTHROPIC_KEY");
        if (key == null || key.isBlank()) {
            throw new VisionException("VINYL_ANTHROPIC_KEY is not set");
        }
        return new ConditionClient("https://api.anthropic.com", key, config, listener);
    }

    /** Photos must already be resized JPEGs. Nothing about the user's own view of the record is passed in. */
    public ConditionResult assess(List<Path> jpegs) {
        long start = System.currentTimeMillis();
        HttpResponse<String> response = post(requestBody(jpegs));
        if (response.statusCode() != 200) {
            String detail = response.body() == null ? "" : response.body();
            throw new VisionException("Anthropic API returned HTTP " + response.statusCode() + ": "
                    + detail.substring(0, Math.min(detail.length(), 500)));
        }
        MessageResponse msg = mapper.readValue(response.body(), MessageResponse.class);
        if ("max_tokens".equals(msg.stopReason()) || "refusal".equals(msg.stopReason())) {
            throw new VisionException("Model stopped with " + msg.stopReason());
        }
        String text = msg.content().stream().filter(b -> "text".equals(b.type())).map(Block::text)
                .findFirst().orElseThrow(() -> new VisionException("Model returned no text"));
        Payload p = mapper.readValue(text, Payload.class);

        long in = msg.usage().inputTokens();
        long out = msg.usage().outputTokens();
        AiCall call = new AiCall(config.model(), jpegs.size(), in, out, config.price().cost(in, out),
                System.currentTimeMillis() - start);
        listener.accept(call);
        return new ConditionResult(
                p.photos().stream().map(x -> new ConditionResult.PhotoView(x.id(), x.view())).toList(),
                p.defects().stream().map(d -> new ConditionResult.Defect(d.surface(), d.kind(), d.photo(),
                        d.location(), d.description(), d.confidence())).toList(),
                grade(p.mediaGrade()), grade(p.sleeveGrade()), p.notAssessable(), call);
    }

    private static ConditionResult.GradeSuggestion grade(GradeItem g) {
        return new ConditionResult.GradeSuggestion(g.grade(), g.basis(), g.confidence());
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
        Map<String, Object> photo = object(Map.of("id", str(), "view", enumOf(VIEWS)), List.of("id", "view"));
        Map<String, Object> defect = object(Map.of("surface", enumOf(List.of("vinyl", "sleeve")),
                        "kind", enumOf(KINDS), "photo", str(), "location", str(), "description", str(),
                        "confidence", enumOf(CONFIDENCE)),
                List.of("surface", "kind", "photo", "location", "description", "confidence"));
        Map<String, Object> grade = object(Map.of("grade", enumOf(GRADES), "basis", str(),
                "confidence", enumOf(CONFIDENCE)), List.of("grade", "basis", "confidence"));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("photos", Map.of("type", "array", "items", photo));
        props.put("defects", Map.of("type", "array", "items", defect));
        props.put("media_grade", grade);
        props.put("sleeve_grade", grade);
        props.put("not_assessable", Map.of("type", "array", "items", str()));
        return object(props, List.of("photos", "defects", "media_grade", "sleeve_grade", "not_assessable"));
    }

    private static Map<String, Object> str() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
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

    record Payload(List<PhotoItem> photos, List<DefectItem> defects,
                   @JsonProperty("media_grade") GradeItem mediaGrade,
                   @JsonProperty("sleeve_grade") GradeItem sleeveGrade,
                   @JsonProperty("not_assessable") List<String> notAssessable) {
    }

    record PhotoItem(String id, String view) {
    }

    record DefectItem(String surface, String kind, String photo, String location, String description,
                      String confidence) {
    }

    record GradeItem(String grade, String basis, String confidence) {
    }
}
