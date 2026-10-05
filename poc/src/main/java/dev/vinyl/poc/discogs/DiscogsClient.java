package dev.vinyl.poc.discogs;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal Discogs client: search, release, marketplace stats and price suggestions.
 * Throttled to the rate limit, logs each call through the listener, and never exposes the token.
 */
public class DiscogsClient {

    private static final TypeReference<Map<String, PriceDto>> PRICES = new TypeReference<>() { };
    private static final String USER_AGENT = "vinyl-assistant-poc/0.1";

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String baseUrl;
    private final String token;
    private final Throttle throttle;
    private final Consumer<ApiCall> listener;

    public DiscogsClient(String baseUrl, String token, Throttle throttle, Consumer<ApiCall> listener) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.throttle = throttle;
        this.listener = listener;
    }

    public static DiscogsClient fromEnvironment(Consumer<ApiCall> listener) {
        String token = System.getenv("DISCOGS_TOKEN");
        if (token == null || token.isBlank()) {
            throw new DiscogsException("DISCOGS_TOKEN is not set");
        }
        return new DiscogsClient("https://api.discogs.com", token, new Throttle(), listener);
    }

    public SearchResponse searchByBarcode(String barcode) {
        return search("barcode", barcode);
    }

    public SearchResponse searchByCatnoAndLabel(String catno, String label) {
        return search("catno", catno, "label", label);
    }

    public SearchResponse searchByArtistAndTitle(String artist, String title) {
        return search("artist", artist, "release_title", title);
    }

    public ReleaseDto getRelease(String releaseId) {
        return mapper.readValue(get("/releases/" + encode(releaseId)), ReleaseDto.class);
    }

    public StatsDto getStats(String releaseId) {
        return mapper.readValue(get("/marketplace/stats/" + encode(releaseId) + "?curr_abbr=USD"), StatsDto.class);
    }

    /** Suggested price per Discogs grade name, for example "Very Good Plus (VG+)". */
    public Map<String, PriceDto> getPriceSuggestions(String releaseId) {
        return mapper.readValue(get("/marketplace/price_suggestions/" + encode(releaseId)), PRICES);
    }

    private SearchResponse search(String... keyValues) {
        StringBuilder q = new StringBuilder("/database/search?type=release&per_page=50");
        for (int i = 0; i < keyValues.length; i += 2) {
            q.append('&').append(keyValues[i]).append('=').append(encode(keyValues[i + 1]));
        }
        return mapper.readValue(get(q.toString()), SearchResponse.class);
    }

    private String get(String pathAndQuery) {
        for (int attempt = 1; ; attempt++) {
            throttle.beforeRequest();
            long start = System.currentTimeMillis();
            HttpResponse<String> response;
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                        .header("Authorization", "Discogs token=" + token)
                        .header("User-Agent", USER_AGENT)
                        .GET().build();
                response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new DiscogsException("Discogs request failed for " + pathAndQuery, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DiscogsException("Interrupted during " + pathAndQuery, e);
            }
            Integer remaining = response.headers().firstValue("x-discogs-ratelimit-remaining")
                    .map(DiscogsClient::parseIntOrNull).orElse(null);
            listener.accept(new ApiCall(pathAndQuery, response.statusCode(),
                    System.currentTimeMillis() - start, remaining));
            throttle.afterResponse(remaining);
            if (response.statusCode() == 429 && attempt == 1) {
                throttle.blockFor();
                continue;
            }
            if (response.statusCode() != 200) {
                throw new DiscogsException("Discogs returned HTTP " + response.statusCode() + " for " + pathAndQuery);
            }
            return response.body();
        }
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
