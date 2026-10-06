package dev.vinyl.poc.vision;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The model's answer here is canned test data, not a real assessment. */
class ConditionClientTest {

    private static final String OK = """
            {"content":[{"type":"thinking","thinking":""},{"type":"text","text":"%s"}],
             "stop_reason":"end_turn","usage":{"input_tokens":12000,"output_tokens":900}}""";

    private static final String PAYLOAD = """
            {\\"photos\\":[{\\"id\\":\\"photo_1\\",\\"view\\":\\"vinyl_side_a\\"},{\\"id\\":\\"photo_2\\",\\"view\\":\\"sleeve_front\\"}],\
            \\"defects\\":[{\\"surface\\":\\"vinyl\\",\\"kind\\":\\"scuff\\",\\"photo\\":\\"photo_1\\",\\"location\\":\\"near the label\\",\
            \\"description\\":\\"light scuff\\",\\"confidence\\":\\"low\\"},\
            {\\"surface\\":\\"sleeve\\",\\"kind\\":\\"ring_wear\\",\\"photo\\":\\"photo_2\\",\\"location\\":\\"centre\\",\
            \\"description\\":\\"faint ring\\",\\"confidence\\":\\"medium\\"}],\
            \\"media_grade\\":{\\"grade\\":\\"VG+\\",\\"basis\\":\\"one low-confidence scuff\\",\\"confidence\\":\\"low\\"},\
            \\"sleeve_grade\\":{\\"grade\\":\\"cannot_tell\\",\\"basis\\":\\"back not shown\\",\\"confidence\\":\\"low\\"},\
            \\"not_assessable\\":[\\"side B not photographed\\"]}""";

    @TempDir
    Path tmp;

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final List<AiCall> calls = new ArrayList<>();
    private ConditionClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            requestBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = OK.formatted(PAYLOAD).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        VisionConfig config = new VisionConfig("test-model", 8000, "medium", 1568,
                new ModelPrice(new BigDecimal("2.00"), new BigDecimal("10.00")));
        client = new ConditionClient("http://127.0.0.1:" + server.getAddress().getPort(), "SECRET-KEY", config,
                calls::add);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private List<Path> photos(int n) throws IOException {
        List<Path> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Path p = tmp.resolve("lamp-" + i + ".jpg");
            Files.write(p, new byte[] {1, 2, 3, (byte) i});
            out.add(p);
        }
        return out;
    }

    @Test
    void parsesDefectsGradesAndWhatCouldNotBeAssessed() throws IOException {
        ConditionResult r = client.assess(photos(2));
        assertEquals("vinyl_side_a", r.photos().get(0).view());
        assertEquals(1, r.defectsOn("vinyl").size());
        assertEquals("photo_1", r.defectsOn("vinyl").get(0).photo());
        assertEquals("low", r.defectsOn("vinyl").get(0).confidence());
        assertEquals(1, r.defectsOn("sleeve").size());
        assertEquals("VG+", r.media().grade());
        assertEquals("cannot_tell", r.sleeve().grade());
        assertEquals(List.of("side B not photographed"), r.notAssessable());
    }

    @Test
    void logsTokensAndCost() throws IOException {
        client.assess(photos(2));
        assertEquals(1, calls.size());
        // 12000 * 2 / 1e6 + 900 * 10 / 1e6 = 0.024 + 0.009
        assertEquals(new BigDecimal("0.033000"), calls.get(0).costUsd());
        assertEquals(2, calls.get(0).photos());
    }

    @Test
    void theRequestCarriesTheSchemaAndPhotosButNoFileNamesKeyOrUserInput() throws IOException {
        client.assess(photos(2));
        String body = requestBody.get();
        assertTrue(body.contains("json_schema"));
        assertTrue(body.contains("cannot_tell"));
        assertEquals(2, body.split("\"type\":\"image\"", -1).length - 1);
        assertFalse(body.contains("lamp-"));
        assertFalse(body.contains("SECRET-KEY"));
    }

    @Test
    void thePromptSaysVisualOnlyAndForbidsGuessing() {
        assertTrue(ConditionClient.PROMPT.contains("visual only"));
        assertTrue(ConditionClient.PROMPT.contains("never guess"));
        assertTrue(ConditionClient.PROMPT.contains("do not say anything about how the record plays"));
    }

    @Test
    void theCacheRoundTripsAResult() throws IOException {
        ConditionResult r = client.assess(photos(2));
        ConditionCache cache = new ConditionCache(tmp.resolve("cache"));
        assertTrue(cache.get("007").isEmpty());
        cache.put("007", r);
        ConditionResult back = cache.get("007").orElseThrow();
        assertEquals(r.media(), back.media());
        assertEquals(r.defects(), back.defects());
        assertEquals(r.call().costUsd(), back.call().costUsd());
    }
}
