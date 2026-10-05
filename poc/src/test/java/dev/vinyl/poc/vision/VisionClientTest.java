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

class VisionClientTest {

    private static final String OK = """
            {"content":[{"type":"thinking","thinking":""},{"type":"text","text":"%s"}],
             "stop_reason":"end_turn","usage":{"input_tokens":26217,"output_tokens":2675}}""";

    private static final String PAYLOAD = """
            {\\"photos\\":[{\\"id\\":\\"photo_1\\",\\"role\\":\\"front\\"},{\\"id\\":\\"photo_2\\",\\"role\\":\\"runout_b\\"},\
            {\\"id\\":\\"photo_3\\",\\"role\\":\\"mystery\\"}],\
            \\"observations\\":[{\\"field\\":\\"catalog_number\\",\\"value\\":\\"8122796640\\",\
            \\"raw_text\\":\\"ATLANTIC 8122796640\\",\\"photo\\":\\"photo_1\\"},\
            {\\"field\\":\\"matrix_text\\",\\"value\\":\\"6016-01\\",\\"raw_text\\":\\"6016-01\\",\\"photo\\":\\"photo_2\\"}]}""";

    @TempDir
    Path tmp;

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final List<AiCall> calls = new ArrayList<>();
    private String responseBody = OK.formatted(PAYLOAD);
    private int status = 200;
    private VisionClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            requestBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKeyHeader.set(ex.getRequestHeaders().getFirst("x-api-key"));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        VisionConfig config = new VisionConfig("test-model", 8000, "medium", 1568,
                new ModelPrice(new BigDecimal("2.00"), new BigDecimal("10.00")));
        client = new VisionClient("http://127.0.0.1:" + server.getAddress().getPort(), "SECRET-KEY", config, calls::add);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private List<Path> photos(int n) throws IOException {
        List<Path> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Path p = tmp.resolve("whatever-name-" + i + ".jpg");
            Files.write(p, new byte[] {1, 2, 3, (byte) i});
            out.add(p);
        }
        return out;
    }

    @Test
    void parsesRolesAndObservationsAndKeepsRawText() throws IOException {
        VisionResult r = client.extract(photos(3));
        assertEquals(PhotoRole.FRONT, r.photos().get(0).role());
        assertEquals(PhotoRole.RUNOUT_B, r.photos().get(1).role());
        assertEquals("ATLANTIC 8122796640", r.observationsFor("catalog_number").get(0).rawText());
        assertEquals("photo_2", r.observationsFor("matrix_text").get(0).photo());
    }

    @Test
    void unknownRoleIsReportedAsUnclearNotGuessed() throws IOException {
        assertEquals(PhotoRole.UNCLEAR, client.extract(photos(3)).photos().get(2).role());
    }

    @Test
    void logsTokensAndCostForEveryCall() throws IOException {
        client.extract(photos(3));
        assertEquals(1, calls.size());
        assertEquals(26217, calls.get(0).inputTokens());
        assertEquals(2675, calls.get(0).outputTokens());
        // 26217 * 2 / 1e6 + 2675 * 10 / 1e6 = 0.052434 + 0.02675
        assertEquals(new BigDecimal("0.079184"), calls.get(0).costUsd());
        assertEquals(3, calls.get(0).photos());
    }

    @Test
    void requestCarriesModelEffortSchemaAndEveryPhotoWithoutFileNames() throws IOException {
        client.extract(photos(3));
        String body = requestBody.get();
        assertTrue(body.contains("\"model\":\"test-model\""));
        assertTrue(body.contains("\"effort\":\"medium\""));
        assertTrue(body.contains("json_schema"));
        assertEquals(3, body.split("\"type\":\"image\"", -1).length - 1);
        assertTrue(body.contains("photo_3"));
        assertFalse(body.contains("whatever-name"));
    }

    @Test
    void sendsTheKeyInTheHeaderOnly() throws IOException {
        client.extract(photos(1));
        assertEquals("SECRET-KEY", apiKeyHeader.get());
        assertFalse(requestBody.get().contains("SECRET-KEY"));
    }

    @Test
    void maxTokensStopIsAnErrorNotAnEmptyResult() {
        responseBody = """
                {"content":[{"type":"thinking","thinking":""}],"stop_reason":"max_tokens",
                 "usage":{"input_tokens":1,"output_tokens":2000}}""";
        VisionException e = assertThrows(VisionException.class, () -> client.extract(photos(1)));
        assertTrue(e.getMessage().contains("max_tokens"));
    }

    @Test
    void refusalIsAnError() {
        responseBody = "{\"content\":[],\"stop_reason\":\"refusal\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
        assertThrows(VisionException.class, () -> client.extract(photos(1)));
    }

    @Test
    void httpErrorMessageNeverContainsTheKey() {
        status = 401;
        responseBody = "{}";
        VisionException e = assertThrows(VisionException.class, () -> client.extract(photos(1)));
        assertTrue(e.getMessage().contains("401"));
        assertFalse(e.getMessage().contains("SECRET-KEY"));
    }
}
