package dev.vinyl.poc.discogs;

/** One logged API call. Cost is zero for Discogs; the AI cost is logged by the vision client. */
public record ApiCall(String pathAndQuery, int status, long elapsedMillis, Integer rateRemaining) {
}
