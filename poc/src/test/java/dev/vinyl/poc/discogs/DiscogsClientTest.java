package dev.vinyl.poc.discogs;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import dev.vinyl.poc.domain.ReleaseInfo;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscogsClientTest {

    // Shaped like a real /releases/{id} response, trimmed. Values are test data.
    private static final String RELEASE = """
            {"id":19502752,"title":"Led Zeppelin II","country":"Worldwide","year":2020,
             "artists":[{"name":"Led Zeppelin"}],
             "labels":[{"name":"Atlantic","catno":"8122796640"},{"name":"Atlantic","catno":"R1 535225"}],
             "formats":[{"name":"Vinyl","qty":"1","descriptions":["LP","Album","Reissue"],"text":"180 Gram"}],
             "identifiers":[{"type":"Barcode","value":"081227966409"},
                            {"type":"Matrix / Runout","value":"BK06016-01 B1 0081227966409 B2"},
                            {"type":"Rights Society","value":"ASCAP"}],
             "num_for_sale":36,"lowest_price":15.0,"some_new_field":true}""";

    private HttpServer server;
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final List<ApiCall> calls = new ArrayList<>();
    private final AtomicInteger hits = new AtomicInteger();
    private DiscogsClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/releases/19502752", ex -> reply(ex, 200, RELEASE));
        server.createContext("/releases/429", ex -> {
            if (hits.incrementAndGet() == 1) {
                reply(ex, 429, "{}");
            } else {
                reply(ex, 200, RELEASE);
            }
        });
        server.createContext("/releases/missing", ex -> reply(ex, 404, "{\"message\":\"not found\"}"));
        server.createContext("/marketplace/price_suggestions/19502752", ex -> reply(ex, 200,
                "{\"Very Good Plus (VG+)\":{\"currency\":\"USD\",\"value\":20.62},"
                        + "\"Near Mint (NM or M-)\":{\"currency\":\"USD\",\"value\":26.97}}"));
        server.createContext("/database/search", ex -> reply(ex, 200,
                "{\"results\":[{\"id\":1,\"title\":\"A - B\",\"country\":\"US\",\"year\":\"2014\","
                        + "\"format\":[\"Vinyl\"],\"label\":[\"Atlantic\"],\"catno\":\"X\",\"barcode\":[\"1\"]}],"
                        + "\"echo\":\"" + ex.getRequestURI().getRawQuery() + "\"}"));
        server.start();
        client = new DiscogsClient("http://127.0.0.1:" + server.getAddress().getPort(), "SECRET-TOKEN",
                new Throttle(System::currentTimeMillis, ms -> { }), calls::add);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("x-discogs-ratelimit-remaining", "42");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Test
    void sendsTheTokenInTheAuthorizationHeader() {
        client.getRelease("19502752");
        assertEquals(List.of("Discogs token=SECRET-TOKEN"), authHeaders);
    }

    @Test
    void mapsAReleaseAndIgnoresUnknownFields() {
        ReleaseInfo info = ReleaseMapper.toInfo(client.getRelease("19502752"));
        assertEquals("19502752", info.releaseId());
        assertEquals(2020, info.year());
        assertEquals(List.of("8122796640", "R1 535225"), info.catalogNumbers());
        assertEquals(List.of("081227966409"), info.barcodes());
        assertEquals(List.of("BK06016-01 B1 0081227966409 B2"), info.matrices());
        assertEquals(List.of("Atlantic"), info.labels());
    }

    @Test
    void parsesPriceSuggestionsByGradeName() {
        Map<String, PriceDto> prices = client.getPriceSuggestions("19502752");
        assertEquals(20.62, prices.get("Very Good Plus (VG+)").value(), 1e-9);
        assertEquals("USD", prices.get("Near Mint (NM or M-)").currency());
    }

    @Test
    void searchEncodesParametersAndParsesResults() {
        SearchResponse r = client.searchByCatnoAndLabel("R1 535225", "Atlantic & Co");
        assertEquals(1, r.results().size());
        assertEquals("2014", r.results().get(0).year());
        assertTrue(calls.get(0).pathAndQuery().contains("catno=R1+535225"));
        assertTrue(calls.get(0).pathAndQuery().contains("label=Atlantic+%26+Co"));
    }

    @Test
    void retriesOnceOn429() {
        assertEquals(19502752L, client.getRelease("429").id());
        assertEquals(2, calls.size());
        assertEquals(429, calls.get(0).status());
    }

    @Test
    void logsEveryCallWithRateRemainingAndNeverTheToken() {
        client.getRelease("19502752");
        assertEquals(1, calls.size());
        assertEquals(42, calls.get(0).rateRemaining());
        assertFalse(calls.get(0).toString().contains("SECRET-TOKEN"));
    }

    @Test
    void errorMessageDoesNotContainTheToken() {
        DiscogsException e = assertThrows(DiscogsException.class, () -> client.getRelease("missing"));
        assertTrue(e.getMessage().contains("404"));
        assertFalse(e.getMessage().contains("SECRET-TOKEN"));
    }
}
